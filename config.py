import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

class Config:
 SECRET_KEY = os.environ.get("SECRET_KEY", "")
 # SQLite database (file: revenue_account.db)
 SQLALCHEMY_DATABASE_URI = "sqlite:///" + os.path.join(BASE_DIR, "revenue_account.db")
 SQLALCHEMY_TRACK_MODIFICATIONS = False
 JWT_ACCESS_DAYS = 7 # access token validity
 JWT_REFRESH_DAYS = 30 # refresh token validity
 TRIAL_DAYS = 7 # register par free trial

 # File uploads (UPI payment screenshots + QR code + job photos/videos)
 UPLOAD_FOLDER = os.path.join(BASE_DIR, "uploads")
 SCREENSHOT_FOLDER = os.path.join(UPLOAD_FOLDER, "screenshots")
 QR_FOLDER = os.path.join(UPLOAD_FOLDER, "qr")
 JOB_MEDIA_FOLDER = os.path.join(UPLOAD_FOLDER, "job_media")
 MAX_CONTENT_LENGTH = 60 * 1024 * 1024 # 60MB max upload (video ke liye badhaya)
 ALLOWED_IMAGE_EXT = {"png", "jpg", "jpeg", "webp"}
 ALLOWED_VIDEO_EXT = {"mp4", "3gp", "mkv", "webm"}

 # OTP via Telegram bot (temporary — real SMS gateway baad me lagayenge).
 # Filhaal SAARE OTP is ek Telegram chat par jaate hain (developer/owner ka apna bot),
 # taaki abhi SMS provider ke bina bhi OTP verification test kiya ja sake.
 TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
 TELEGRAM_CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "")
 OTP_EXPIRY_MINUTES = 5
 OTP_MAX_ATTEMPTS = 5
 OTP_RESEND_COOLDOWN_SECONDS = 45
