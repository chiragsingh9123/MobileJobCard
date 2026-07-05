"""Payments + Khata (udhaar ledger) with FIFO per-job settlement tracking"""
from flask import Blueprint, request, jsonify
from extensions import db
from models import Payment, LedgerEntry, Customer, JobCard, Expense, JobActivityLog
from utils import login_required

payment_bp = Blueprint("payments", __name__, url_prefix="/api/payments")


def _job_outstanding(customer_id, job_card_id):
    """Ek specific job ka kitna khata abhi baaki hai"""
    debit = sum(e.amount for e in LedgerEntry.query.filter_by(
        customer_id=customer_id, job_card_id=job_card_id, entry_type="DEBIT").all())
    credit = sum(e.amount for e in LedgerEntry.query.filter_by(
        customer_id=customer_id, job_card_id=job_card_id, entry_type="CREDIT").all())
    return round(debit - credit, 2)


def _settle_khata_fifo(user, customer, amount, specific_job_id=None):
    """Khata settlement: agar specific job di hai to usi par apply karo, warna
    sabse purani pending job se shuru karke FIFO order me paisa adjust karo.
    Har job ke against alag CREDIT entry banti hai - isse pata chalta hai kis
    job ka paisa clear hua (transparent, per-job tracking)."""
    remaining = amount
    settled_jobs = []

    if specific_job_id:
        job = JobCard.query.filter_by(id=specific_job_id, shop_id=user.shop_id).first()
        if job:
            outstanding = _job_outstanding(customer.id, specific_job_id)
            apply_amt = min(remaining, outstanding) if outstanding > 0 else remaining
            if apply_amt > 0:
                db.session.add(LedgerEntry(shop_id=user.shop_id, customer_id=customer.id,
                                           job_card_id=specific_job_id, entry_type="CREDIT",
                                           amount=apply_amt, note="Khata payment (job-specific)"))
                JobActivityLog.log(user.shop_id, job.id, user.id, "PAYMENT",
                                   f"Rs.{apply_amt} khata settle hua")
                settled_jobs.append({"job_id": job.job_id, "amount": apply_amt})
                remaining -= apply_amt
    else:
        debited_job_ids = [row[0] for row in db.session.query(LedgerEntry.job_card_id)
                           .filter_by(customer_id=customer.id, entry_type="DEBIT")
                           .filter(LedgerEntry.job_card_id.isnot(None))
                           .distinct().all()]
        jobs = (JobCard.query.filter(JobCard.id.in_(debited_job_ids))
               .order_by(JobCard.created_at.asc()).all()) if debited_job_ids else []
        for job in jobs:
            if remaining <= 0:
                break
            outstanding = _job_outstanding(customer.id, job.id)
            if outstanding <= 0:
                continue
            apply_amt = min(remaining, outstanding)
            db.session.add(LedgerEntry(shop_id=user.shop_id, customer_id=customer.id,
                                       job_card_id=job.id, entry_type="CREDIT",
                                       amount=apply_amt, note="Khata payment (FIFO auto-settle)"))
            JobActivityLog.log(user.shop_id, job.id, user.id, "PAYMENT",
                               f"Rs.{apply_amt} khata settle hua")
            settled_jobs.append({"job_id": job.job_id, "amount": apply_amt})
            remaining -= apply_amt

    if remaining > 0:
        db.session.add(LedgerEntry(shop_id=user.shop_id, customer_id=customer.id,
                                   job_card_id=None, entry_type="CREDIT",
                                   amount=remaining, note="Khata payment (general/advance credit)"))
        settled_jobs.append({"job_id": None, "amount": remaining})

    return settled_jobs


@payment_bp.post("/payments/")
@login_required
def create_payment(user):
    d = request.get_json(force=True)
    amount = float(d.get("amount") or 0)
    if amount <= 0:
        return jsonify({"detail": "Amount sahi daalein"}), 400

    p = Payment(shop_id=user.shop_id, amount=amount,
                customer_id=d.get("customer_id"),
                job_card_id=d.get("job_card_id"),
                method=(d.get("method") or "CASH").upper(),
                payment_type=(d.get("payment_type") or "FINAL").upper(),
                note=d.get("note", ""))
    db.session.add(p)

    settled_jobs = []
    # KHATA_SETTLE -> FIFO CREDIT entries (per-job tracking, bug-free)
    if p.payment_type == "KHATA_SETTLE" and p.customer_id:
        customer = Customer.query.get(p.customer_id)
        if customer:
            settled_jobs = _settle_khata_fifo(user, customer, amount, d.get("job_card_id"))
    db.session.commit()

    resp = p.to_dict()
    if settled_jobs:
        resp["settled_jobs"] = settled_jobs
    if p.customer_id:
        c = Customer.query.get(p.customer_id)
        if c:
            resp["customer_khata_balance"] = c.khata_balance
    return jsonify(resp), 201


@payment_bp.get("/khata/pending/")
@login_required
def khata_pending(user):
    """Sabhi customers jinka khata baaki hai"""
    customers = Customer.query.filter_by(shop_id=user.shop_id).all()
    pending = [c.to_dict() for c in customers if c.khata_balance > 0]
    pending.sort(key=lambda x: x["khata_balance"], reverse=True)
    return jsonify({"count": len(pending),
                    "total_pending": round(sum(c["khata_balance"] for c in pending), 2),
                    "results": pending})


@payment_bp.get("/khata/jobs/<int:customer_id>/")
@login_required
def khata_jobs(user, customer_id):
    """Customer ki kaunsi jobs me kitna khata baaki hai (per-job breakdown)"""
    customer = Customer.query.filter_by(id=customer_id, shop_id=user.shop_id).first_or_404()
    debited_job_ids = [row[0] for row in db.session.query(LedgerEntry.job_card_id)
                       .filter_by(customer_id=customer.id, entry_type="DEBIT")
                       .filter(LedgerEntry.job_card_id.isnot(None))
                       .distinct().all()]
    jobs = (JobCard.query.filter(JobCard.id.in_(debited_job_ids))
           .order_by(JobCard.created_at.asc()).all()) if debited_job_ids else []
    results = []
    for job in jobs:
        outstanding = _job_outstanding(customer.id, job.id)
        if outstanding > 0:
            results.append({"job_card_id": job.id, "job_id": job.job_id,
                            "device": f"{job.device_brand} {job.device_model}",
                            "outstanding": outstanding})
    return jsonify({"customer": customer.to_dict(), "results": results})


@payment_bp.post("/expenses/")
@login_required
def create_expense(user):
    d = request.get_json(force=True)
    e = Expense(shop_id=user.shop_id, title=d.get("title", ""),
                amount=float(d.get("amount") or 0),
                category=(d.get("category") or "OTHER").upper())
    db.session.add(e)
    db.session.commit()
    return jsonify(e.to_dict()), 201


@payment_bp.get("/expenses/")
@login_required
def list_expenses(user):
    es = (Expense.query.filter_by(shop_id=user.shop_id)
          .order_by(Expense.created_at.desc()).all())
    return jsonify({"count": len(es), "results": [e.to_dict() for e in es]})
