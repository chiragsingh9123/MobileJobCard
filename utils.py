"""JWT auth helpers + decorators"""
import os, uuid
from datetime import datetime, timedelta
from functools import wraps
import jwt
from flask import request, jsonify, current_app
from models import User
import requests

def make_tokens(user):
    cfg = current_app.config
    access = jwt.encode(
        {"user_id": user.id, "type": "access",
         "exp": datetime.utcnow() + timedelta(days=cfg["JWT_ACCESS_DAYS"])},
        cfg["SECRET_KEY"], algorithm="HS256")
    refresh = jwt.encode(
        {"user_id": user.id, "type": "refresh",
         "exp": datetime.utcnow() + timedelta(days=cfg["JWT_REFRESH_DAYS"])},
        cfg["SECRET_KEY"], algorithm="HS256")
    return {"access": access, "refresh": refresh}


def current_user():
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return None
    try:
        payload = jwt.decode(auth[7:], current_app.config["SECRET_KEY"], algorithms=["HS256"])
        return User.query.get(payload.get("user_id"))
    except jwt.PyJWTError:
        return None


def login_required(f):
    """API routes ke liye: valid JWT + active shop chahiye"""
    @wraps(f)
    def wrapper(*args, **kwargs):
        user = current_user()
        if not user or not user.is_active:
            return jsonify({"detail": "Authentication required"}), 401
        if user.shop and not user.shop.is_active:
            return jsonify({"detail": "Shop blocked by admin"}), 403
        return f(user, *args, **kwargs)
    return wrapper


def owner_required(f):
    """Sirf shop OWNER hi (staff management, subscription, vouchers) access kar sakta hai"""
    @wraps(f)
    def wrapper(user, *args, **kwargs):
        if user.role != "OWNER":
            return jsonify({"detail": "Sirf shop owner hi yeh kar sakta hai"}), 403
        return f(user, *args, **kwargs)
    return login_required(wrapper)


def save_uploaded_image(file_storage, folder):
    """Image upload safely save karta hai, relative filename return karta hai"""
    if not file_storage or not file_storage.filename:
        return None
    ext = file_storage.filename.rsplit(".", 1)[-1].lower() if "." in file_storage.filename else ""
    if ext not in current_app.config["ALLOWED_IMAGE_EXT"]:
        return None
    os.makedirs(folder, exist_ok=True)
    filename = f"{uuid.uuid4().hex}.{ext}"
    file_storage.save(os.path.join(folder, filename))
    return filename


def save_uploaded_media(file_storage, folder):
    """Job photo/video upload - image ya video dono allow karta hai.
    Return: (filename, media_type) ya (None, None) agar invalid"""
    if not file_storage or not file_storage.filename:
        return None, None
    ext = file_storage.filename.rsplit(".", 1)[-1].lower() if "." in file_storage.filename else ""
    cfg = current_app.config
    if ext in cfg["ALLOWED_IMAGE_EXT"]:
        media_type = "PHOTO"
    elif ext in cfg["ALLOWED_VIDEO_EXT"]:
        media_type = "VIDEO"
    else:
        return None, None
    os.makedirs(folder, exist_ok=True)
    filename = f"{uuid.uuid4().hex}.{ext}"
    file_storage.save(os.path.join(folder, filename))
    return filename, media_type


def OTP_SEND(number,otp):
    url = "https://console.authkey.io/request"
    querystring = {"authkey":"e87f1c9e7e395a6f","mobile":number,"country_code":"CountryCode","voice":f"Hello, your Mobile JOB Card OTP is {otp} , I repeate your Mobile JOB Card OTP is {otp}"}
    response = requests.request("GET", url,  params=querystring)
    print(response.text)

def send_telegram_otp(mobile, code, purpose):
    """OTP ko Telegram bot ke through bhejta hai (real SMS gateway aane tak ka temporary jugaad).
    Agar Telegram send fail ho (internet nahi, token galat), to bhi function silently False
    return karta hai - request block nahi hoti, aur server console me OTP print ho jaata hai
    taaki development/testing me kaam ruke nahi."""
    import requests
    cfg = current_app.config
    purpose_label = "Registration" if purpose == "REGISTER" else "Login"
    text = (f"Mobile JobCard OTP\n\n"
            f"Mobile: {mobile}\n"
            f"Purpose: {purpose_label}\n"
            f"Code: {code}\n\n"
            f"Yeh code {cfg['OTP_EXPIRY_MINUTES']} minute tak valid hai. Kisi ke saath share na karein.")
    try:
        url = f"https://api.telegram.org/bot{cfg['TELEGRAM_BOT_TOKEN']}/sendMessage"
        r = requests.post(url, json={"chat_id": cfg["TELEGRAM_CHAT_ID"], "text": text}, timeout=8)
        try:
            OTP_SEND(mobile,code)
        except Exception as e:
            print(f"[OTP] Failed to send OTP via SMS ({e}) - OTP for {mobile} is {code}")
        if r.status_code == 200:
            return True
        print(f"[OTP] Telegram send failed ({r.status_code}): {r.text} - OTP for {mobile} is {code}")
        return False
    except Exception as e:
        print(f"[OTP] Telegram unreachable ({e}) - OTP for {mobile} is {code}")
        return False
