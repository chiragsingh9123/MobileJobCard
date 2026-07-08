"""Test: Forgot Password flow (send-otp purpose=RESET_PASSWORD + reset-password)"""
from run import app
from models import OTPCode

c = app.test_client()
def j(r): return r.get_json()
def get_otp(mobile, purpose):
    with app.app_context():
        row = OTPCode.latest_pending(mobile, purpose)
        return row.code if row else None

# Setup: register a user first
c.post("/api/auth/send-otp/", json={"mobile":"9177700001","purpose":"REGISTER"})
otp = get_otp("9177700001", "REGISTER")
r = c.post("/api/auth/register/", json={"mobile":"9177700001","password":"oldpass123",
    "first_name":"ForgotTest","shop_name":"Forgot Test Shop","otp":otp})
assert r.status_code == 201
print("1. User registered")

# 2. Request OTP for non-registered mobile should fail
r = c.post("/api/auth/send-otp/", json={"mobile":"9177799999","purpose":"RESET_PASSWORD"})
print("2. OTP for unregistered mobile:", r.status_code)
assert r.status_code == 400

# 3. Request OTP for RESET_PASSWORD on registered mobile - should succeed
r = c.post("/api/auth/send-otp/", json={"mobile":"9177700001","purpose":"RESET_PASSWORD"})
print("3. RESET_PASSWORD OTP request:", r.status_code)
assert r.status_code == 200

# 4. Try reset with wrong OTP
r = c.post("/api/auth/reset-password/", json={"mobile":"9177700001","otp":"000000",
    "new_password":"newpass456"})
print("4. Reset with wrong OTP:", r.status_code)
assert r.status_code == 400

# 5. Reset with correct OTP
otp = get_otp("9177700001", "RESET_PASSWORD")
r = c.post("/api/auth/reset-password/", json={"mobile":"9177700001","otp":otp,
    "new_password":"newpass456"})
print("5. Reset with correct OTP:", r.status_code, j(r))
assert r.status_code == 200

# 6. Login with NEW password + fresh LOGIN otp should succeed
c.post("/api/auth/send-otp/", json={"mobile":"9177700001","purpose":"LOGIN"})
login_otp = get_otp("9177700001", "LOGIN")
r = c.post("/api/auth/login/", json={"mobile":"9177700001","password":"newpass456","otp":login_otp})
print("6. Login with new password:", r.status_code)
assert r.status_code == 200

# 7. Login with OLD password should now fail
c.post("/api/auth/send-otp/", json={"mobile":"9177700001","purpose":"LOGIN"})
login_otp2 = get_otp("9177700001", "LOGIN")
r = c.post("/api/auth/login/", json={"mobile":"9177700001","password":"oldpass123","otp":login_otp2})
print("7. Login with OLD password (should fail):", r.status_code)
assert r.status_code == 401

# 8. Reset with too-short new password should fail validation
c.post("/api/auth/send-otp/", json={"mobile":"9177700001","purpose":"RESET_PASSWORD"})
otp3 = get_otp("9177700001", "RESET_PASSWORD")
r = c.post("/api/auth/reset-password/", json={"mobile":"9177700001","otp":otp3,"new_password":"ab"})
print("8. Too-short new password:", r.status_code)
assert r.status_code == 400

print("\nALL FORGOT-PASSWORD TESTS PASS!")
