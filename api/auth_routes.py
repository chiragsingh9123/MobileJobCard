"""Auth: OTP (Telegram), register (auto trial), login, me, plans, redeem voucher"""
from datetime import timedelta
from flask import Blueprint, request, jsonify, current_app
from extensions import db
from models import User, Shop, SubscriptionPlan, Subscription, Voucher, OTPCode, now
from utils import make_tokens, login_required, send_telegram_otp

auth_bp = Blueprint("auth", __name__, url_prefix="/api/auth")


# ================= OTP (Telegram ke through bhejta hai) =================

@auth_bp.post("/send-otp/")
def send_otp():
    d = request.get_json(force=True)
    mobile = (d.get("mobile") or "").strip()
    purpose = (d.get("purpose") or "").upper()
    if len(mobile) != 10 or not mobile.isdigit():
        return jsonify({"detail": "Enter a valid 10-digit mobile number"}), 400
    if purpose not in ("REGISTER", "LOGIN", "RESET_PASSWORD"):
        return jsonify({"detail": "purpose must be REGISTER, LOGIN, or RESET_PASSWORD"}), 400

    existing_user = User.query.filter_by(mobile=mobile).first()
    if purpose == "REGISTER" and existing_user:
        return jsonify({"detail": "This mobile number is already registered - please log in"}), 400
    if purpose in ("LOGIN", "RESET_PASSWORD") and not existing_user:
        return jsonify({"detail": "This mobile number is not registered - please register first"}), 400

    # Resend cooldown - prevents OTP spam
    cooldown = current_app.config["OTP_RESEND_COOLDOWN_SECONDS"]
    recent = (OTPCode.query.filter_by(mobile=mobile, purpose=purpose)
             .order_by(OTPCode.created_at.desc()).first())
    if recent and (now() - recent.created_at).total_seconds() < cooldown:
        wait = int(cooldown - (now() - recent.created_at).total_seconds())
        return jsonify({"detail": f"Please wait {wait} seconds before requesting another OTP"}), 429

    otp = OTPCode.generate(mobile, purpose, current_app.config["OTP_EXPIRY_MINUTES"])
    sent = send_telegram_otp(mobile, otp.code, purpose)
    return jsonify({
        "message": "OTP has been sent" if sent else
                   "OTP generated (could not be sent via Telegram - please check the admin console)",
        "expires_in_minutes": current_app.config["OTP_EXPIRY_MINUTES"]
    })


def _verify_otp(mobile, purpose, submitted_otp):
    """Shared helper used before register/login/password-reset can proceed"""
    otp_row = OTPCode.latest_pending(mobile, purpose)
    if not otp_row:
        return "OTP not found - please tap 'Send OTP' first"
    ok, err = otp_row.verify(submitted_otp, current_app.config["OTP_MAX_ATTEMPTS"])
    return None if ok else err


# ================= REGISTER / LOGIN =================

@auth_bp.post("/register/")
def register():
    d = request.get_json(force=True)
    for f in ["mobile", "password", "first_name", "shop_name", "otp"]:
        if not d.get(f):
            return jsonify({"detail": f"{f} is required"}), 400
    if User.query.filter_by(mobile=d["mobile"]).first():
        return jsonify({"detail": "This mobile number is already registered"}), 400

    # OTP verify karo pehle
    err = _verify_otp(d["mobile"], "REGISTER", d["otp"])
    if err:
        return jsonify({"detail": err}), 400

    shop = Shop(name=d["shop_name"], address=d.get("shop_address", ""),
                city=d.get("city", ""))
    db.session.add(shop)
    db.session.flush()

    user = User(mobile=d["mobile"], first_name=d["first_name"],
                last_name=d.get("last_name", ""), email=d.get("email", ""),
                role="OWNER", shop_id=shop.id)
    user.set_password(d["password"])
    db.session.add(user)

    # Auto 7-day FREE TRIAL
    trial_plan = SubscriptionPlan.query.filter_by(name="Free Trial").first()
    if not trial_plan:
        trial_plan = SubscriptionPlan(name="Free Trial", price=0,
                                      duration_days=current_app.config["TRIAL_DAYS"],
                                      description="Naye users ke liye free trial",
                                      is_purchasable=False)
        db.session.add(trial_plan)
        db.session.flush()
    db.session.add(Subscription(shop_id=shop.id, plan_id=trial_plan.id, is_trial=True, source="TRIAL",
                                end_date=now() + timedelta(days=current_app.config["TRIAL_DAYS"])))
    db.session.commit()
    return jsonify({"tokens": make_tokens(user), "user": user.to_dict(),
                    "message": "Registration successful! Your 7-day free trial is now active."}), 201


@auth_bp.post("/login/")
def login():
    d = request.get_json(force=True)
    user = User.query.filter_by(mobile=d.get("mobile", "")).first()
    if not user or not user.check_password(d.get("password", "")):
        return jsonify({"detail": "Mobile number or password is incorrect"}), 401
    if not user.is_active:
        return jsonify({"detail": "This account has been deactivated"}), 403

    # OTP verify karo (password sahi hone ke baad)
    otp = d.get("otp")
    if not otp:
        return jsonify({"detail": "OTP is required - please tap 'Send OTP' first"}), 400
    err = _verify_otp(user.mobile, "LOGIN", otp)
    if err:
        return jsonify({"detail": err}), 400

    return jsonify({"tokens": make_tokens(user), "user": user.to_dict()})


@auth_bp.post("/reset-password/")
def reset_password():
    """Forgot Password flow: mobile + OTP (purpose=RESET_PASSWORD) + new password.
    Uses the same OTP infrastructure as register/login, so no separate delivery
    channel is needed."""
    d = request.get_json(force=True)
    mobile = (d.get("mobile") or "").strip()
    otp = d.get("otp")
    new_password = d.get("new_password") or ""

    user = User.query.filter_by(mobile=mobile).first()
    if not user:
        return jsonify({"detail": "This mobile number is not registered"}), 404
    if not otp:
        return jsonify({"detail": "OTP is required - please tap 'Send OTP' first"}), 400
    if len(new_password) < 4:
        return jsonify({"detail": "New password must be at least 4 characters"}), 400

    err = _verify_otp(mobile, "RESET_PASSWORD", otp)
    if err:
        return jsonify({"detail": err}), 400

    user.set_password(new_password)
    db.session.commit()
    return jsonify({"message": "Password reset successfully - please log in with your new password"})


@auth_bp.get("/me/")
@login_required
def me(user):
    return jsonify(user.to_dict())


@auth_bp.post("/fcm-token/")
@login_required
def register_fcm_token(user):
    """Called by the app right after login (and whenever Firebase issues a
    fresh token) so push notifications can reach this specific device."""
    d = request.get_json(force=True)
    token = (d.get("fcm_token") or "").strip()
    if not token:
        return jsonify({"detail": "fcm_token is required"}), 400
    user.fcm_token = token
    db.session.commit()
    return jsonify({"message": "Push token registered"})
 


@auth_bp.get("/plans/")
def plans():
    return jsonify({"results": [p.to_dict() for p in
                    SubscriptionPlan.query.filter_by(is_active=True, is_purchasable=True).all()]})


@auth_bp.post("/redeem-voucher/")
@login_required
def redeem_voucher(user):
    code = (request.get_json(force=True).get("code") or "").strip().upper()
    v = Voucher.query.filter_by(code=code).first()
    if not v:
        return jsonify({"detail": "The voucher code is incorrect"}), 404
    sub, err = v.redeem(user.shop)
    if err:
        return jsonify({"detail": err}), 400
    return jsonify({"message": f"{sub.plan.name} plan activated! "
                               f"Valid for {sub.days_remaining + 1} days.",
                    "subscription": sub.to_dict()})
