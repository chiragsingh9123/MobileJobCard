"""Dashboard + Analytics"""
from datetime import datetime, timedelta
from flask import Blueprint, request, jsonify
from models import JobCard, Customer, Payment, Product, Expense
from utils import login_required

report_bp = Blueprint("reports", __name__, url_prefix="/api/reports")


@report_bp.get("/dashboard/")
@login_required
def dashboard(user):
    sid = user.shop_id
    today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    jobs_today = JobCard.query.filter(JobCard.shop_id == sid,
                                      JobCard.created_at >= today).count()
    payments_today = Payment.query.filter(Payment.shop_id == sid,
                                          Payment.created_at >= today).all()
    customers = Customer.query.filter_by(shop_id=sid).all()
    products = Product.query.filter_by(shop_id=sid).all()
    return jsonify({
        "todays_jobs": jobs_today,
        "repairing": JobCard.query.filter_by(shop_id=sid, status="REPAIRING").count(),
        "completed": JobCard.query.filter_by(shop_id=sid, status="READY").count(),
        "delivered_today": JobCard.query.filter(JobCard.shop_id == sid,
                                                JobCard.delivered_at >= today).count(),
        "todays_income": round(sum(p.amount for p in payments_today), 2),
        "pending_amount": round(sum(c.khata_balance for c in customers
                                    if c.khata_balance > 0), 2),
        "total_customers": len(customers),
        "low_stock_items": sum(1 for p in products if p.stock_status in ("LOW", "OUT"))})


@report_bp.get("/analytics/")
@login_required
def analytics(user):
    sid = user.shop_id
    days = int(request.args.get("days", 30))
    since = datetime.now() - timedelta(days=days)

    payments = Payment.query.filter(Payment.shop_id == sid,
                                    Payment.created_at >= since).all()
    expenses = Expense.query.filter(Expense.shop_id == sid,
                                    Expense.created_at >= since).all()
    jobs = JobCard.query.filter(JobCard.shop_id == sid,
                                JobCard.created_at >= since).all()

    daily = {}
    for p in payments:
        k = p.created_at.strftime("%Y-%m-%d")
        daily[k] = round(daily.get(k, 0) + p.amount, 2)

    by_status = {}
    for j in jobs:
        by_status[j.status] = by_status.get(j.status, 0) + 1

    collection = round(sum(p.amount for p in payments), 2)
    expense_total = round(sum(e.amount for e in expenses), 2)
    return jsonify({
        "days": days,
        "total_sales": round(sum(j.estimated_cost for j in jobs), 2),
        "total_collection": collection,
        "total_expense": expense_total,
        "profit": round(collection - expense_total, 2),
        "total_jobs": len(jobs),
        "daily_revenue": [{"date": k, "amount": v} for k, v in sorted(daily.items())],
        "jobs_by_status": by_status})
