"""Inventory: products CRUD, summary, add stock"""
from flask import Blueprint, request, jsonify
from extensions import db
from models import Product
from utils import login_required

inventory_bp = Blueprint("inventory", __name__, url_prefix="/api/inventory")


@inventory_bp.get("/products/")
@login_required
def list_products(user):
    q = Product.query.filter_by(shop_id=user.shop_id)
    cat = request.args.get("category", "").upper()
    if cat:
        q = q.filter_by(category=cat)
    search = request.args.get("search", "")
    if search:
        q = q.filter(Product.name.ilike(f"%{search}%"))
    products = q.order_by(Product.name).all()
    stock = request.args.get("stock", "").upper()
    if stock:
        products = [p for p in products if p.stock_status == stock]
    return jsonify({"count": len(products), "results": [p.to_dict() for p in products]})


@inventory_bp.post("/products/")
@login_required
def create_product(user):
    d = request.get_json(force=True)
    p = Product(shop_id=user.shop_id, name=d.get("name", ""),
                category=d.get("category", "ACCESSORY"), brand=d.get("brand", ""),
                model_compatibility=d.get("model_compatibility", ""),
                purchase_price=float(d.get("purchase_price") or 0),
                sale_price=float(d.get("sale_price") or 0),
                quantity=int(d.get("quantity") or 0),
                low_stock_threshold=int(d.get("low_stock_threshold") or 3))
    db.session.add(p)
    db.session.commit()
    return jsonify(p.to_dict()), 201


@inventory_bp.get("/products/summary/")
@login_required
def summary(user):
    products = Product.query.filter_by(shop_id=user.shop_id).all()
    return jsonify({
        "total_products": len(products),
        "in_stock": sum(1 for p in products if p.stock_status == "IN"),
        "low_stock": sum(1 for p in products if p.stock_status == "LOW"),
        "out_of_stock": sum(1 for p in products if p.stock_status == "OUT"),
        "stock_value": round(sum(p.purchase_price * p.quantity for p in products), 2)})


@inventory_bp.post("/products/<int:pid>/add_stock/")
@login_required
def add_stock(user, pid):
    p = Product.query.filter_by(id=pid, shop_id=user.shop_id).first_or_404()
    p.quantity += int(request.get_json(force=True).get("quantity", 0))
    db.session.commit()
    return jsonify(p.to_dict())
