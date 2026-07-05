"""Test: Editable job, media upload, pattern/PIN, RWR reason+payment, search by date/name, message templates"""
import io, json
from run import app
from models import OTPCode

c = app.test_client()
def j(r): return r.get_json()
def get_otp(mobile, purpose):
    with app.app_context():
        row = OTPCode.latest_pending(mobile, purpose)
        return row.code if row else None

# Setup: register + login
c.post("/api/auth/send-otp/", json={"mobile":"9123456780","purpose":"REGISTER"})
otp = get_otp("9123456780", "REGISTER")
r = c.post("/api/auth/register/", json={"mobile":"9123456780","password":"test123",
    "first_name":"Owner","shop_name":"Test Shop","otp":otp})
tok = j(r)["tokens"]["access"]
H = {"Authorization": f"Bearer {tok}"}

# 1. Create job with custom_name, pattern lock, alternate_mobile
r = c.post("/api/jobs/", json={"customer_data":{"name":"Amit Kumar","mobile":"9876500001"},
    "custom_name":"VIP Customer Repair","device_brand":"Samsung","device_model":"M31",
    "problem":"Screen crack","estimated_cost":1200,"lock_type":"PATTERN",
    "device_password":"0-1-2-5-8","alternate_mobile":"9988877766"}, headers=H)
job = j(r)
print("1. Job created with custom_name:", job["custom_name"], "lock_type:", job["lock_type"])
assert job["custom_name"] == "VIP Customer Repair"
assert job["lock_type"] == "PATTERN"
assert job["device_password"] == "0-1-2-5-8"
job_id = job["id"]

# 2. Edit job details (device info change)
r = c.post(f"/api/jobs/{job_id}/update/", json={"problem":"Screen crack + battery issue",
    "estimated_cost": 1500, "custom_name": "VIP Customer - Updated"}, headers=H)
updated = j(r)
print("2. Job edited:", updated["problem"], updated["estimated_cost"], updated["custom_name"])
assert updated["estimated_cost"] == 1500
assert "battery" in updated["problem"]

# 3. Upload photo
r = c.post(f"/api/jobs/{job_id}/media/",
    data={"caption": "Front damage", "file": (io.BytesIO(b"fake jpg bytes"), "photo1.jpg")},
    content_type="multipart/form-data", headers=H)
print("3. Photo upload:", r.status_code, j(r))
assert r.status_code == 201
assert j(r)["media_type"] == "PHOTO"

# 4. Upload video
r = c.post(f"/api/jobs/{job_id}/media/",
    data={"caption": "Working demo", "file": (io.BytesIO(b"fake mp4 bytes"), "vid1.mp4")},
    content_type="multipart/form-data", headers=H)
print("4. Video upload:", r.status_code, j(r)["media_type"])
assert j(r)["media_type"] == "VIDEO"

# 5. Reject invalid file type
r = c.post(f"/api/jobs/{job_id}/media/",
    data={"file": (io.BytesIO(b"exe bytes"), "virus.exe")},
    content_type="multipart/form-data", headers=H)
print("5. Invalid file type rejected:", r.status_code)
assert r.status_code == 400

# 6. List media
r = c.get(f"/api/jobs/{job_id}/media/", headers=H)
print("6. Media list count:", len(j(r)["results"]))
assert len(j(r)["results"]) == 2

# 7. RWR with payment required
r = c.post(f"/api/jobs/{job_id}/update_status/", json={"status":"RWR",
    "rwr_reason":"Motherboard dead, part available nahi hai","rwr_payment_required":True,
    "rwr_amount":300}, headers=H)
rwr_job = j(r)
print("7. RWR status:", rwr_job["status"], "reason:", rwr_job["rwr_reason"],
      "payment_required:", rwr_job["rwr_payment_required"], "khata_added:", rwr_job["khata_added"])
assert rwr_job["status"] == "RWR"
assert rwr_job["rwr_payment_required"] == True
assert rwr_job["khata_added"] == 300.0

# 8. Double RWR should not double-charge
r2 = c.post(f"/api/jobs/{job_id}/update_status/", json={"status":"RWR",
    "rwr_reason":"same","rwr_payment_required":True,"rwr_amount":300}, headers=H)
print("8. Second RWR call khata_added (expect 0):", j(r2)["khata_added"])
assert j(r2)["khata_added"] == 0

# 9. RWR without payment required - separate job
r = c.post("/api/jobs/", json={"customer_data":{"name":"Priya Singh","mobile":"9876500002"},
    "device_brand":"Oppo","device_model":"A5","problem":"Not charging","estimated_cost":500}, headers=H)
job2_id = j(r)["id"]
r = c.post(f"/api/jobs/{job2_id}/update_status/", json={"status":"RWR",
    "rwr_reason":"Customer ne khud repair karwa liya","rwr_payment_required":False}, headers=H)
print("9. RWR no-payment:", j(r)["rwr_payment_required"], "khata_added:", j(r)["khata_added"])
assert j(r)["rwr_payment_required"] == False
assert j(r)["khata_added"] == 0

# 10. Search jobs by custom_name
r = c.get("/api/jobs/?search=VIP", headers=H)
print("10. Search by custom_name count:", j(r)["count"])
assert j(r)["count"] == 1

# 11. Search by date (today)
import datetime
today = datetime.date.today().isoformat()
r = c.get(f"/api/jobs/?date={today}", headers=H)
print("11. Search by today's date count:", j(r)["count"])
assert j(r)["count"] == 2

# 12. Search by date range (future date should return 0)
future = (datetime.date.today() + datetime.timedelta(days=5)).isoformat()
r = c.get(f"/api/jobs/?date_from={future}", headers=H)
print("12. Future date_from count (expect 0):", j(r)["count"])
assert j(r)["count"] == 0

# 13. Invalid date format handled gracefully
r = c.get("/api/jobs/?date=notadate", headers=H)
print("13. Invalid date format:", r.status_code)
assert r.status_code == 400

# 14. Message template for job
r = c.get(f"/api/jobs/{job_id}/message/ready/", headers=H)
print("14. Ready message:", j(r))
assert "customer_mobile" in j(r)

r = c.get(f"/api/jobs/{job_id}/message/rwr/", headers=H)
print("14b. RWR message:", j(r)["message"])

# 15. Invalid message type
r = c.get(f"/api/jobs/{job_id}/message/invalid/", headers=H)
print("15. Invalid message type:", r.status_code)
assert r.status_code == 400

# 16. Delete media
media_id = j(c.get(f"/api/jobs/{job_id}/media/", headers=H))["results"][0]["id"]
r = c.post(f"/api/jobs/{job_id}/media/{media_id}/delete/", headers=H)
print("16. Delete media:", r.status_code)
r = c.get(f"/api/jobs/{job_id}/media/", headers=H)
assert len(j(r)["results"]) == 1

# 17. Full job details includes all new fields
r = c.get(f"/api/jobs/{job_id}/", headers=H)
data = j(r)
print("17. Full job dict keys present:", all(k in data for k in
      ["custom_name", "lock_type", "alternate_mobile", "rwr_reason", "rwr_payment_required", "media"]))

print("\nALL NEW-FEATURE TESTS PASS!")
