"""Test: admin-broadcast notifications + forgot-password reset flow"""
from run import app
from extensions import db
from models import OTPCode, Notification, User

c = app.test_client()
def j(r): return r.get_json()
def get_otp(mobile, purpose):
    with app.app_context():
        row = OTPCode.latest_pending(mobile, purpose)
        return row.code if row else None

# Setup a regular user
c.post("/api/auth/send-otp/", json={"mobile":"9166600001","purpose":"REGISTER"})
otp = get_otp("9166600001", "REGISTER")
r = c.post("/api/auth/register/", json={"mobile":"9166600001","password":"orig123",
    "first_name":"NotifTest","shop_name":"Notif Test Shop","otp":otp})
tok = j(r)["tokens"]["access"]
H = {"Authorization": f"Bearer {tok}"}

# 1. No notifications yet
r = c.get("/api/notifications/check/", headers=H)
print("1. Initial check:", len(j(r)["notifications"]))
assert len(j(r)["notifications"]) == 0

# 2. Admin sends a notification via the admin panel
with app.app_context():
    admin = User.query.filter_by(mobile="9199900000").first()
    if not admin:
        admin = User(mobile="9199900000", first_name="SuperAdmin", role="ADMIN")
        admin.set_password("admin123")
        db.session.add(admin)
        db.session.commit()
c.post("/admin/login/", data={"mobile":"9199900000","password":"admin123"}, follow_redirects=True)
r = c.post("/admin/notifications/", data={"title":"Maintenance Notice",
    "message":"The app will be under maintenance tonight from 11 PM to 1 AM."}, follow_redirects=True)
print("2. Admin sent notification:", r.status_code)
assert r.status_code == 200

# 3. User now sees the new notification
r = c.get("/api/notifications/check/", headers=H)
notifs = j(r)["notifications"]
print("3. User check after broadcast:", len(notifs), "-", notifs[0]["title"] if notifs else None)
assert len(notifs) == 1
assert notifs[0]["title"] == "Maintenance Notice"

# 4. Checking again should show nothing new (already marked as seen)
r = c.get("/api/notifications/check/", headers=H)
print("4. Second check (should be empty):", len(j(r)["notifications"]))
assert len(j(r)["notifications"]) == 0

# 5. Admin sends a second notification
c.post("/admin/notifications/", data={"title":"New Feature",
    "message":"You can now track Aadhaar and Bill documentation for every job."}, follow_redirects=True)
r = c.get("/api/notifications/check/", headers=H)
print("5. Second broadcast check:", len(j(r)["notifications"]))
assert len(j(r)["notifications"]) == 1

# 6. Admin retracts a notification - test with a fresh user who hasn't seen anything
c.post("/api/auth/send-otp/", json={"mobile":"9166600002","purpose":"REGISTER"})
otp2 = get_otp("9166600002", "REGISTER")
r = c.post("/api/auth/register/", json={"mobile":"9166600002","password":"p","first_name":"Fresh",
    "shop_name":"Fresh Shop","otp":otp2})
tok2 = j(r)["tokens"]["access"]
H2 = {"Authorization": f"Bearer {tok2}"}
r = c.get("/api/notifications/check/", headers=H2)
print("6. Fresh user sees both active notifications:", len(j(r)["notifications"]))
assert len(j(r)["notifications"]) == 2

with app.app_context():
    first_notif = Notification.query.filter_by(title="Maintenance Notice").first()
    notif_id = first_notif.id
c.post(f"/admin/notifications/{notif_id}/retract/", follow_redirects=True)

c.post("/api/auth/send-otp/", json={"mobile":"9166600003","purpose":"REGISTER"})
otp3 = get_otp("9166600003", "REGISTER")
r = c.post("/api/auth/register/", json={"mobile":"9166600003","password":"p","first_name":"Fresher",
    "shop_name":"Fresher Shop","otp":otp3})
tok3 = j(r)["tokens"]["access"]
H3 = {"Authorization": f"Bearer {tok3}"}
r = c.get("/api/notifications/check/", headers=H3)
print("7. After retracting one, newest user sees only 1 active:", len(j(r)["notifications"]))
assert len(j(r)["notifications"]) == 1

print("\nNOTIFICATION TESTS PASS!")

# ============ FORGOT PASSWORD TESTS ============

# 8. Request reset OTP for existing user
r = c.post("/api/auth/send-otp/", json={"mobile":"9166600001","purpose":"RESET_PASSWORD"})
print("8. Reset OTP request:", r.status_code)
assert r.status_code == 200
reset_otp = get_otp("9166600001", "RESET_PASSWORD")

# 9. Reset with wrong OTP should fail
r = c.post("/api/auth/reset-password/", json={"mobile":"9166600001","otp":"000000",
    "new_password":"newpass123"})
print("9. Wrong OTP reset attempt:", r.status_code)
assert r.status_code == 400

# 10. Reset with correct OTP should succeed
r = c.post("/api/auth/reset-password/", json={"mobile":"9166600001","otp":reset_otp,
    "new_password":"newpass123"})
print("10. Correct OTP reset:", r.status_code, j(r).get("message"))
assert r.status_code == 200

# 11. Old password should no longer work, new password should work
c.post("/api/auth/send-otp/", json={"mobile":"9166600001","purpose":"LOGIN"})
login_otp = get_otp("9166600001", "LOGIN")
r = c.post("/api/auth/login/", json={"mobile":"9166600001","password":"orig123","otp":login_otp})
print("11a. Old password login attempt:", r.status_code)
assert r.status_code == 401

c.post("/api/auth/send-otp/", json={"mobile":"9166600001","purpose":"LOGIN"})
login_otp2 = get_otp("9166600001", "LOGIN")
r = c.post("/api/auth/login/", json={"mobile":"9166600001","password":"newpass123","otp":login_otp2})
print("11b. New password login attempt:", r.status_code)
assert r.status_code == 200

# 12. Reset for unregistered mobile should 404
r = c.post("/api/auth/reset-password/", json={"mobile":"9100000099","otp":"123456",
    "new_password":"whatever123"})
print("12. Unregistered mobile reset:", r.status_code)
assert r.status_code == 404

print("\nFORGOT-PASSWORD TESTS PASS!")
