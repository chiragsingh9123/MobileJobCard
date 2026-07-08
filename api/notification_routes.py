"""Admin-broadcast notifications. A single admin action creates one Notification
row that every user will see the next time they open the app - no per-user
targeting or fan-out needed, since read-state is tracked via a single integer
(User.last_seen_notification_id) rather than a join table."""
from flask import Blueprint, jsonify
from extensions import db
from models import Notification
from utils import login_required

notification_bp = Blueprint("notifications", __name__, url_prefix="/api/notifications")


@notification_bp.get("/check/")
@login_required
def check_notifications(user):
    """Called when the app opens. Returns any active notifications newer than
    what this user has already seen, then marks the latest one as seen."""
    latest = (Notification.query.filter_by(is_active=True)
              .order_by(Notification.id.desc()).first())
    unseen = (Notification.query
              .filter(Notification.is_active.is_(True),
                      Notification.id > (user.last_seen_notification_id or 0))
              .order_by(Notification.id.asc()).all())
    if latest and latest.id > (user.last_seen_notification_id or 0):
        user.last_seen_notification_id = latest.id
        db.session.commit()
    return jsonify({"notifications": [n.to_dict() for n in unseen]})
