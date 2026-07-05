"""Full flow test - backend verify karne ke liye (OTP flow ke saath)"""
import json
from run import app
from extensions import db
from models import User, SubscriptionPlan, Voucher, OTPCode

c = app.test_client()

def j(r): return r.get_json()

def get_otp(mobile, purpose):
    """Test me Telegram nahi bhej sakte, isliye DB se seedha OTP nikal lete hain"""
    with app.app_context():
        row = OTPCode.latest_pending(mobile, purpose)
        return row.code if row else None

# 1. Send OTP + Register
r = c.post("/api/auth/send-otp/", json={"mobile":"9876543210","purpose":"REGISTER"})
assert r.status_code == 200, r.get_data()
otp = get_otp("9876543210", "REGISTER")
print("1. OTP generated for register:", otp)

r = c.post("/api/auth/register/", json={"mobile":"9876543210","password":"test123",
    "first_name":"Gaurav","shop_name":"Singh Mobile Repair","city":"Meerut","otp":otp})
assert r.status_code == 201, r.get_data()
tok = j(r)["tokens"]["access"]
H = {"Authorization": f"Bearer {tok}"}
print("2. Register + trial:", j(r)["user"]["subscription"]["plan"]["name"])

# 3. Wrong OTP rejected
r = c.post("/api/auth/send-otp/", json={"mobile":"9111111111","purpose":"REGISTER"})
r2 = c.post("/api/auth/register/", json={"mobile":"9111111111","password":"test123",
    "first_name":"X","shop_name":"Y","otp":"000000"})
assert r2.status_code == 400
print("3. Wrong OTP rejected:", j(r2)["detail"])

# 4. Login requires OTP
r = c.post("/api/auth/login/", json={"mobile":"9876543210","password":"test123"})
assert r.status_code == 400
print("4. Login without OTP blocked:", j(r)["detail"])

r = c.post("/api/auth/send-otp/", json={"mobile":"9876543210","purpose":"LOGIN"})
otp = get_otp("9876543210", "LOGIN")
r = c.post("/api/auth/login/", json={"mobile":"9876543210","password":"test123","otp":otp})
assert r.status_code == 200
print("5. Login with correct OTP works")

# 6. Staff creation (owner only)
r = c.post("/api/staff/", json={"mobile":"9988776655","password":"tech123",
    "first_name":"Ramesh","designation":"Technician"}, headers=H)
assert r.status_code == 201, r.get_data()
staff_id = j(r)["id"]
print("6. Staff created:", j(r))

# 7. Create job with inline customer + assign to staff
r = c.post("/api/jobs/", json={"customer_data":{"name":"Rahul Singh","mobile":"9876543211",
    "address":"Azamgarh","city":"Meerut"},"device_brand":"Vivo","device_model":"Y22",
    "problem":"Charging nahi ho raha","estimated_cost":800,"assigned_to_id":staff_id}, headers=H)
job = j(r); print("7. Job:", job["job_id"], "assigned_to:", job["assigned_to"])

# 8. Auto-fetch
r = c.get("/api/customers/lookup/?mobile=9876543211", headers=H)
assert j(r)["found"]; print("8. Auto-fetch:", j(r)["customer"]["name"])

# 9. Advance 300
c.post("/api/payments/payments/", json={"amount":300,"customer_id":job["customer"]["id"],
    "job_card_id":job["id"],"payment_type":"ADVANCE"}, headers=H)

# 10. Deliver -> khata (test the FIXED bug: double-deliver should NOT double-charge)
r = c.post(f"/api/jobs/{job['id']}/update_status/", json={"status":"DELIVERED"}, headers=H)
print("9. Delivered, khata_added:", j(r)["khata_added"])
r2 = c.post(f"/api/jobs/{job['id']}/update_status/", json={"status":"DELIVERED"}, headers=H)
print("10. Second DELIVERED call, khata_added (should be 0):", j(r2)["khata_added"])
assert j(r2)["khata_added"] == 0, "BUG: double-charged khata!"

# 11. Khata pending
r = c.get("/api/payments/khata/pending/", headers=H)
print("11. Khata pending total:", j(r)["total_pending"])
assert j(r)["total_pending"] == 500.0

# 12. Partial settle
c.post("/api/payments/payments/", json={"amount":200,"customer_id":job["customer"]["id"],
    "payment_type":"KHATA_SETTLE"}, headers=H)
r = c.get("/api/payments/khata/pending/", headers=H)
print("12. After partial 200 settle, pending (expect 300):", j(r)["total_pending"])
assert j(r)["total_pending"] == 300.0

# 13. Full settle
c.post("/api/payments/payments/", json={"amount":300,"customer_id":job["customer"]["id"],
    "payment_type":"KHATA_SETTLE"}, headers=H)
r = c.get("/api/payments/khata/pending/", headers=H)
print("13. After full settle, pending (expect 0):", j(r)["total_pending"])
assert j(r)["total_pending"] == 0

# 14. Voucher
with app.app_context():
    plan = SubscriptionPlan(name="Pro", price=499, duration_days=30)
    db.session.add(plan); db.session.flush()
    v = Voucher(code=Voucher.generate_code(), plan_id=plan.id, extra_days=5)
    db.session.add(v); db.session.commit()
    code = v.code
    plan_id = plan.id
r = c.post("/api/auth/redeem-voucher/", json={"code":code}, headers=H)
print("14.", j(r)["message"])

# 15. Job activity log (full tracking)
r = c.get(f"/api/jobs/{job['id']}/activity/", headers=H)
print("15. Job activity log entries:", len(j(r)["activity"]))
for a in j(r)["activity"]:
    print("   -", a["action"], ":", a["description"])

# 16. Shop-wide activity log
r = c.get("/api/shop/activity-log/", headers=H)
print("16. Shop activity log count:", j(r)["count"])

# 17. UPI subscription request flow
r = c.get("/api/subscription/upi-details/", headers=H)
print("17. UPI details:", j(r))

import io
r = c.post("/api/subscription/submit-payment/",
    data={"plan_id": str(plan_id), "utr_number": "UTR123456789",
          "screenshot": (io.BytesIO(b"fake image bytes"), "test.jpg")},
    content_type="multipart/form-data", headers=H)
print("18. Payment request submit:", r.status_code, j(r).get("message"))

# 19. Inventory + dashboard
c.post("/api/inventory/products/", json={"name":"Vivo Y22 Battery","category":"BATTERY",
    "purchase_price":450,"sale_price":800,"quantity":2}, headers=H)
r = c.get("/api/reports/dashboard/", headers=H)
print("19. Dashboard:", json.dumps(j(r)))
r = c.get("/api/reports/analytics/?days=30", headers=H)
print("20. Analytics profit:", j(r)["profit"])

print("\nALL TESTS PASS! (OTP + Staff + Khata-fix + Activity-log + UPI flow)")
