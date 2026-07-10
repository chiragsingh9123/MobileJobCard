"""
Push notifications via Firebase Cloud Messaging (FCM).

This module is intentionally defensive: if firebase-admin isn't installed, or
the service-account credentials file isn't present yet, every function here
becomes a no-op that logs instead of raising - so a missing/incomplete
Firebase setup never breaks the underlying API action (creating a job,
assigning staff, etc). Once FIREBASE_CREDENTIALS_PATH points at a real
service-account JSON file, pushes start sending for real with no other code
changes needed.
"""
import os

try:
    import firebase_admin
    from firebase_admin import credentials, messaging
    _FIREBASE_AVAILABLE = True
except ImportError:
    _FIREBASE_AVAILABLE = False

_initialized = False



def _init_firebase():
    global _initialized
    if _initialized:
        return True
    if not _FIREBASE_AVAILABLE:
        return False
    cred_path = os.environ.get("FIREBASE_CREDENTIALS_PATH", "/root/MobileJobCard/firebase-service-account.json")
    if not os.path.exists(cred_path):
        return False
    try:
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred)
        _initialized = True
        return True
    except Exception as e:
        print(f"[PUSH] Firebase init failed: {e}")
        return False


def send_push(token, title, body, data=None):
    """Sends a push notification to a single device token. Returns True/False.

    Sent as a data-only message (no top-level "notification" field) so the
    Android app's own MyFirebaseMessagingService always builds the
    notification itself - with its custom icon/color/action button - instead
    of Android silently auto-displaying a plain default notification when
    the app is backgrounded (which is what happens if a "notification" field
    is included, and it bypasses the app's styling entirely)."""
    if not token:
        return False
    if not _init_firebase():
        print(f"[PUSH] (not configured) would send to token ...{token[-8:]}: {title} - {body}")
        return False
    try:
        payload = {"title": title, "body": body}
        payload.update({k: str(v) for k, v in (data or {}).items()})
        message = messaging.Message(
            data=payload,
            token=token,
            android=messaging.AndroidConfig(priority="high"),
        )
        messaging.send(message)
        return True
    except Exception as e:
        print(f"[PUSH] Failed to send to token ...{token[-8:]}: {e}")
        return False


def send_push_multicast(tokens, title, body, data=None):
    """Sends the same push to many device tokens at once (admin broadcasts).
    Returns the number of devices it was successfully delivered to.
    Also sent data-only - see send_push() for why."""
    tokens = [t for t in tokens if t]
    if not tokens:
        return 0
    if not _init_firebase():
        print(f"[PUSH] (not configured) would broadcast to {len(tokens)} device(s): {title}")
        return 0
    try:
        payload = {"title": title, "body": body}
        payload.update({k: str(v) for k, v in (data or {}).items()})
        message = messaging.MulticastMessage(
            data=payload,
            tokens=tokens,
            android=messaging.AndroidConfig(priority="high"),
        )
        response = messaging.send_each_for_multicast(message)
        return response.success_count
    except Exception as e:
        print(f"[PUSH] Multicast failed: {e}")
        return 0