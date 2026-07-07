"""Test: Aadhaar/Bill documentation feature (categorized media) + force-update control"""
import io
from run import app
from extensions import db
from models import OTPCode, PlatformSettings

c = app.test_client()
def j(r): return r.get_json()
def get_otp(mobile, purpose):
    with app.app_context():
        row = OTPCode.latest_pending(mobile, purpose)
        return row.code if row else None

# Setup
c.post("/api/auth/send-otp/", json={"mobile":"9155500001","purpose":"REGISTER"})
otp = get_otp("9155500001", "REGISTER")
r = c.post("/api/auth/register/", json={"mobile":"9155500001","password":"t","first_name":"Owner",
    "shop_name":"Doc Test Shop","otp":otp})
tok = j(r)["tokens"]["access"]
H = {"Authorization": f"Bearer {tok}"}

# 1. Create job with aadhaar_number and bill_number
r = c.post("/api/jobs/", json={"customer_data":{"name":"Test Customer","mobile":"9800000002"},
    "device_model":"Samsung A15","problem":"Screen issue","estimated_cost":500,
    "aadhaar_number":"123456789012","bill_number":"BILL-2026-001"}, headers=H)
job = j(r)
job_id = job["id"]
print("1. Job created with aadhaar_number:", job["aadhaar_number"], "bill_number:", job["bill_number"])
assert job["aadhaar_number"] == "123456789012"
assert job["bill_number"] == "BILL-2026-001"

# 2. Upload categorized media - Aadhaar front
r = c.post(f"/api/jobs/{job_id}/media/",
    data={"category": "AADHAAR_FRONT", "file": (io.BytesIO(b"fake"), "aadhaar_front.jpg")},
    content_type="multipart/form-data", headers=H)
print("2. Aadhaar front upload:", r.status_code, j(r)["category"])
assert j(r)["category"] == "AADHAAR_FRONT"

# 3. Upload Aadhaar back
c.post(f"/api/jobs/{job_id}/media/",
    data={"category": "AADHAAR_BACK", "file": (io.BytesIO(b"fake"), "aadhaar_back.jpg")},
    content_type="multipart/form-data", headers=H)

# 4. Upload Aadhaar statement video
c.post(f"/api/jobs/{job_id}/media/",
    data={"category": "AADHAAR_STATEMENT", "file": (io.BytesIO(b"fake"), "statement.mp4")},
    content_type="multipart/form-data", headers=H)

# 5. Upload Bill photo
c.post(f"/api/jobs/{job_id}/media/",
    data={"category": "BILL_PHOTO", "file": (io.BytesIO(b"fake"), "bill.jpg")},
    content_type="multipart/form-data", headers=H)

# 6. Upload Box photo
c.post(f"/api/jobs/{job_id}/media/",
    data={"category": "BOX_PHOTO", "file": (io.BytesIO(b"fake"), "box.jpg")},
    content_type="multipart/form-data", headers=H)

# 7. Upload a GENERAL photo too (regular Photos & Videos section)
c.post(f"/api/jobs/{job_id}/media/",
    data={"file": (io.BytesIO(b"fake"), "general.jpg")},
    content_type="multipart/form-data", headers=H)

# 8. List all media - should be 6 total
r = c.get(f"/api/jobs/{job_id}/media/", headers=H)
print("8. Total media count:", len(j(r)["results"]))
assert len(j(r)["results"]) == 6

# 9. Filter by category - only Aadhaar-related
r = c.get(f"/api/jobs/{job_id}/media/?category=AADHAAR_FRONT", headers=H)
print("9. AADHAAR_FRONT filtered count:", len(j(r)["results"]))
assert len(j(r)["results"]) == 1

# 10. Filter by GENERAL category
r = c.get(f"/api/jobs/{job_id}/media/?category=GENERAL", headers=H)
print("10. GENERAL filtered count:", len(j(r)["results"]))
assert len(j(r)["results"]) == 1

# 11. Edit aadhaar_number / bill_number later via update endpoint
r = c.post(f"/api/jobs/{job_id}/update/", json={"aadhaar_number": "999988887777"}, headers=H)
print("11. Updated aadhaar_number:", j(r)["aadhaar_number"])
assert j(r)["aadhaar_number"] == "999988887777"

print("\nAADHAAR/BILL DOCUMENTATION TESTS PASS!")

# ============ FORCE UPDATE TESTS ============

# 12. Version check with force-update disabled (default) - should not require update
r = c.get("/api/app/version-check/?version_code=1")
print("12. Version check (disabled):", j(r))
assert j(r)["update_required"] == False

# 13. Enable force-update via direct DB manipulation (simulating admin panel action)
with app.app_context():
    settings = PlatformSettings.get()
    settings.force_update_enabled = True
    settings.min_version_code = 5
    settings.update_message = "Please update to the latest version."
    settings.play_store_url = "https://play.google.com/store/apps/details?id=com.revenueaccount.app"
    db.session.commit()

# 14. Old version should now require update
r = c.get("/api/app/version-check/?version_code=1")
print("14. Version check (old version, force-update ON):", j(r))
assert j(r)["update_required"] == True
assert j(r)["message"] == "Please update to the latest version."

# 15. New version should NOT require update
r = c.get("/api/app/version-check/?version_code=5")
print("15. Version check (current version):", j(r))
assert j(r)["update_required"] == False

# 16. Newer version than minimum should also be fine
r = c.get("/api/app/version-check/?version_code=10")
print("16. Version check (newer version):", j(r))
assert j(r)["update_required"] == False

# 17. Admin panel toggle route works
with app.app_context():
    from models import User
    if not User.query.filter_by(mobile="9999900000").first():
        admin = User(mobile="9999900000", first_name="SuperAdmin", role="ADMIN")
        admin.set_password("admin123")
        db.session.add(admin)
        db.session.commit()
c.post("/admin/login/", data={"mobile":"9999900000","password":"admin123"}, follow_redirects=True)
r = c.get("/admin/app-settings/")
print("17. Admin app-settings page:", r.status_code)
assert r.status_code == 200

r = c.post("/admin/app-settings/", data={"min_version_code": "8",
    "update_message": "New message", "play_store_url": "https://example.com"}, follow_redirects=True)
print("18. Admin app-settings save:", r.status_code)
assert r.status_code == 200
with app.app_context():
    settings = PlatformSettings.get()
    print("19. min_version_code after save:", settings.min_version_code)
    assert settings.min_version_code == 8
    assert settings.force_update_enabled == False  # checkbox not sent = unchecked

print("\nFORCE-UPDATE TESTS PASS!")
