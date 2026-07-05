"""Mobile JobCard - Flask Backend
Chalane ke liye: python run.py
API: http://localhost:8000/api/...
Admin: http://localhost:8000/admin/
"""
import os
from flask import Flask, jsonify, send_from_directory
from flask_cors import CORS
from config import Config
from extensions import db

app = Flask(__name__)
app.config.from_object(Config)
CORS(app)
db.init_app(app)

# Upload folders (payment screenshots + UPI QR code + job photos/videos)
os.makedirs(app.config["SCREENSHOT_FOLDER"], exist_ok=True)
os.makedirs(app.config["QR_FOLDER"], exist_ok=True)
os.makedirs(app.config["JOB_MEDIA_FOLDER"], exist_ok=True)

with app.app_context():
 import models # noqa
 db.create_all()

from api import ALL_BLUEPRINTS
from admin.routes import admin_bp
for bp in ALL_BLUEPRINTS:
 app.register_blueprint(bp)
app.register_blueprint(admin_bp)


@app.get("/uploads/screenshots/<filename>")
def serve_screenshot(filename):
 return send_from_directory(app.config["SCREENSHOT_FOLDER"], filename)


@app.get("/uploads/qr/<filename>")
def serve_qr(filename):
 return send_from_directory(app.config["QR_FOLDER"], filename)


@app.get("/uploads/job_media/<filename>")
def serve_job_media(filename):
 return send_from_directory(app.config["JOB_MEDIA_FOLDER"], filename)


@app.get("/")
def index():
 return jsonify({"app": "Mobile JobCard API", "status": "running",
 "admin_panel": "/admin/", "api_base": "/api/"})


if __name__ == "__main__":
 app.run(host="0.0.0.0", port=8000, debug=False)
