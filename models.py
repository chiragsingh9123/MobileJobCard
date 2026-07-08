"""Revenue Account - saare database models (SQLite)"""
import random, string
from datetime import datetime, timedelta
from werkzeug.security import generate_password_hash, check_password_hash
from extensions import db


def now():
    return datetime.now()


# ---------------- SHOP & USER ----------------
class Shop(db.Model):
    __tablename__ = "shops"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(120), nullable=False)
    address = db.Column(db.String(255), default="")
    city = db.Column(db.String(80), default="")
    gst_number = db.Column(db.String(30), default="")
    is_active = db.Column(db.Boolean, default=True)  # admin block/unblock
    created_at = db.Column(db.DateTime, default=now)

    # Print / receipt settings
    receipt_header = db.Column(db.String(255), default="")
    receipt_footer = db.Column(db.String(255), default="Thank you! Please visit again.")
    show_gst_on_receipt = db.Column(db.Boolean, default=False)

    # WhatsApp / SMS message templates (placeholders: {customer}, {job_id}, {shop})
    template_received = db.Column(db.Text, default="Hello {customer}, your job card {job_id} has been "
                                                     "received at {shop}. We will update you shortly.")
    template_ready = db.Column(db.Text, default="Hello {customer}, your device ({job_id}) is "
                                                 "ready! Please visit {shop} to collect it.")
    template_delivered = db.Column(db.Text, default="Hello {customer}, your device ({job_id}) has been "
                                                      "delivered. Thank you for visiting {shop}!")
    template_rwr = db.Column(db.Text, default="Hello {customer}, we were unable to repair your "
                                               "device ({job_id}). Please contact {shop} for more details.")

    users = db.relationship("User", backref="shop", lazy=True)

    def to_dict(self):
        return {"id": self.id, "name": self.name, "address": self.address,
                "city": self.city, "gst_number": self.gst_number,
                "is_active": self.is_active}

    def to_dict_full(self):
        d = self.to_dict()
        d.update({"receipt_header": self.receipt_header, "receipt_footer": self.receipt_footer,
                  "show_gst_on_receipt": self.show_gst_on_receipt,
                  "template_received": self.template_received,
                  "template_ready": self.template_ready,
                  "template_delivered": self.template_delivered,
                  "template_rwr": self.template_rwr})
        return d

    def format_message(self, template_key, job):
        """Template me {customer}/{job_id}/{shop} placeholders ko actual data se bharta hai"""
        template = getattr(self, template_key, "") or ""
        return (template.replace("{customer}", job.customer.name if job.customer else "")
                        .replace("{job_id}", job.job_id or "")
                        .replace("{shop}", self.name or ""))


class User(db.Model):
    __tablename__ = "users"
    id = db.Column(db.Integer, primary_key=True)
    mobile = db.Column(db.String(15), unique=True, nullable=False, index=True)
    password_hash = db.Column(db.String(255), nullable=False)
    first_name = db.Column(db.String(80), default="")
    last_name = db.Column(db.String(80), default="")
    email = db.Column(db.String(120), default="")
    role = db.Column(db.String(10), default="OWNER")  # OWNER / STAFF / ADMIN
    designation = db.Column(db.String(40), default="")  # e.g. "Technician", "Counter Staff"
    is_active = db.Column(db.Boolean, default=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"))
    created_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    last_seen_notification_id = db.Column(db.Integer, default=0)
    created_at = db.Column(db.DateTime, default=now)

    def set_password(self, raw):
        self.password_hash = generate_password_hash(raw)

    def check_password(self, raw):
        return check_password_hash(self.password_hash, raw)

    @property
    def is_owner(self): return self.role == "OWNER"
    @property
    def is_staff(self): return self.role == "STAFF"
    @property
    def is_admin(self): return self.role == "ADMIN"

    def to_dict(self):
        sub = None
        if self.shop:
            s = (Subscription.query.filter_by(shop_id=self.shop_id)
                 .order_by(Subscription.end_date.desc()).first())
            if s:
                sub = s.to_dict()
        return {"id": self.id, "mobile": self.mobile, "first_name": self.first_name,
                "last_name": self.last_name, "email": self.email, "role": self.role,
                "designation": self.designation, "is_active": self.is_active,
                "shop": self.shop.to_dict() if self.shop else None,
                "subscription": sub}

    def to_dict_staff(self):
        """Chhota dict - staff list me dikhane ke liye"""
        return {"id": self.id, "first_name": self.first_name, "last_name": self.last_name,
                "mobile": self.mobile, "role": self.role, "designation": self.designation,
                "is_active": self.is_active,
                "jobs_assigned": JobCard.query.filter_by(assigned_to_id=self.id).count()}


# ---------------- SUBSCRIPTION & VOUCHER ----------------
class SubscriptionPlan(db.Model):
    __tablename__ = "subscription_plans"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(80), nullable=False)  # Basic / Pro / Premium
    price = db.Column(db.Float, default=0)
    duration_days = db.Column(db.Integer, default=30)
    description = db.Column(db.String(255), default="")
    is_active = db.Column(db.Boolean, default=True)
    is_purchasable = db.Column(db.Boolean, default=True)  # False for auto Free-Trial plan

    def to_dict(self):
        return {"id": self.id, "name": self.name, "price": self.price,
                "duration_days": self.duration_days, "description": self.description}


class Subscription(db.Model):
    __tablename__ = "subscriptions"
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    plan_id = db.Column(db.Integer, db.ForeignKey("subscription_plans.id"), nullable=False)
    start_date = db.Column(db.DateTime, default=now)
    end_date = db.Column(db.DateTime, nullable=False)
    is_trial = db.Column(db.Boolean, default=False)
    created_at = db.Column(db.DateTime, default=now)

    shop = db.relationship("Shop", backref="subscriptions")
    plan = db.relationship("SubscriptionPlan")

    @property
    def days_remaining(self):
        d = (self.end_date - now()).days
        return max(d, 0)

    @property
    def is_active(self):
        return self.end_date > now()

    def to_dict(self):
        return {"id": self.id, "plan": self.plan.to_dict(),
                "start_date": self.start_date.isoformat(),
                "end_date": self.end_date.isoformat(),
                "days_remaining": self.days_remaining,
                "is_trial": self.is_trial, "is_active": self.is_active}

    @staticmethod
    def activate(shop, plan, extra_days=0):
        """Shop ke liye subscription activate/extend karo (current end se aage badhao)"""
        total_days = plan.duration_days + extra_days
        current = (Subscription.query.filter_by(shop_id=shop.id)
                   .order_by(Subscription.end_date.desc()).first())
        start = now()
        if current and current.end_date > now():
            start = current.end_date
        sub = Subscription(shop_id=shop.id, plan_id=plan.id,
                           start_date=start, end_date=start + timedelta(days=total_days))
        db.session.add(sub)
        return sub


class Voucher(db.Model):
    __tablename__ = "vouchers"
    id = db.Column(db.Integer, primary_key=True)
    code = db.Column(db.String(20), unique=True, nullable=False, index=True)
    plan_id = db.Column(db.Integer, db.ForeignKey("subscription_plans.id"), nullable=False)
    extra_days = db.Column(db.Integer, default=0)  # bonus days
    is_redeemed = db.Column(db.Boolean, default=False)
    redeemed_by_shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=True)
    redeemed_at = db.Column(db.DateTime, nullable=True)
    expires_at = db.Column(db.DateTime, nullable=True)  # voucher khud kab expire
    created_at = db.Column(db.DateTime, default=now)

    plan = db.relationship("SubscriptionPlan")
    redeemed_by_shop = db.relationship("Shop")

    @staticmethod
    def generate_code():
        while True:
            code = "RA-" + "".join(random.choices(string.ascii_uppercase + string.digits, k=10))
            if not Voucher.query.filter_by(code=code).first():
                return code

    def redeem(self, shop):
        """Voucher redeem karo -> subscription banao/extend karo"""
        if self.is_redeemed:
            return None, "This voucher has already been used"
        if self.expires_at and self.expires_at < now():
            return None, "This voucher has expired"

        sub = Subscription.activate(shop, self.plan, self.extra_days)
        self.is_redeemed = True
        self.redeemed_by_shop_id = shop.id
        self.redeemed_at = now()
        db.session.commit()
        return sub, None


# ---------------- UPI PAYMENT VERIFICATION (manual) ----------------
class PlatformSettings(db.Model):
    """Singleton row: platform-wide settings (UPI payment details + app update control)"""
    __tablename__ = "platform_settings"
    id = db.Column(db.Integer, primary_key=True)
    upi_id = db.Column(db.String(120), default="")
    upi_name = db.Column(db.String(120), default="")
    qr_image_path = db.Column(db.String(255), default="")  # relative path under uploads/qr/
    updated_at = db.Column(db.DateTime, default=now)

    # Force-update control
    force_update_enabled = db.Column(db.Boolean, default=False)
    min_version_code = db.Column(db.Integer, default=1)
    update_message = db.Column(db.String(500),
                               default="A new version is available. Please update to continue.")
    play_store_url = db.Column(db.String(255), default="")

    @staticmethod
    def get():
        s = PlatformSettings.query.first()
        if not s:
            s = PlatformSettings()
            db.session.add(s)
            db.session.commit()
        return s

    def to_dict(self):
        return {"upi_id": self.upi_id, "upi_name": self.upi_name,
                "qr_image_url": f"/uploads/qr/{self.qr_image_path}" if self.qr_image_path else None,
                "force_update_enabled": self.force_update_enabled,
                "min_version_code": self.min_version_code,
                "update_message": self.update_message,
                "play_store_url": self.play_store_url}


class PaymentRequest(db.Model):
    """User UPI se pay karke screenshot + UTR submit karta hai, admin manually verify karta hai"""
    __tablename__ = "payment_requests"
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    plan_id = db.Column(db.Integer, db.ForeignKey("subscription_plans.id"), nullable=False)
    amount = db.Column(db.Float, default=0)
    utr_number = db.Column(db.String(60), default="")
    screenshot_path = db.Column(db.String(255), default="")  # relative path under uploads/screenshots/
    status = db.Column(db.String(12), default="PENDING")  # PENDING / APPROVED / REJECTED
    admin_note = db.Column(db.String(255), default="")
    reviewed_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    reviewed_at = db.Column(db.DateTime, nullable=True)
    created_at = db.Column(db.DateTime, default=now)

    shop = db.relationship("Shop")
    plan = db.relationship("SubscriptionPlan")

    def to_dict(self):
        return {"id": self.id, "shop": self.shop.to_dict(), "plan": self.plan.to_dict(),
                "amount": self.amount, "utr_number": self.utr_number,
                "screenshot_url": f"/uploads/screenshots/{self.screenshot_path}" if self.screenshot_path else None,
                "status": self.status, "admin_note": self.admin_note,
                "created_at": self.created_at.isoformat(),
                "reviewed_at": self.reviewed_at.isoformat() if self.reviewed_at else None}


# ---------------- CUSTOMER ----------------
class Customer(db.Model):
    __tablename__ = "customers"
    __table_args__ = (db.UniqueConstraint("shop_id", "mobile", name="uq_shop_mobile"),)
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    name = db.Column(db.String(120), nullable=False)
    mobile = db.Column(db.String(15), nullable=False, index=True)
    email = db.Column(db.String(120), default="")
    address = db.Column(db.String(255), default="")
    city = db.Column(db.String(80), default="")
    customer_type = db.Column(db.String(10), default="NORMAL")  # NORMAL/REPEAT/VIP
    notes = db.Column(db.Text, default="")
    created_at = db.Column(db.DateTime, default=now)

    shop = db.relationship("Shop")
    jobs = db.relationship("JobCard", backref="customer", lazy=True, foreign_keys="JobCard.customer_id")

    @property
    def total_jobs(self):
        return len(self.jobs)

    @property
    def total_spent(self):
        return sum(p.amount for j in self.jobs for p in j.payments)

    @property
    def khata_balance(self):
        """DEBIT (udhaar) - CREDIT (jama) = baaki"""
        debit = sum(e.amount for e in self.ledger_entries if e.entry_type == "DEBIT")
        credit = sum(e.amount for e in self.ledger_entries if e.entry_type == "CREDIT")
        return round(debit - credit, 2)

    def to_dict(self, full=False):
        d = {"id": self.id, "name": self.name, "mobile": self.mobile,
             "email": self.email, "address": self.address, "city": self.city,
             "customer_type": self.customer_type,
             "total_jobs": self.total_jobs,
             "khata_balance": self.khata_balance}
        if full:
            d["total_spent"] = self.total_spent
            d["notes"] = self.notes
        return d


# ---------------- JOB CARD ----------------
JOB_STATUSES = ["RECEIVED", "REPAIRING", "WAITING_PARTS", "READY", "DELIVERED", "RWR"]

class JobCard(db.Model):
    __tablename__ = "job_cards"
    __table_args__ = (
        # Speeds up the most common dashboard/list queries: filtering a shop's
        # jobs by status, and ordering/filtering a shop's jobs by date.
        db.Index("ix_jobcards_shop_status", "shop_id", "status"),
        db.Index("ix_jobcards_shop_created", "shop_id", "created_at"),
    )
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    customer_id = db.Column(db.Integer, db.ForeignKey("customers.id"), nullable=False, index=True)
    job_id = db.Column(db.String(20), index=True)  # RA-100001
    custom_name = db.Column(db.String(120), default="")  # Customised job name/title
    device_brand = db.Column(db.String(60), default="")
    device_model = db.Column(db.String(80), default="")
    imei1 = db.Column(db.String(20), default="")
    imei2 = db.Column(db.String(20), default="")
    color = db.Column(db.String(30), default="")
    storage = db.Column(db.String(20), default="")
    lock_type = db.Column(db.String(10), default="NONE")  # NONE / PIN / PATTERN
    device_password = db.Column(db.String(50), default="")  # PIN digits, ya pattern sequence jaise "0-1-2-5-8"
    accessories = db.Column(db.String(255), default="")
    alternate_mobile = db.Column(db.String(15), default="")  # extra contact number
    problem = db.Column(db.Text, default="")
    diagnosis = db.Column(db.Text, default="")
    estimated_cost = db.Column(db.Float, default=0)
    advance_amount = db.Column(db.Float, default=0)
    status = db.Column(db.String(20), default="RECEIVED", index=True)
    expected_delivery = db.Column(db.String(30), default="")
    created_at = db.Column(db.DateTime, default=now, index=True)
    delivered_at = db.Column(db.DateTime, nullable=True)

    # RWR (Return Without Repair) - reason + payment tracking
    rwr_reason = db.Column(db.String(255), default="")
    rwr_payment_required = db.Column(db.Boolean, default=False)
    rwr_amount = db.Column(db.Float, default=0)

    # Delivery-time discount (e.g. customer ko 50/100 rupaye ki chhoot di gayi)
    discount_amount = db.Column(db.Float, default=0)

    # Identity/ownership documentation (Aadhaar + Bill/Box details)
    aadhaar_number = db.Column(db.String(20), default="")
    bill_number = db.Column(db.String(60), default="")

    # FULL TRACKING: kisne receive kiya, kisko assign hai, kisne deliver kiya, khata duplicate-guard
    received_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    assigned_to_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    delivered_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    khata_debited = db.Column(db.Boolean, default=False)  # duplicate-DEBIT bug fix guard

    shop = db.relationship("Shop")
    received_by = db.relationship("User", foreign_keys=[received_by_id])
    assigned_to = db.relationship("User", foreign_keys=[assigned_to_id])
    delivered_by = db.relationship("User", foreign_keys=[delivered_by_id])
    payments = db.relationship("Payment", backref="job", lazy=True)
    status_history = db.relationship("StatusHistory", backref="job", lazy=True,
                                     order_by="StatusHistory.created_at")
    activity_log = db.relationship("JobActivityLog", backref="job", lazy=True,
                                   order_by="JobActivityLog.created_at")
    used_parts = db.relationship("UsedPart", backref="job", lazy=True)
    media = db.relationship("JobMedia", backref="job", lazy=True, order_by="JobMedia.created_at")

    @staticmethod
    def next_job_id(shop_id):
        last = (JobCard.query.filter_by(shop_id=shop_id)
                .order_by(JobCard.id.desc()).first())
        n = 100001
        if last and last.job_id and "-" in last.job_id:
            try:
                n = int(last.job_id.split("-")[1]) + 1
            except ValueError:
                pass
        return f"RA-{n}"

    @property
    def paid_amount(self):
        return round(sum(p.amount for p in self.payments), 2)

    @property
    def balance_amount(self):
        """Discount ghata kar hi asli baaki nikalta hai"""
        return round(self.estimated_cost - (self.discount_amount or 0) - self.paid_amount, 2)

    def to_dict(self, full=False):
        d = {"id": self.id, "job_id": self.job_id, "custom_name": self.custom_name,
             "status": self.status,
             "customer": self.customer.to_dict(),
             "device_brand": self.device_brand, "device_model": self.device_model,
             "problem": self.problem, "estimated_cost": self.estimated_cost,
             "discount_amount": self.discount_amount,
             "paid_amount": self.paid_amount, "balance_amount": self.balance_amount,
             "assigned_to": self.assigned_to.to_dict_staff() if self.assigned_to else None,
             "received_by_name": self.received_by.first_name if self.received_by else "",
             "delivered_by_name": self.delivered_by.first_name if self.delivered_by else "",
             "created_at": self.created_at.isoformat()}
        if full:
            d.update({"imei1": self.imei1, "imei2": self.imei2, "color": self.color,
                      "storage": self.storage, "lock_type": self.lock_type,
                      "device_password": self.device_password,
                      "accessories": self.accessories, "diagnosis": self.diagnosis,
                      "alternate_mobile": self.alternate_mobile,
                      "expected_delivery": self.expected_delivery,
                      "rwr_reason": self.rwr_reason,
                      "rwr_payment_required": self.rwr_payment_required,
                      "rwr_amount": self.rwr_amount,
                      "aadhaar_number": self.aadhaar_number,
                      "bill_number": self.bill_number,
                      "status_history": [h.to_dict() for h in self.status_history],
                      "activity_log": [a.to_dict() for a in self.activity_log],
                      "media": [m.to_dict() for m in self.media]})
        return d


class StatusHistory(db.Model):
    __tablename__ = "status_history"
    id = db.Column(db.Integer, primary_key=True)
    job_id = db.Column(db.Integer, db.ForeignKey("job_cards.id"), nullable=False, index=True)
    status = db.Column(db.String(20))
    note = db.Column(db.String(255), default="")
    by_user = db.Column(db.String(80), default="")
    created_at = db.Column(db.DateTime, default=now)

    def to_dict(self):
        return {"status": self.status, "note": self.note, "by_user": self.by_user,
                "created_at": self.created_at.isoformat()}


class JobActivityLog(db.Model):
    """FULL AUDIT TRAIL: har action (created/assigned/status/payment/note) yahan log hota hai"""
    __tablename__ = "job_activity_log"
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    job_card_id = db.Column(db.Integer, db.ForeignKey("job_cards.id"), nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    action = db.Column(db.String(20))  # CREATED/STATUS/ASSIGNED/PAYMENT/NOTE/EDITED
    description = db.Column(db.String(255), default="")
    created_at = db.Column(db.DateTime, default=now)

    user = db.relationship("User")

    def to_dict(self):
        return {"action": self.action, "description": self.description,
                "by_user": self.user.first_name if self.user else "System",
                "created_at": self.created_at.isoformat()}

    @staticmethod
    def log(shop_id, job_card_id, user_id, action, description):
        db.session.add(JobActivityLog(shop_id=shop_id, job_card_id=job_card_id,
                                      user_id=user_id, action=action, description=description))


# ---------------- INVENTORY ----------------
class Product(db.Model):
    __tablename__ = "products"
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    name = db.Column(db.String(120), nullable=False)
    category = db.Column(db.String(20), default="ACCESSORY")  # DISPLAY/BATTERY/IC/CONNECTOR/ACCESSORY
    brand = db.Column(db.String(60), default="")
    model_compatibility = db.Column(db.String(255), default="")
    purchase_price = db.Column(db.Float, default=0)
    sale_price = db.Column(db.Float, default=0)
    quantity = db.Column(db.Integer, default=0)
    low_stock_threshold = db.Column(db.Integer, default=3)
    created_at = db.Column(db.DateTime, default=now)

    shop = db.relationship("Shop")

    @property
    def stock_status(self):
        if self.quantity <= 0:
            return "OUT"
        if self.quantity <= self.low_stock_threshold:
            return "LOW"
        return "IN"

    def to_dict(self):
        return {"id": self.id, "name": self.name, "category": self.category,
                "brand": self.brand, "model_compatibility": self.model_compatibility,
                "purchase_price": self.purchase_price, "sale_price": self.sale_price,
                "quantity": self.quantity, "stock_status": self.stock_status}


class UsedPart(db.Model):
    __tablename__ = "used_parts"
    id = db.Column(db.Integer, primary_key=True)
    job_card_id = db.Column(db.Integer, db.ForeignKey("job_cards.id"), nullable=False, index=True)
    product_id = db.Column(db.Integer, db.ForeignKey("products.id"), nullable=False)
    quantity = db.Column(db.Integer, default=1)
    price = db.Column(db.Float, default=0)

    product = db.relationship("Product")


# ---------------- JOB PHOTOS / VIDEOS ----------------
class JobMedia(db.Model):
    """Device photo/video (damage proof, condition, ownership documentation, etc.)"""
    __tablename__ = "job_media"
    id = db.Column(db.Integer, primary_key=True)
    job_card_id = db.Column(db.Integer, db.ForeignKey("job_cards.id"), nullable=False, index=True)
    media_type = db.Column(db.String(10), default="PHOTO")  # PHOTO / VIDEO
    file_path = db.Column(db.String(255), default="")  # relative path under uploads/job_media/
    caption = db.Column(db.String(120), default="")
    # Category tags used by the Aadhaar/Bill documentation feature. GENERAL is used
    # for the regular Photos & Videos section on the Job Details screen.
    # Other values: AADHAAR_FRONT, AADHAAR_BACK, AADHAAR_STATEMENT,
    # BILL_PHOTO, BOX_PHOTO, BILL_STATEMENT
    category = db.Column(db.String(30), default="GENERAL")
    uploaded_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    created_at = db.Column(db.DateTime, default=now)

    uploaded_by = db.relationship("User")

    def to_dict(self):
        return {"id": self.id, "media_type": self.media_type,
                "url": f"/uploads/job_media/{self.file_path}" if self.file_path else None,
                "caption": self.caption, "category": self.category,
                "uploaded_by": self.uploaded_by.first_name if self.uploaded_by else "",
                "created_at": self.created_at.isoformat()}


# ---------------- PAYMENTS & KHATA ----------------
class Payment(db.Model):
    __tablename__ = "payments"
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    customer_id = db.Column(db.Integer, db.ForeignKey("customers.id"))
    job_card_id = db.Column(db.Integer, db.ForeignKey("job_cards.id"), nullable=True)
    amount = db.Column(db.Float, nullable=False)
    method = db.Column(db.String(10), default="CASH")  # CASH/UPI/CARD/MIXED
    payment_type = db.Column(db.String(15), default="FINAL")  # ADVANCE/FINAL/KHATA_SETTLE
    note = db.Column(db.String(255), default="")
    created_at = db.Column(db.DateTime, default=now)

    customer = db.relationship("Customer", backref="payments")

    def to_dict(self):
        return {"id": self.id, "amount": self.amount, "method": self.method,
                "payment_type": self.payment_type, "note": self.note,
                "created_at": self.created_at.isoformat()}


class LedgerEntry(db.Model):
    """KHATA: DEBIT = udhaar gaya, CREDIT = wapas mila. job_card_id se pata chalta
    hai yeh kis job ke against hai (behtar per-job tracking + transparency)."""
    __tablename__ = "ledger_entries"
    __table_args__ = (
        # Khata balance lookups always filter by shop_id + customer_id together
        db.Index("ix_ledger_shop_customer", "shop_id", "customer_id"),
    )
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    customer_id = db.Column(db.Integer, db.ForeignKey("customers.id"), nullable=False, index=True)
    job_card_id = db.Column(db.Integer, db.ForeignKey("job_cards.id"), nullable=True)
    entry_type = db.Column(db.String(6), nullable=False)  # DEBIT / CREDIT
    amount = db.Column(db.Float, nullable=False)
    note = db.Column(db.String(255), default="")
    created_at = db.Column(db.DateTime, default=now)

    customer = db.relationship("Customer", backref="ledger_entries")
    job = db.relationship("JobCard")

    def to_dict(self):
        return {"id": self.id, "entry_type": self.entry_type, "amount": self.amount,
                "note": self.note, "job_id": self.job.job_id if self.job else None,
                "created_at": self.created_at.isoformat()}


class Expense(db.Model):
    __tablename__ = "expenses"
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    title = db.Column(db.String(120), nullable=False)
    amount = db.Column(db.Float, nullable=False)
    category = db.Column(db.String(30), default="OTHER")  # RENT/SALARY/PARTS/ELECTRICITY/OTHER
    created_at = db.Column(db.DateTime, default=now)

    def to_dict(self):
        return {"id": self.id, "title": self.title, "amount": self.amount,
                "category": self.category, "created_at": self.created_at.isoformat()}


# ---------------- OTP VERIFICATION (Telegram ke through, temporary) ----------------
class OTPCode(db.Model):
    """Register/Login ke liye 6-digit OTP. Filhaal Telegram bot par bhejte hain
    (real SMS gateway aane tak). Ek mobile+purpose ka sirf sabse naya OTP valid hota hai."""
    __tablename__ = "otp_codes"
    id = db.Column(db.Integer, primary_key=True)
    mobile = db.Column(db.String(15), nullable=False, index=True)
    code = db.Column(db.String(6), nullable=False)
    purpose = db.Column(db.String(20), nullable=False)  # REGISTER / LOGIN / RESET_PASSWORD
    is_used = db.Column(db.Boolean, default=False)
    attempts = db.Column(db.Integer, default=0)
    expires_at = db.Column(db.DateTime, nullable=False)
    created_at = db.Column(db.DateTime, default=now)

    @staticmethod
    def generate(mobile, purpose, expiry_minutes=5):
        code = "".join(random.choices(string.digits, k=6))
        otp = OTPCode(mobile=mobile, code=code, purpose=purpose,
                     expires_at=now() + timedelta(minutes=expiry_minutes))
        db.session.add(otp)
        db.session.commit()
        return otp

    @staticmethod
    def latest_pending(mobile, purpose):
        return (OTPCode.query.filter_by(mobile=mobile, purpose=purpose, is_used=False)
                .order_by(OTPCode.created_at.desc()).first())

    def verify(self, submitted_code, max_attempts=5):
        """Code check karo. Galat code baar-baar try karne par lock ho jaata hai."""
        if self.is_used:
            return False, "This OTP has already been used"
        if self.expires_at < now():
            return False, "This OTP has expired, please request a new one"
        if self.attempts >= max_attempts:
            return False, "Too many incorrect attempts, please request a new OTP"
        self.attempts += 1
        if self.code != str(submitted_code).strip():
            db.session.commit()
            return False, f"Incorrect OTP ({max_attempts - self.attempts} attempts remaining)"
        self.is_used = True
        db.session.commit()
        return True, None


class Notification(db.Model):
    """Admin-broadcast notification, shown to every user the next time they open
    the app. Read-tracking is done via User.last_seen_notification_id rather than
    a per-user join table, since these are simple broadcast announcements (not
    individually targeted), which keeps the read/unread check to a single cheap
    comparison instead of a join."""
    __tablename__ = "notifications"
    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(120), nullable=False)
    message = db.Column(db.String(1000), nullable=False)
    is_active = db.Column(db.Boolean, default=True)  # admin can retract a notification
    created_at = db.Column(db.DateTime, default=now, index=True)

    def to_dict(self):
        return {"id": self.id, "title": self.title, "message": self.message,
                "created_at": self.created_at.isoformat()}
