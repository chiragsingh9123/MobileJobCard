"""Test admin payment-request approval flow (session-based admin panel)"""
from run import app
from extensions import db
from models import User, PaymentRequest, Subscription

c = app.test_client()

with app.app_context():
    if not User.query.filter_by(role="ADMIN").first():
        admin = User(mobile="9999900000", first_name="SuperAdmin", role="ADMIN")
        admin.set_password("admin123")
        db.session.add(admin)
        db.session.commit()

# admin login
r = c.post("/admin/login/", data={"mobile":"9999900000","password":"admin123"}, follow_redirects=True)
print("Admin login:", r.status_code)
assert r.status_code == 200

r = c.get("/admin/payment-requests/")
print("Payment requests page:", r.status_code)
assert r.status_code == 200
assert b"UTR123456789" in r.data
print("Admin can see pending payment request with UTR + screenshot link")

with app.app_context():
    pr = PaymentRequest.query.filter_by(utr_number="UTR123456789").first()
    pr_id = pr.id
    shop_id = pr.shop_id

r = c.post(f"/admin/payment-requests/{pr_id}/approve/", follow_redirects=True)
print("Approve:", r.status_code)
assert r.status_code == 200

with app.app_context():
    pr = PaymentRequest.query.get(pr_id)
    print("Request status after approve:", pr.status)
    assert pr.status == "APPROVED"
    sub = Subscription.query.filter_by(shop_id=shop_id).order_by(Subscription.end_date.desc()).first()
    print("New subscription plan:", sub.plan.name, "days:", sub.days_remaining)
    assert sub.plan.name == "Pro"

print("Admin approval activates subscription correctly")
print("\nADMIN APPROVAL FLOW WORKS END-TO-END")
