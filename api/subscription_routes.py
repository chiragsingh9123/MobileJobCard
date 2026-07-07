"""UPI Subscription Payment (manual verification flow)
User plan chunta hai -> QR dekh kar UPI se pay karta hai -> screenshot + UTR submit karta hai
-> Admin manually verify karke approve/reject karta hai."""
from flask import Blueprint, request, jsonify, current_app
from extensions import db
from models import PlatformSettings, PaymentRequest, SubscriptionPlan
from utils import login_required, owner_required, save_uploaded_image

subscription_bp = Blueprint("subscription", __name__, url_prefix="/api/subscription")


@subscription_bp.get("/upi-details/")
@login_required
def upi_details(user):
    """User ko dikhane ke liye: kis UPI ID / QR par pay karna hai"""
    settings = PlatformSettings.get()
    return jsonify(settings.to_dict())


@subscription_bp.post("/submit-payment/")
@owner_required
def submit_payment(user):
    """User payment karne ke baad screenshot + UTR submit karta hai (multipart form)"""
    plan_id = request.form.get("plan_id")
    utr_number = (request.form.get("utr_number") or "").strip()
    plan = SubscriptionPlan.query.filter_by(id=plan_id, is_active=True, is_purchasable=True).first()
    if not plan:
        return jsonify({"detail": "Plan not found"}), 400
    if not utr_number:
        return jsonify({"detail": "Enter the UTR / transaction number"}), 400

    screenshot = request.files.get("screenshot")
    filename = save_uploaded_image(screenshot, current_app.config["SCREENSHOT_FOLDER"])
    if not filename:
        return jsonify({"detail": "Upload the payment screenshot (jpg/png)"}), 400

    pr = PaymentRequest(shop_id=user.shop_id, plan_id=plan.id, amount=plan.price,
                       utr_number=utr_number, screenshot_path=filename, status="PENDING")
    db.session.add(pr)
    db.session.commit()
    return jsonify({"message": "Payment request submitted! Once the admin verifies it, "
                              "your plan will be activated.",
                    "request": pr.to_dict()}), 201


@subscription_bp.get("/my-payment-requests/")
@login_required
def my_payment_requests(user):
    """User apne submit kiye hue payment requests dekh sakta hai (status track karne ke liye)"""
    reqs = (PaymentRequest.query.filter_by(shop_id=user.shop_id)
           .order_by(PaymentRequest.created_at.desc()).all())
    return jsonify({"count": len(reqs), "results": [r.to_dict() for r in reqs]})
