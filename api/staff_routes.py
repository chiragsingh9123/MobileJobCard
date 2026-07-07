"""Staff Management: shop owner apne staff members add/manage kar sakta hai.
Har staff ka apna login hota hai, jobs assign ho sakte hain, poora tracking hota hai."""
from flask import Blueprint, request, jsonify
from extensions import db
from models import User, JobCard
from utils import login_required, owner_required

staff_bp = Blueprint("staff", __name__, url_prefix="/api/staff")


@staff_bp.get("/")
@login_required
def list_staff(user):
    """Sabhi staff + owner is shop ke - sabko dikhao (owner ko manage karne ka access hoga)"""
    users = User.query.filter_by(shop_id=user.shop_id).order_by(User.created_at).all()
    return jsonify({"count": len(users), "results": [u.to_dict_staff() for u in users]})


@staff_bp.post("/")
@owner_required
def create_staff(user):
    """Sirf OWNER naya staff member bana sakta hai"""
    d = request.get_json(force=True)
    mobile = (d.get("mobile") or "").strip()
    if not mobile or not d.get("password") or not d.get("first_name"):
        return jsonify({"detail": "Name, mobile number, and password are all required"}), 400
    if User.query.filter_by(mobile=mobile).first():
        return jsonify({"detail": "This mobile number is already registered"}), 400

    staff = User(mobile=mobile, first_name=d.get("first_name", ""),
                last_name=d.get("last_name", ""), role="STAFF",
                designation=d.get("designation", "Technician"),
                shop_id=user.shop_id, created_by_id=user.id)
    staff.set_password(d["password"])
    db.session.add(staff)
    db.session.commit()
    return jsonify(staff.to_dict_staff()), 201


@staff_bp.post("/<int:sid>/toggle/")
@owner_required
def toggle_staff(user, sid):
    """Staff ko active/inactive karo (job assign karna band ho jaayega, login bhi block)"""
    staff = User.query.filter_by(id=sid, shop_id=user.shop_id, role="STAFF").first_or_404()
    staff.is_active = not staff.is_active
    db.session.commit()
    return jsonify(staff.to_dict_staff())


@staff_bp.post("/<int:sid>/reset-password/")
@owner_required
def reset_staff_password(user, sid):
    """Owner staff ka password bhool jaane par reset kar sakta hai (SMS/email service nahi hai,
    isliye yeh real-world practical solution hai - owner khud naya password set kar deta hai)"""
    staff = User.query.filter_by(id=sid, shop_id=user.shop_id, role="STAFF").first_or_404()
    d = request.get_json(force=True)
    new_password = d.get("password", "")
    if len(new_password) < 4:
        return jsonify({"detail": "Password must be at least 4 characters"}), 400
    staff.set_password(new_password)
    db.session.commit()
    return jsonify({"message": f"Password reset successfully for {staff.first_name}"})


@staff_bp.get("/<int:sid>/jobs/")
@login_required
def staff_jobs(user, sid):
    """Ek staff member ko assign hui saari jobs"""
    staff = User.query.filter_by(id=sid, shop_id=user.shop_id).first_or_404()
    jobs = (JobCard.query.filter_by(shop_id=user.shop_id, assigned_to_id=sid)
           .order_by(JobCard.created_at.desc()).all())
    return jsonify({"staff": staff.to_dict_staff(),
                    "count": len(jobs), "results": [j.to_dict() for j in jobs]})
