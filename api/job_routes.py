"""Job Cards: create, edit, list (search+date filter), update_status (khata + RWR),
assign to staff, media (photo/video), message templates, full activity log."""
from datetime import datetime
from flask import Blueprint, request, jsonify, current_app
from extensions import db
from models import (JobCard, Customer, StatusHistory, JobActivityLog, LedgerEntry,
                    Product, UsedPart, User, JobMedia, JOB_STATUSES, now)
from utils import login_required, save_uploaded_media

job_bp = Blueprint("jobs", __name__, url_prefix="/api/jobs")

EDITABLE_FIELDS = ["custom_name", "device_brand", "device_model", "imei1", "imei2",
                   "color", "storage", "lock_type", "device_password", "accessories",
                   "alternate_mobile", "problem", "diagnosis", "estimated_cost",
                   "expected_delivery"]


@job_bp.post("/")
@login_required
def create_job(user):
    d = request.get_json(force=True)

    # Customer: existing id ya inline data (get_or_create - duplicate nahi banega)
    customer = None
    if d.get("customer_id"):
        customer = Customer.query.filter_by(id=d["customer_id"],
                                            shop_id=user.shop_id).first()
    elif d.get("customer_data"):
        cd = d["customer_data"]
        customer = Customer.query.filter_by(shop_id=user.shop_id,
                                            mobile=cd.get("mobile", "")).first()
        if not customer:
            customer = Customer(shop_id=user.shop_id, name=cd.get("name", ""),
                                mobile=cd.get("mobile", ""), email=cd.get("email", ""),
                                address=cd.get("address", ""), city=cd.get("city", ""))
            db.session.add(customer)
            db.session.flush()
    if not customer:
        return jsonify({"detail": "Customer info required hai"}), 400

    # 2nd job -> REPEAT customer
    if len(customer.jobs) >= 1 and customer.customer_type == "NORMAL":
        customer.customer_type = "REPEAT"

    assigned_to_id = d.get("assigned_to_id")
    if assigned_to_id:
        staff = User.query.filter_by(id=assigned_to_id, shop_id=user.shop_id).first()
        if not staff:
            assigned_to_id = None

    job = JobCard(shop_id=user.shop_id, customer_id=customer.id,
                  job_id=JobCard.next_job_id(user.shop_id),
                  custom_name=d.get("custom_name", ""),
                  device_brand=d.get("device_brand", ""),
                  device_model=d.get("device_model", ""),
                  imei1=d.get("imei1", ""), imei2=d.get("imei2", ""),
                  color=d.get("color", ""), storage=d.get("storage", ""),
                  lock_type=(d.get("lock_type") or "NONE").upper(),
                  device_password=d.get("device_password", ""),
                  accessories=d.get("accessories", ""),
                  alternate_mobile=d.get("alternate_mobile", ""),
                  problem=d.get("problem", ""),
                  estimated_cost=float(d.get("estimated_cost") or 0),
                  expected_delivery=d.get("expected_delivery", ""),
                  received_by_id=user.id, assigned_to_id=assigned_to_id)
    db.session.add(job)
    db.session.flush()
    db.session.add(StatusHistory(job_id=job.id, status="RECEIVED",
                                 by_user=user.first_name, note="Job card banaya"))
    # Full audit trail
    JobActivityLog.log(user.shop_id, job.id, user.id, "CREATED",
                       f"{user.first_name} ne job card banaya")
    if assigned_to_id:
        staff = User.query.get(assigned_to_id)
        JobActivityLog.log(user.shop_id, job.id, user.id, "ASSIGNED",
                           f"{staff.first_name} ko assign kiya gaya")
    db.session.commit()
    return jsonify(job.to_dict(full=True)), 201


@job_bp.get("/")
@login_required
def list_jobs(user):
    q = JobCard.query.filter_by(shop_id=user.shop_id)
    status = request.args.get("status", "").strip().upper()
    if status:
        q = q.filter_by(status=status)
    assigned_to = request.args.get("assigned_to", "").strip()
    if assigned_to:
        q = q.filter_by(assigned_to_id=int(assigned_to))

    # Date filter - single din (date=YYYY-MM-DD) ya range (date_from/date_to)
    single_date = request.args.get("date", "").strip()
    date_from = request.args.get("date_from", "").strip()
    date_to = request.args.get("date_to", "").strip()
    try:
        if single_date:
            d0 = datetime.strptime(single_date, "%Y-%m-%d")
            q = q.filter(JobCard.created_at >= d0,
                        JobCard.created_at < d0.replace(hour=23, minute=59, second=59))
        else:
            if date_from:
                q = q.filter(JobCard.created_at >= datetime.strptime(date_from, "%Y-%m-%d"))
            if date_to:
                d1 = datetime.strptime(date_to, "%Y-%m-%d").replace(hour=23, minute=59, second=59)
                q = q.filter(JobCard.created_at <= d1)
    except ValueError:
        return jsonify({"detail": "Date format YYYY-MM-DD hona chahiye"}), 400

    # Search: job_id, custom_name, customer name/mobile - sab cover karta hai
    search = request.args.get("search", "").strip()
    if search:
        q = (q.join(Customer)
             .filter(db.or_(JobCard.job_id.ilike(f"%{search}%"),
                            JobCard.custom_name.ilike(f"%{search}%"),
                            Customer.name.ilike(f"%{search}%"),
                            Customer.mobile.ilike(f"%{search}%"))))
    jobs = q.order_by(JobCard.created_at.desc()).all()
    return jsonify({"count": len(jobs), "results": [j.to_dict() for j in jobs]})


@job_bp.get("/<int:jid>/")
@login_required
def job_details(user, jid):
    job = JobCard.query.filter_by(id=jid, shop_id=user.shop_id).first_or_404()
    return jsonify(job.to_dict(full=True))


@job_bp.post("/<int:jid>/update/")
@login_required
def update_job(user, jid):
    """Job details kabhi bhi edit kar sakte hain (device info, problem, cost, etc.)"""
    job = JobCard.query.filter_by(id=jid, shop_id=user.shop_id).first_or_404()
    d = request.get_json(force=True)
    changed = []
    for field in EDITABLE_FIELDS:
        if field in d:
            new_val = d[field]
            if field == "estimated_cost":
                new_val = float(new_val or 0)
            elif field == "lock_type":
                new_val = (new_val or "NONE").upper()
            old_val = getattr(job, field)
            if old_val != new_val:
                setattr(job, field, new_val)
                changed.append(field)
    if changed:
        JobActivityLog.log(user.shop_id, job.id, user.id, "EDITED",
                           f"{user.first_name} ne edit kiya: {', '.join(changed)}")
        db.session.commit()
    return jsonify(job.to_dict(full=True))


@job_bp.post("/<int:jid>/update_status/")
@login_required
def update_status(user, jid):
    """DELIVERED (ya RWR jisme payment lagta ho) + balance baaki -> auto KHATA (DEBIT) entry.
    BUG FIX: khata_debited flag se yeh sirf EK BAAR hi charge hota hai.
    RWR: reason + payment-required capture karta hai."""
    job = JobCard.query.filter_by(id=jid, shop_id=user.shop_id).first_or_404()
    d = request.get_json(force=True)
    status = (d.get("status") or "").upper()
    if status not in JOB_STATUSES:
        return jsonify({"detail": f"Status inme se ho: {JOB_STATUSES}"}), 400

    old_status = job.status
    job.status = status
    khata_added = 0

    if status == "RWR":
        job.rwr_reason = d.get("rwr_reason", job.rwr_reason)
        job.rwr_payment_required = bool(d.get("rwr_payment_required", False))
        if "rwr_amount" in d:
            job.rwr_amount = float(d.get("rwr_amount") or 0)

    # DELIVERED -> poora balance charge hota hai.
    # RWR (payment required) -> sirf rwr_amount (diagnostic/inspection fee) charge hota hai,
    # poora estimated repair cost NAHI (kyunki repair actually hua hi nahi).
    # khata_debited flag guarantee karta hai ki yeh EK BAAR hi charge ho, chahe status
    # dobara DELIVERED/RWR set kiya jaaye.
    charge_now = status == "DELIVERED" or (status == "RWR" and job.rwr_payment_required)
    if charge_now and not job.khata_debited:
        if status == "DELIVERED":
            job.delivered_at = now()
            owed = job.balance_amount
            note = f"Job {job.job_id} delivery par baaki"
        else:
            owed = round((job.rwr_amount or 0) - job.paid_amount, 2)
            note = f"Job {job.job_id} RWR charge (diagnostic/inspection fee)"
        if owed > 0:
            db.session.add(LedgerEntry(
                shop_id=user.shop_id, customer_id=job.customer_id,
                job_card_id=job.id, entry_type="DEBIT", amount=owed, note=note))
            khata_added = owed
        job.khata_debited = True

    db.session.add(StatusHistory(job_id=job.id, status=status,
                                 by_user=user.first_name, note=d.get("note", "")))
    if old_status != status:
        extra = f" (RWR reason: {job.rwr_reason})" if status == "RWR" and job.rwr_reason else ""
        JobActivityLog.log(user.shop_id, job.id, user.id, "STATUS",
                           f"{user.first_name}: {old_status} -> {status}{extra}")
    if d.get("note"):
        JobActivityLog.log(user.shop_id, job.id, user.id, "NOTE", d.get("note"))

    db.session.commit()
    resp = job.to_dict(full=True)
    resp["khata_added"] = khata_added
    if khata_added:
        resp["message"] = f"Rs. {khata_added} customer ke khate me add hua"
    elif charge_now and job.khata_debited:
        resp["message"] = "Yeh job pehle hi close ho chuki thi - dobara khata add nahi hua"
    return jsonify(resp)


@job_bp.post("/<int:jid>/assign/")
@login_required
def assign_job(user, jid):
    """Job kisi staff member ko assign karo (kaun kaam kar raha hai - full tracking)"""
    job = JobCard.query.filter_by(id=jid, shop_id=user.shop_id).first_or_404()
    d = request.get_json(force=True)
    staff_id = d.get("staff_id")
    if staff_id:
        staff = User.query.filter_by(id=staff_id, shop_id=user.shop_id).first_or_404()
        job.assigned_to_id = staff.id
        JobActivityLog.log(user.shop_id, job.id, user.id, "ASSIGNED",
                           f"{user.first_name} ne {staff.first_name} ko assign kiya")
    else:
        job.assigned_to_id = None
        JobActivityLog.log(user.shop_id, job.id, user.id, "ASSIGNED",
                           f"{user.first_name} ne assignment hataya")
    db.session.commit()
    return jsonify(job.to_dict(full=True))


@job_bp.get("/<int:jid>/activity/")
@login_required
def job_activity(user, jid):
    """Job ki poori activity/audit trail - kisne kya kiya, kab"""
    job = JobCard.query.filter_by(id=jid, shop_id=user.shop_id).first_or_404()
    logs = sorted(job.activity_log, key=lambda a: a.created_at)
    return jsonify({"job_id": job.job_id, "activity": [a.to_dict() for a in logs]})


# ---------------- JOB PHOTOS / VIDEOS ----------------
@job_bp.post("/<int:jid>/media/")
@login_required
def upload_media(user, jid):
    job = JobCard.query.filter_by(id=jid, shop_id=user.shop_id).first_or_404()
    file = request.files.get("file")
    filename, media_type = save_uploaded_media(file, current_app.config["JOB_MEDIA_FOLDER"])
    if not filename:
        return jsonify({"detail": "Sirf photo (jpg/png/webp) ya video (mp4/3gp/mkv/webm) allowed hai"}), 400
    media = JobMedia(job_card_id=job.id, media_type=media_type, file_path=filename,
                     caption=request.form.get("caption", ""), uploaded_by_id=user.id)
    db.session.add(media)
    JobActivityLog.log(user.shop_id, job.id, user.id, "EDITED",
                       f"{'Photo' if media_type == 'PHOTO' else 'Video'} add hui")
    db.session.commit()
    return jsonify(media.to_dict()), 201


@job_bp.get("/<int:jid>/media/")
@login_required
def list_media(user, jid):
    job = JobCard.query.filter_by(id=jid, shop_id=user.shop_id).first_or_404()
    return jsonify({"results": [m.to_dict() for m in job.media]})


@job_bp.post("/<int:jid>/media/<int:mid>/delete/")
@login_required
def delete_media(user, jid, mid):
    job = JobCard.query.filter_by(id=jid, shop_id=user.shop_id).first_or_404()
    media = JobMedia.query.filter_by(id=mid, job_card_id=job.id).first_or_404()
    db.session.delete(media)
    db.session.commit()
    return jsonify({"message": "Media delete ho gaya"})


# ---------------- CUSTOMER MESSAGE (WhatsApp/SMS templates) ----------------
@job_bp.get("/<int:jid>/message/<string:msg_type>/")
@login_required
def get_message(user, jid, msg_type):
    """Job ke liye ready-made message text deta hai (WhatsApp/SMS bhejne ke liye)"""
    job = JobCard.query.filter_by(id=jid, shop_id=user.shop_id).first_or_404()
    key_map = {"received": "template_received", "ready": "template_ready",
               "delivered": "template_delivered", "rwr": "template_rwr"}
    key = key_map.get(msg_type)
    if not key:
        return jsonify({"detail": "message type received/ready/delivered/rwr me se ho"}), 400
    text = user.shop.format_message(key, job)
    return jsonify({"message": text, "customer_mobile": job.customer.mobile})


@job_bp.post("/<int:jid>/use_part/")
@login_required
def use_part(user, jid):
    """Part use karo -> stock auto minus"""
    job = JobCard.query.filter_by(id=jid, shop_id=user.shop_id).first_or_404()
    d = request.get_json(force=True)
    product = Product.query.filter_by(id=d.get("product_id"),
                                      shop_id=user.shop_id).first_or_404()
    qty = int(d.get("quantity", 1))
    if product.quantity < qty:
        return jsonify({"detail": f"Stock kam hai (sirf {product.quantity} bache)"}), 400
    product.quantity -= qty
    db.session.add(UsedPart(job_card_id=job.id, product_id=product.id,
                            quantity=qty, price=product.sale_price))
    JobActivityLog.log(user.shop_id, job.id, user.id, "EDITED",
                       f"Part use hua: {product.name} x{qty}")
    db.session.commit()
    return jsonify({"message": "Part add hua, stock update",
                    "product": product.to_dict()})
