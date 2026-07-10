"""ADMIN PANEL - Flask + custom HTML (Jinja2) templates
Session-based login, sirf role=ADMIN users ke liye."""
from datetime import datetime, timedelta
from functools import wraps
from flask import (Blueprint, render_template, request, redirect,
                   url_for, session, flash, current_app)
from extensions import db
from models import (User, Shop, SubscriptionPlan, Subscription, Voucher,
                    JobCard, Customer, Payment, LedgerEntry, Product,
                    PlatformSettings, PaymentRequest, Notification, now)
from utils import save_uploaded_image
from push_notifications import send_push_multicast

admin_bp = Blueprint("admin", __name__, url_prefix="/admin",
                     template_folder="../templates/admin")


def admin_required(f):
    @wraps(f)
    def wrapper(*args, **kwargs):
        if not session.get("admin_id"):
            return redirect(url_for("admin.login"))
        return f(*args, **kwargs)
    return wrapper


# ---------- LOGIN / LOGOUT ----------
@admin_bp.route("/login/", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        user = User.query.filter_by(mobile=request.form.get("mobile", ""),
                                    role="ADMIN").first()
        if user and user.check_password(request.form.get("password", "")):
            session["admin_id"] = user.id
            session["admin_name"] = user.first_name
            return redirect(url_for("admin.dashboard"))
        flash("Mobile ya password galat hai, ya aap admin nahi hain", "error")
    return render_template("login.html")


@admin_bp.get("/logout/")
def logout():
    session.clear()
    return redirect(url_for("admin.login"))


# ---------- DASHBOARD ----------
@admin_bp.get("/")
@admin_required
def dashboard():
    stats = {
        "shops": Shop.query.count(),
        "users": User.query.count(),
        "active_subs": Subscription.query.filter(Subscription.end_date > now()).count(),
        "vouchers_unused": Voucher.query.filter_by(is_redeemed=False).count(),
        "vouchers_used": Voucher.query.filter_by(is_redeemed=True).count(),
        "jobs": JobCard.query.count(),
        "customers": Customer.query.count(),
        "revenue": round(sum(p.amount for p in Payment.query.all()), 2),
        "pending_payments": PaymentRequest.query.filter_by(status="PENDING").count(),
    }
    recent_shops = Shop.query.order_by(Shop.created_at.desc()).limit(5).all()
    return render_template("dashboard.html", stats=stats, recent_shops=recent_shops)


# ---------- SHOPS ----------
@admin_bp.get("/shops/")
@admin_required
def shops():
    return render_template("shops.html",
                           shops=Shop.query.order_by(Shop.created_at.desc()).all(),
                           now=now())


@admin_bp.post("/shops/<int:sid>/toggle/")
@admin_required
def toggle_shop(sid):
    s = Shop.query.get_or_404(sid)
    s.is_active = not s.is_active
    db.session.commit()
    flash(f"Shop '{s.name}' {'unblock' if s.is_active else 'block'} ho gayi", "success")
    return redirect(url_for("admin.shops"))


# ---------- USERS ----------
@admin_bp.get("/users/")
@admin_required
def users():
    return render_template("users.html",
                           users=User.query.order_by(User.created_at.desc()).all())


@admin_bp.post("/users/<int:uid>/toggle/")
@admin_required
def toggle_user(uid):
    u = User.query.get_or_404(uid)
    u.is_active = not u.is_active
    db.session.commit()
    flash(f"User {u.mobile} {'activate' if u.is_active else 'deactivate'} hua", "success")
    return redirect(url_for("admin.users"))


# ---------- PLANS ----------
@admin_bp.route("/plans/", methods=["GET", "POST"])
@admin_required
def plans():
    if request.method == "POST":
        p = SubscriptionPlan(name=request.form["name"],
                             price=float(request.form.get("price") or 0),
                             duration_days=int(request.form.get("duration_days") or 30),
                             description=request.form.get("description", ""))
        db.session.add(p)
        db.session.commit()
        flash(f"Plan '{p.name}' ban gaya", "success")
        return redirect(url_for("admin.plans"))
    return render_template("plans.html", plans=SubscriptionPlan.query.all())


@admin_bp.post("/plans/<int:pid>/toggle/")
@admin_required
def toggle_plan(pid):
    p = SubscriptionPlan.query.get_or_404(pid)
    p.is_active = not p.is_active
    db.session.commit()
    return redirect(url_for("admin.plans"))


# ---------- VOUCHERS ----------
@admin_bp.route("/vouchers/", methods=["GET", "POST"])
@admin_required
def vouchers():
    if request.method == "POST":
        plan_id = int(request.form["plan_id"])
        count = max(1, min(int(request.form.get("count") or 1), 100))
        extra = int(request.form.get("extra_days") or 0)
        expiry_days = request.form.get("expiry_days")
        expires_at = (now() + timedelta(days=int(expiry_days))) if expiry_days else None
        codes = []
        for _ in range(count):
            v = Voucher(code=Voucher.generate_code(), plan_id=plan_id,
                        extra_days=extra, expires_at=expires_at)
            db.session.add(v)
            codes.append(v.code)
        db.session.commit()
        flash(f"{count} voucher generate hue: {', '.join(codes[:5])}"
              + (" ..." if count > 5 else ""), "success")
        return redirect(url_for("admin.vouchers"))

    q = Voucher.query.order_by(Voucher.created_at.desc())
    f = request.args.get("filter")
    if f == "unused":
        q = q.filter_by(is_redeemed=False)
    elif f == "used":
        q = q.filter_by(is_redeemed=True)
    return render_template("vouchers.html", vouchers=q.all(),
                           plans=SubscriptionPlan.query.filter_by(is_active=True, is_purchasable=True).all(),
                           current_filter=f)


@admin_bp.post("/vouchers/<int:vid>/delete/")
@admin_required
def delete_voucher(vid):
    v = Voucher.query.get_or_404(vid)
    if v.is_redeemed:
        flash("Redeemed voucher delete nahi ho sakta", "error")
    else:
        db.session.delete(v)
        db.session.commit()
        flash(f"Voucher {v.code} delete hua", "success")
    return redirect(url_for("admin.vouchers"))


# ---------- SUBSCRIPTIONS ----------
@admin_bp.get("/subscriptions/")
@admin_required
def subscriptions():
    subs = Subscription.query.order_by(Subscription.end_date.desc()).all()
    return render_template("subscriptions.html", subs=subs, now=now())


# ---------- UPI PAYMENT REQUESTS (manual verification) ----------
@admin_bp.get("/payment-requests/")
@admin_required
def payment_requests():
    q = PaymentRequest.query.order_by(PaymentRequest.created_at.desc())
    f = request.args.get("filter", "PENDING")
    if f in ("PENDING", "APPROVED", "REJECTED"):
        q = q.filter_by(status=f)
    return render_template("payment_requests.html", requests=q.all(), current_filter=f)


@admin_bp.post("/payment-requests/<int:rid>/approve/")
@admin_required
def approve_payment_request(rid):
    pr = PaymentRequest.query.get_or_404(rid)
    if pr.status != "PENDING":
        flash("Yeh request pehle hi process ho chuki hai", "error")
        return redirect(url_for("admin.payment_requests"))
    sub = Subscription.activate(pr.shop, pr.plan)
    pr.status = "APPROVED"
    pr.reviewed_by_id = session.get("admin_id")
    pr.reviewed_at = now()
    db.session.commit()
    flash(f"{pr.shop.name} ka '{pr.plan.name}' plan activate ho gaya "
          f"({sub.days_remaining} din)", "success")
    return redirect(url_for("admin.payment_requests"))


@admin_bp.post("/payment-requests/<int:rid>/reject/")
@admin_required
def reject_payment_request(rid):
    pr = PaymentRequest.query.get_or_404(rid)
    if pr.status != "PENDING":
        flash("Yeh request pehle hi process ho chuki hai", "error")
        return redirect(url_for("admin.payment_requests"))
    pr.status = "REJECTED"
    pr.admin_note = request.form.get("reason", "")
    pr.reviewed_by_id = session.get("admin_id")
    pr.reviewed_at = now()
    db.session.commit()
    flash(f"{pr.shop.name} ki payment request reject ho gayi", "success")
    return redirect(url_for("admin.payment_requests"))


# ---------- UPI SETTINGS (QR code + UPI ID) ----------
@admin_bp.route("/upi-settings/", methods=["GET", "POST"])
@admin_required
def upi_settings():
    settings = PlatformSettings.get()
    if request.method == "POST":
        settings.upi_id = request.form.get("upi_id", "")
        settings.upi_name = request.form.get("upi_name", "")
        qr_file = request.files.get("qr_image")
        if qr_file and qr_file.filename:
            filename = save_uploaded_image(qr_file, current_app.config["QR_FOLDER"])
            if filename:
                settings.qr_image_path = filename
            else:
                flash("QR image sirf jpg/png/webp format me upload karein", "error")
        settings.updated_at = now()
        db.session.commit()
        flash("UPI settings update ho gayi", "success")
        return redirect(url_for("admin.upi_settings"))
    return render_template("upi_settings.html", settings=settings)


# ---------- APP UPDATE SETTINGS (force-update control) ----------
@admin_bp.route("/app-settings/", methods=["GET", "POST"])
@admin_required
def app_settings():
    settings = PlatformSettings.get()
    if request.method == "POST":
        settings.force_update_enabled = "force_update_enabled" in request.form
        settings.min_version_code = int(request.form.get("min_version_code") or 1)
        settings.update_message = request.form.get("update_message", "")
        settings.play_store_url = request.form.get("play_store_url", "")
        db.session.commit()
        flash("App update settings saved", "success")
        return redirect(url_for("admin.app_settings"))
    return render_template("app_settings.html", settings=settings)


# ---------- NOTIFICATIONS (broadcast to all users) ----------
@admin_bp.route("/notifications/", methods=["GET", "POST"])
@admin_required
def notifications():
    if request.method == "POST":
        title = request.form.get("title", "").strip()
        message = request.form.get("message", "").strip()
        if not title or not message:
            flash("Title and message are both required", "error")
            return redirect(url_for("admin.notifications"))
        db.session.add(Notification(title=title, message=message))
        db.session.commit()
 
        tokens = [u.fcm_token for u in User.query.filter(
            User.fcm_token.isnot(None), User.is_active.is_(True)).all()]
        delivered = send_push_multicast(tokens, title, message, data={"type": "ADMIN_BROADCAST"})
 
        flash(f"Notification sent to all users ({delivered} device(s) reached by push)", "success")
        return redirect(url_for("admin.notifications"))
    sent = Notification.query.order_by(Notification.created_at.desc()).all()
    return render_template("notifications.html", sent=sent)


@admin_bp.post("/notifications/<int:nid>/retract/")
@admin_required
def retract_notification(nid):
    n = Notification.query.get_or_404(nid)
    n.is_active = False
    db.session.commit()
    flash("Notification retracted", "success")
    return redirect(url_for("admin.notifications"))


# ---------- JOBS / CUSTOMERS / PAYMENTS / INVENTORY (view) ----------
@admin_bp.get("/jobs/")
@admin_required
def jobs():
    return render_template("jobs.html",
                           jobs=JobCard.query.order_by(JobCard.created_at.desc())
                           .limit(200).all())


@admin_bp.get("/customers/")
@admin_required
def customers():
    return render_template("customers.html",
                           customers=Customer.query
                           .order_by(Customer.created_at.desc()).limit(200).all())


@admin_bp.get("/payments/")
@admin_required
def payments():
    pays = Payment.query.order_by(Payment.created_at.desc()).limit(200).all()
    ledger = LedgerEntry.query.order_by(LedgerEntry.created_at.desc()).limit(200).all()
    return render_template("payments.html", payments=pays, ledger=ledger)


@admin_bp.get("/inventory/")
@admin_required
def inventory():
    return render_template("inventory.html",
                           products=Product.query.order_by(Product.name).all())
