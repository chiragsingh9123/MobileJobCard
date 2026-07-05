"""Edge-case tests - backend kabhi 500 crash na de (OTP flow ke saath updated)"""
import json
from run import app
from models import OTPCode
c = app.test_client()

def j(r):
    try: return r.get_json()
    except Exception: return None

def get_otp(mobile, purpose):
    with app.app_context():
        row = OTPCode.latest_pending(mobile, purpose)
        return row.code if row else None

print("=== Edge case tests ===")

# 1. Empty register
r = c.post("/api/auth/register/", json={})
print("1. Empty register:", r.status_code); assert r.status_code == 400

# 2. Register without OTP
c.post("/api/auth/send-otp/", json={"mobile":"9999999999","purpose":"REGISTER"})
r = c.post("/api/auth/register/", json={"mobile":"9999999999","password":"test123",
    "first_name":"Test","shop_name":"Test Shop"})
print("2. Register no OTP field:", r.status_code); assert r.status_code == 400

# 3. Register with wrong OTP
r = c.post("/api/auth/register/", json={"mobile":"9999999999","password":"test123",
    "first_name":"Test","shop_name":"Test Shop","otp":"999999"})
print("3. Register wrong OTP:", r.status_code); assert r.status_code == 400

# 4. Register with correct OTP
otp = get_otp("9999999999", "REGISTER")
r = c.post("/api/auth/register/", json={"mobile":"9999999999","password":"test123",
    "first_name":"Test","shop_name":"Test Shop","otp":otp})
print("4. Register correct OTP:", r.status_code); assert r.status_code == 201

# 5. OTP reuse blocked
r = c.post("/api/auth/send-otp/", json={"mobile":"9888888888","purpose":"REGISTER"})
otp2 = get_otp("9888888888", "REGISTER")
c.post("/api/auth/register/", json={"mobile":"9888888888","password":"t","first_name":"A",
    "shop_name":"B","otp":otp2})
r2 = c.post("/api/auth/send-otp/", json={"mobile":"9888888888","purpose":"REGISTER"})
print("5. Resend OTP on already-registered mobile:", r2.status_code); assert r2.status_code == 400

# 6. OTP resend cooldown
r = c.post("/api/auth/send-otp/", json={"mobile":"9777777777","purpose":"REGISTER"})
r2 = c.post("/api/auth/send-otp/", json={"mobile":"9777777777","purpose":"REGISTER"})
print("6. Resend cooldown blocks spam:", r2.status_code); assert r2.status_code == 429

# 7. Login wrong password (before OTP even matters)
r = c.post("/api/auth/login/", json={"mobile":"9999999999","password":"wrong"})
print("7. Wrong password:", r.status_code); assert r.status_code == 401

# 8. No token access
r = c.get("/api/auth/me/")
print("8. No token:", r.status_code); assert r.status_code == 401

# 9. Garbage token
r = c.get("/api/auth/me/", headers={"Authorization": "Bearer garbage.token.here"})
print("9. Garbage token:", r.status_code); assert r.status_code == 401

# get valid token
c.post("/api/auth/send-otp/", json={"mobile":"9999999999","purpose":"LOGIN"})
otp = get_otp("9999999999", "LOGIN")
r = c.post("/api/auth/login/", json={"mobile":"9999999999","password":"test123","otp":otp})
tok = j(r)["tokens"]["access"]
H = {"Authorization": f"Bearer {tok}"}

# 10. Job no customer
r = c.post("/api/jobs/", json={"device_brand":"Test"}, headers=H)
print("10. Job no customer:", r.status_code); assert r.status_code == 400

# 11. Staff creation by non-owner blocked
c.post("/api/staff/", json={"mobile":"9111222333","password":"p","first_name":"S"}, headers=H)
r = c.post("/api/auth/send-otp/", json={"mobile":"9111222333","purpose":"LOGIN"})
otp = get_otp("9111222333", "LOGIN")
r = c.post("/api/auth/login/", json={"mobile":"9111222333","password":"p","otp":otp})
staff_tok = j(r)["tokens"]["access"]
SH = {"Authorization": f"Bearer {staff_tok}"}
r = c.post("/api/staff/", json={"mobile":"9000000001","password":"p","first_name":"X"}, headers=SH)
print("11. Non-owner create staff blocked:", r.status_code); assert r.status_code == 403

# 12. Update nonexistent job
r = c.post("/api/jobs/99999/update_status/", json={"status":"DELIVERED"}, headers=H)
print("12. Update nonexistent job:", r.status_code); assert r.status_code == 404

# 13. Invalid voucher
r = c.post("/api/auth/redeem-voucher/", json={"code":"RA-NOTREAL123"}, headers=H)
print("13. Invalid voucher:", r.status_code); assert r.status_code == 404

# 14. Payment no amount
r = c.post("/api/payments/payments/", json={}, headers=H)
print("14. Payment no amount:", r.status_code); assert r.status_code == 400

# 15. Negative amount
r = c.post("/api/payments/payments/", json={"amount": -50}, headers=H)
print("15. Negative amount:", r.status_code); assert r.status_code == 400

# 16. Product minimal fields
r = c.post("/api/inventory/products/", json={"name":"Test Part"}, headers=H)
print("16. Product minimal:", r.status_code); assert r.status_code == 201

# 17. Change password wrong old
r = c.post("/api/shop/change-password/", json={"old_password":"wrong","new_password":"new1234"}, headers=H)
print("17. Change password wrong old:", r.status_code); assert r.status_code == 400

# 18. Change password success
r = c.post("/api/shop/change-password/", json={"old_password":"test123","new_password":"new1234"}, headers=H)
print("18. Change password success:", r.status_code); assert r.status_code == 200

# 19. Blocked shop cannot access API
from extensions import db
from models import User
with app.app_context():
    u = User.query.filter_by(mobile="9999999999").first()
    u.shop.is_active = False
    db.session.commit()
r = c.get("/api/reports/dashboard/", headers=H)
print("19. Blocked shop:", r.status_code); assert r.status_code == 403
with app.app_context():
    u = User.query.filter_by(mobile="9999999999").first()
    u.shop.is_active = True
    db.session.commit()

# 20. Submit payment with invalid plan
r = c.post("/api/subscription/submit-payment/",
    data={"plan_id": "99999", "utr_number": "ABC"}, content_type="multipart/form-data", headers=H)
print("20. Invalid plan payment request:", r.status_code); assert r.status_code == 400

print("\nALL EDGE CASES PASS - koi 500 error nahi, OTP/staff/khata sab safe!")
