import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))


class Config:
    SECRET_KEY = os.environ.get("SECRET_KEY", "change-this-secret-key-in-production")

    # ---------------- DATABASE ----------------
    # Production default is MySQL/MariaDB for proper indexing, reliable concurrent
    # writes, and standard replication/backup tooling. Configure it with these
    # environment variables:
    #   DB_HOST, DB_PORT (default 3306), DB_NAME, DB_USER, DB_PASSWORD
    # To fall back to SQLite for quick local testing only, set DB_ENGINE=sqlite.
    DB_ENGINE = os.environ.get("DB_ENGINE", "mysql")

    if DB_ENGINE == "sqlite":
        SQLALCHEMY_DATABASE_URI = "sqlite:///" + os.path.join(BASE_DIR, "revenue_account.db")
        SQLALCHEMY_ENGINE_OPTIONS = {}
    else:
        DB_HOST = os.environ.get("DB_HOST", "localhost")
        DB_PORT = os.environ.get("DB_PORT", "3306")
        DB_NAME = os.environ.get("DB_NAME", "revenue_account")
        DB_USER = os.environ.get("DB_USER", "root")
        DB_PASSWORD = os.environ.get("DB_PASSWORD", "root")
        SQLALCHEMY_DATABASE_URI = (
            f"mysql+pymysql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
            f"?charset=utf8mb4"
        )
        # Tuned for production: pool_recycle avoids MySQL's "server has gone away"
        # error on connections that sat idle past the server's wait_timeout;
        # pool_pre_ping verifies a connection is alive before handing it out.
        SQLALCHEMY_ENGINE_OPTIONS = {
            "pool_recycle": 280,
            "pool_pre_ping": True,
            "pool_size": 10,
            "max_overflow": 20,
        }

    SQLALCHEMY_TRACK_MODIFICATIONS = False
    JWT_ACCESS_DAYS = 7  # access token validity
    JWT_REFRESH_DAYS = 30  # refresh token validity
    TRIAL_DAYS = 7  # free trial length on registration

    # File uploads (UPI payment screenshots + QR code + job photos/videos)
    UPLOAD_FOLDER = os.path.join(BASE_DIR, "uploads")
    SCREENSHOT_FOLDER = os.path.join(UPLOAD_FOLDER, "screenshots")
    QR_FOLDER = os.path.join(UPLOAD_FOLDER, "qr")
    JOB_MEDIA_FOLDER = os.path.join(UPLOAD_FOLDER, "job_media")
    MAX_CONTENT_LENGTH = 60 * 1024 * 1024  # 60MB max upload (raised to allow video)
    ALLOWED_IMAGE_EXT = {"png", "jpg", "jpeg", "webp"}
    ALLOWED_VIDEO_EXT = {"mp4", "3gp", "mkv", "webm"}

    # OTP via Telegram bot (temporary - a dedicated SMS gateway will replace this).
    # All OTPs currently go to a single Telegram chat (the developer/owner's own
    # bot) so verification can be tested end-to-end before an SMS provider is wired up.
    TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "8154256318:AAHXknJtIMSCUqLQawtckg9jaIqRD16oQIA")
    TELEGRAM_CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "7474379284")
    OTP_EXPIRY_MINUTES = 5
    OTP_MAX_ATTEMPTS = 5
    OTP_RESEND_COOLDOWN_SECONDS = 45
