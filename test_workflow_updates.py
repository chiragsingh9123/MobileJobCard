"""Test: staff attribution on create/status-change, READY requires final_amount,
discount at delivery, delivered_by tracking"""
from run import app
from models import OTPCode

c = app.test_client()
def j(r): return r.get_json()
def get_otp(mobile, purpose):
    with app.app_context():
        row = OTPCode.latest_pending(mobile, purpose)
        return row.code if row else None

# Setup: register owner + 2 staff
c.post("/api/auth/send-otp/", json={"mobile":"9111000001","purpose":"REGISTER"})
otp = get_otp("9111000001", "REGISTER")
r = c.post("/api/auth/register/", json={"mobile":"9111000001","password":"test123",
    "first_name":"Owner","shop_name":"Workflow Test Shop","otp":otp})
tok = j(r)["tokens"]["access"]
H = {"Authorization": f"Bearer {tok}"}

r = c.post("/api/staff/", json={"mobile":"9111000002","password":"p1",
    "first_name":"Ramesh","designation":"Technician"}, headers=H)
ramesh_id = j(r)["id"]
r = c.post("/api/staff/", json={"mobile":"9111000003","password":"p1",
    "first_name":"Suresh","designation":"Counter Staff"}, headers=H)
suresh_id = j(r)["id"]

# 1. Create job with created_by_staff_id (Ramesh is "saving" it)
r = c.post("/api/jobs/", json={"customer_data":{"name":"Test Cust","mobile":"9988000001"},
    "device_model":"Vivo Y20","problem":"Screen crack","estimated_cost":1000,
    "created_by_staff_id": ramesh_id}, headers=H)
job = j(r)
job_id = job["id"]
print("1. Job created_by:", job["received_by_name"])
assert job["received_by_name"] == "Ramesh"

# 2. Trying READY without final_amount should fail
r = c.post(f"/api/jobs/{job_id}/update_status/", json={"status":"READY"}, headers=H)
print("2. READY without final_amount:", r.status_code)
assert r.status_code == 400

# 3. READY with final_amount + changed_by_staff_id should succeed
r = c.post(f"/api/jobs/{job_id}/update_status/", json={"status":"READY",
    "final_amount": 850, "changed_by_staff_id": suresh_id}, headers=H)
updated = j(r)
print("3. READY with final_amount:", updated["status"], "estimated_cost:", updated["estimated_cost"])
assert r.status_code == 200
assert updated["estimated_cost"] == 850.0

# 4. Check status_history shows Suresh made the change
r = c.get(f"/api/jobs/{job_id}/", headers=H)
history = j(r)["status_history"]
print("4. Status history last entry by_user:", history[-1]["by_user"])
assert history[-1]["by_user"] == "Suresh"

# 5. Deliver with discount + delivered_by
r = c.post(f"/api/jobs/{job_id}/update_status/", json={"status":"DELIVERED",
    "discount_amount": 50, "delivered_by_staff_id": ramesh_id}, headers=H)
delivered = j(r)
print("5. Delivered - discount:", delivered["discount_amount"],
      "delivered_by:", delivered["delivered_by_name"], "khata_added:", delivered["khata_added"])
assert delivered["discount_amount"] == 50.0
assert delivered["delivered_by_name"] == "Ramesh"
# balance = estimated_cost(850) - discount(50) - paid(0) = 800
assert delivered["khata_added"] == 800.0

# 6. Job activity log shows all attribution
r = c.get(f"/api/jobs/{job_id}/activity/", headers=H)
for a in j(r)["activity"]:
    print("   -", a["action"], ":", a["description"])

print("\nALL WORKFLOW-UPDATE TESTS PASS!")
