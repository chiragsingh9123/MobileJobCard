"""Shop settings: profile, print/receipt settings, WhatsApp templates,
backup export, change password, shop-wide activity log."""
from flask import Blueprint, request, jsonify
from extensions import db
from models import Customer, JobCard, Payment, Product, JobActivityLog
from utils import login_required, owner_required

shop_bp = Blueprint("shop", __name__, url_prefix="/api/shop")


@shop_bp.get("/profile/")
@login_required
def get_profile(user):
    if not user.shop:
        return jsonify({"detail": "Shop not found"}), 404
    return jsonify(user.shop.to_dict_full())


@shop_bp.post("/profile/")
@owner_required
def update_profile(user):
    d = request.get_json(force=True)
    shop = user.shop
    for field in ["name", "address", "city", "gst_number"]:
        if field in d:
            setattr(shop, field, d[field])
    db.session.commit()
    return jsonify(shop.to_dict_full())


@shop_bp.post("/print-settings/")
@owner_required
def update_print_settings(user):
    d = request.get_json(force=True)
    shop = user.shop
    if "receipt_header" in d: shop.receipt_header = d["receipt_header"]
    if "receipt_footer" in d: shop.receipt_footer = d["receipt_footer"]
    if "show_gst_on_receipt" in d:
        # Defensive: string "false"/"true" ya real boolean dono handle karo
        # (bool("false") Python me True hota hai - yeh gotcha yahan guard kiya gaya hai)
        v = d["show_gst_on_receipt"]
        shop.show_gst_on_receipt = v if isinstance(v, bool) else str(v).strip().lower() in ("true", "1", "yes")
    db.session.commit()
    return jsonify(shop.to_dict_full())


@shop_bp.post("/whatsapp-templates/")
@owner_required
def update_templates(user):
    d = request.get_json(force=True)
    shop = user.shop
    if "template_received" in d: shop.template_received = d["template_received"]
    if "template_ready" in d: shop.template_ready = d["template_ready"]
    if "template_delivered" in d: shop.template_delivered = d["template_delivered"]
    if "template_rwr" in d: shop.template_rwr = d["template_rwr"]
    db.session.commit()
    return jsonify(shop.to_dict_full())


@shop_bp.post("/change-password/")
@login_required
def change_password(user):
    d = request.get_json(force=True)
    old = d.get("old_password", "")
    new = d.get("new_password", "")
    if not user.check_password(old):
        return jsonify({"detail": "Current password is incorrect"}), 400
    if len(new) < 4:
        return jsonify({"detail": "New password must be at least 4 characters"}), 400
    user.set_password(new)
    db.session.commit()
    return jsonify({"message": "Password changed successfully"})


@shop_bp.get("/activity-log/")
@login_required
def shop_activity_log(user):
    """Poori shop ki recent activity (sab jobs milakar) - audit trail"""
    logs = (JobActivityLog.query.filter_by(shop_id=user.shop_id)
           .order_by(JobActivityLog.created_at.desc()).limit(100).all())
    results = []
    for log in logs:
        d = log.to_dict()
        d["job_id"] = log.job.job_id if log.job else None
        results.append(d)
    return jsonify({"count": len(results), "results": results})


@shop_bp.get("/export/")
@owner_required
def export_backup(user):
    """Backup & Restore: shop ka poora data JSON me export karo"""
    customers = Customer.query.filter_by(shop_id=user.shop_id).all()
    jobs = JobCard.query.filter_by(shop_id=user.shop_id).all()
    payments = Payment.query.filter_by(shop_id=user.shop_id).all()
    products = Product.query.filter_by(shop_id=user.shop_id).all()
    return jsonify({
        "shop": user.shop.to_dict_full(),
        "exported_at": __import__("datetime").datetime.now().isoformat(),
        "customers": [c.to_dict(full=True) for c in customers],
        "jobs": [j.to_dict(full=True) for j in jobs],
        "payments": [p.to_dict() for p in payments],
        "products": [p.to_dict() for p in products],
    })
