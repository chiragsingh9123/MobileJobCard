"""
Subscription payments via Razorpay (automated - replaces the old manual
UPI-screenshot + admin-approval flow).

Flow:
  1. App calls POST /create-order/ with a plan_id -> we create a Razorpay
     order and a RazorpayPayment row (status=CREATED), return the order_id
     + amount + Razorpay key_id for the Android app to open Checkout with.
  2. User completes payment in the Razorpay Checkout UI.
  3. App calls POST /verify-payment/ with the order_id/payment_id/signature
     Razorpay gave it -> we verify the signature server-side (never trust
     the client's word alone), activate the subscription, and push a
     notification to the platform admin(s).
  4. POST /webhook/ is a second, independent confirmation path directly from
     Razorpay's servers - it catches the rare case where the Android app
     loses connectivity after payment but before step 3 completes, so the
     subscription still gets activated even if the app never calls back.
"""
from flask import Blueprint, request, jsonify, current_app
from extensions import db
from models import SubscriptionPlan, RazorpayPayment, Subscription, User, now
from utils import login_required, owner_required
from push_notifications import send_push_multicast
import razorpay_client

subscription_bp = Blueprint("subscription", __name__, url_prefix="/api/subscription")


def _notify_admins_of_payment(payment):
    """Pushes every platform admin a heads-up that a shop just paid."""
    admin_tokens = [a.fcm_token for a in User.query.filter_by(role="ADMIN").all() if a.fcm_token]
    if admin_tokens:
        send_push_multicast(
            admin_tokens,
            "Subscription payment received",
            f"{payment.shop.name} paid Rs. {payment.amount} for the {payment.plan.name} plan",
            data={"type": "ADMIN_BROADCAST"},
        )


@subscription_bp.get("/status/")
@login_required
def subscription_status(user):
    """Current subscription state for this shop - the app uses this to show
    the 'X days remaining' banner and to decide whether to show a paywall."""
    current = Subscription.get_current(user.shop_id)
    return jsonify({
        "has_subscription": current is not None,
        "is_active": current.is_active if current else False,
        "subscription": current.to_dict() if current else None,
    })


@subscription_bp.post("/create-order/")
@owner_required
def create_order(user):
    d = request.get_json(force=True)
    plan = SubscriptionPlan.query.filter_by(
        id=d.get("plan_id"), is_active=True, is_purchasable=True).first()
    if not plan:
        return jsonify({"detail": "Plan not found"}), 400

    receipt = f"shop{user.shop_id}-plan{plan.id}-{int(now().timestamp())}"
    order, error = razorpay_client.create_order(
        plan.price, receipt,
        notes={"shop_id": str(user.shop_id), "plan_id": str(plan.id), "shop_name": user.shop.name},
    )
    if error:
        return jsonify({"detail": f"Could not start payment: {error}"}), 502

    payment = RazorpayPayment(shop_id=user.shop_id, plan_id=plan.id, amount=plan.price,
                              razorpay_order_id=order["id"], status="CREATED")
    db.session.add(payment)
    db.session.commit()

    return jsonify({
        "razorpay_key_id": current_app.config.get("RAZORPAY_KEY_ID", ""),
        "order_id": order["id"],
        "amount_paise": order["amount"],
        "currency": order["currency"],
        "plan": plan.to_dict(),
        "shop_name": user.shop.name,
        "prefill_contact": user.mobile,
    }), 201


@subscription_bp.post("/verify-payment/")
@owner_required
def verify_payment(user):
    d = request.get_json(force=True)
    order_id = d.get("razorpay_order_id", "")
    payment_id = d.get("razorpay_payment_id", "")
    signature = d.get("razorpay_signature", "")
    if not (order_id and payment_id and signature):
        return jsonify({"detail": "Missing payment verification fields"}), 400

    payment = RazorpayPayment.query.filter_by(
        razorpay_order_id=order_id, shop_id=user.shop_id).first()
    if not payment:
        return jsonify({"detail": "No matching order found for this shop"}), 404

    if payment.status == "PAID":
        # Already processed (e.g. the webhook beat the app to it) - this is
        # not an error, just tell the app what already happened.
        return jsonify({"message": "Payment already verified",
                        "subscription": payment.subscription.to_dict() if payment.subscription else None})

    if not razorpay_client.verify_payment_signature(order_id, payment_id, signature):
        payment.status = "FAILED"
        payment.failure_reason = "Signature verification failed"
        db.session.commit()
        return jsonify({"detail": "Payment could not be verified. If money was deducted, "
                                  "it will be auto-refunded within a few days, or contact support."}), 400

    sub = Subscription.activate(user.shop, payment.plan, source="RAZORPAY")
    payment.razorpay_payment_id = payment_id
    payment.razorpay_signature = signature
    payment.status = "PAID"
    payment.verified_at = now()
    db.session.flush()
    payment.subscription_id = sub.id
    db.session.commit()

    _notify_admins_of_payment(payment)

    return jsonify({"message": f"Payment verified! Your {payment.plan.name} plan is now active.",
                    "subscription": sub.to_dict()})


@subscription_bp.post("/webhook/")
def razorpay_webhook():
    """Server-to-server confirmation directly from Razorpay. Configure this
    URL (https://yourdomain.com/api/subscription/webhook/) in Razorpay
    Dashboard -> Settings -> Webhooks, subscribed to the payment.captured
    event, and put the webhook secret it gives you into
    RAZORPAY_WEBHOOK_SECRET. No login required here (Razorpay can't send a
    JWT) - the HMAC signature check is what proves this request is genuine."""
    signature = request.headers.get("X-Razorpay-Signature", "")
    secret = current_app.config.get("RAZORPAY_WEBHOOK_SECRET", "")
    if not razorpay_client.verify_webhook_signature(request.data, signature, secret):
        return jsonify({"detail": "Invalid signature"}), 400

    payload = request.get_json(force=True)
    event = payload.get("event", "")
    if event != "payment.captured":
        return jsonify({"message": "Ignored (not a payment.captured event)"}), 200

    entity = payload.get("payload", {}).get("payment", {}).get("entity", {})
    order_id = entity.get("order_id", "")
    payment_id = entity.get("id", "")

    payment = RazorpayPayment.query.filter_by(razorpay_order_id=order_id).first()
    if not payment:
        return jsonify({"message": "No matching order on our side"}), 200
    if payment.status == "PAID":
        return jsonify({"message": "Already processed"}), 200

    sub = Subscription.activate(payment.shop, payment.plan, source="RAZORPAY")
    payment.razorpay_payment_id = payment_id
    payment.status = "PAID"
    payment.verified_at = now()
    db.session.flush()
    payment.subscription_id = sub.id
    db.session.commit()

    _notify_admins_of_payment(payment)
    return jsonify({"message": "Processed"}), 200


@subscription_bp.get("/my-payments/")
@login_required
def my_payments(user):
    """Payment history for this shop (receipts / renewal tracking)."""
    payments = (RazorpayPayment.query.filter_by(shop_id=user.shop_id)
               .order_by(RazorpayPayment.created_at.desc()).all())
    return jsonify({"count": len(payments), "results": [p.to_dict() for p in payments]})