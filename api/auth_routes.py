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
    if purpose not in ("REGISTER", "LOGIN"):
        return jsonify({"detail": "purpose must be REGISTER or LOGIN"}), 400

    existing_user = User.query.filter_by(mobile=mobile).first()
    if purpose == "REGISTER" and existing_user:
        return jsonify({"detail": "This mobile number is already registered - please log in"}), 400
    if purpose == "LOGIN" and not existing_user:
        return jsonify({"detail": "This mobile number is not registered - please register first"}), 400

    # Resend cooldown - spam se bachne ke liye
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
                   "OTP generate ho gaya (Telegram abhi nahi bheja ja saka, console/admin se check karein)",
        "expires_in_minutes": current_app.config["OTP_EXPIRY_MINUTES"]
    })


def _verify_otp(mobile, purpose, submitted_otp):
    """Register/Login se pehle OTP check karne ka common helper"""
    otp_row = OTPCode.latest_pending(mobile, purpose)
    if not otp_row:
        return "OTP nahi mila - pehle 'Send OTP' dabayein"
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
    db.session.add(Subscription(shop_id=shop.id, plan_id=trial_plan.id, is_trial=True,
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


@auth_bp.get("/me/")
@login_required
def me(user):
    return jsonify(user.to_dict())


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
