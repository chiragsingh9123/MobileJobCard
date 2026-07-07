"""Customers: lookup (auto-fetch), list, create, history"""
from flask import Blueprint, request, jsonify
from extensions import db
from models import Customer, LedgerEntry
from utils import login_required

customer_bp = Blueprint("customers", __name__, url_prefix="/api/customers")


@customer_bp.get("/lookup/")
@login_required
def lookup(user):
    """AUTO-FETCH: mobile number se purana customer dhundo"""
    mobile = request.args.get("mobile", "").strip()
    c = Customer.query.filter_by(shop_id=user.shop_id, mobile=mobile).first()
    if not c:
        return jsonify({"found": False})
    return jsonify({"found": True, "customer": c.to_dict(full=True)})


@customer_bp.get("/")
@login_required
def list_customers(user):
    q = Customer.query.filter_by(shop_id=user.shop_id)
    search = request.args.get("search", "").strip()
    if search:
        q = q.filter(db.or_(Customer.name.ilike(f"%{search}%"),
                            Customer.mobile.ilike(f"%{search}%")))
    customers = q.order_by(Customer.created_at.desc()).all()
    if request.args.get("pending") == "true":
        customers = [c for c in customers if c.khata_balance > 0]
    return jsonify({"count": len(customers),
                    "results": [c.to_dict() for c in customers]})


@customer_bp.post("/")
@login_required
def create_customer(user):
    d = request.get_json(force=True)
    if not d.get("name") or not d.get("mobile"):
        return jsonify({"detail": "Name and mobile number are required"}), 400
    if Customer.query.filter_by(shop_id=user.shop_id, mobile=d["mobile"]).first():
        return jsonify({"detail": "A customer with this mobile number already exists"}), 400
    c = Customer(shop_id=user.shop_id, name=d["name"], mobile=d["mobile"],
                 email=d.get("email", ""), address=d.get("address", ""),
                 city=d.get("city", ""), notes=d.get("notes", ""))
    db.session.add(c)
    db.session.commit()
    return jsonify(c.to_dict(full=True)), 201


@customer_bp.get("/<int:cid>/history/")
@login_required
def history(user, cid):
    c = Customer.query.filter_by(id=cid, shop_id=user.shop_id).first_or_404()
    entries = (LedgerEntry.query.filter_by(customer_id=c.id)
               .order_by(LedgerEntry.created_at.desc()).all())
    return jsonify({"customer": c.to_dict(full=True),
                    "jobs": [j.to_dict() for j in
                             sorted(c.jobs, key=lambda j: j.created_at, reverse=True)],
                    "khata": [e.to_dict() for e in entries]})
