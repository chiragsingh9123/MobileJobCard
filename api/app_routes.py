"""App version check - force-update control from the admin panel"""
from flask import Blueprint, request, jsonify
from models import PlatformSettings

app_bp = Blueprint("app_meta", __name__, url_prefix="/api/app")


@app_bp.get("/version-check/")
def version_check():
    current_version = int(request.args.get("version_code", 0))
    settings = PlatformSettings.get()
    update_required = (settings.force_update_enabled
                        and current_version < settings.min_version_code)
    return jsonify({
        "update_required": update_required,
        "message": settings.update_message,
        "play_store_url": settings.play_store_url,
        "min_version_code": settings.min_version_code
    })
