"""
Standalone push notification test - no Flask, no database, no app context.
Just: firebase-service-account.json + one FCM token -> one push.

SETUP (edit these two lines below, then run: python test_send_push.py):
"""
import sys

# ============================================================
# 1. Path to the service-account JSON you downloaded from
#    Firebase Console -> Project Settings -> Service Accounts
# ============================================================
SERVICE_ACCOUNT_PATH = "firebase-service-account.json"

# ============================================================
# 2. The FCM token from a real device (Logcat will show it when
#    the app calls MyFirebaseMessagingService.syncTokenWithServer,
#    or query it from your `users` table: SELECT fcm_token FROM users;)
# ============================================================
FCM_TOKEN = "eR2qRNR4QiuZ5Pqmjxbm50:APA91bHHOX8dYc6_68Ybmgw0QEdTRjchR0dH9LNbEZQotYrBh9tbbuVyGMU37ne4kqgq2_D3zHi_x3LiTv2Opw2MEm5Q9sJF249MSsqbLer4awJU8vP2XO0"


def main():
    import os
    if not os.path.exists(SERVICE_ACCOUNT_PATH):
        print(f"ERROR: '{SERVICE_ACCOUNT_PATH}' not found.")
        print("Set SERVICE_ACCOUNT_PATH at the top of this file to the full path")
        print("of the JSON key you downloaded from Firebase Console.")
        sys.exit(1)

    if FCM_TOKEN == "PASTE_YOUR_FCM_TOKEN_HERE" or not FCM_TOKEN.strip():
        print("ERROR: Set FCM_TOKEN at the top of this file to a real device token.")
        sys.exit(1)

    try:
        import firebase_admin
        from firebase_admin import credentials, messaging
    except ImportError:
        print("ERROR: firebase-admin isn't installed. Run:")
        print("  pip install firebase-admin")
        sys.exit(1)

    print(f"Loading credentials from: {SERVICE_ACCOUNT_PATH}")
    try:
        cred = credentials.Certificate(SERVICE_ACCOUNT_PATH)
        firebase_admin.initialize_app(cred)
    except Exception as e:
        print(f"ERROR: Could not load credentials from '{SERVICE_ACCOUNT_PATH}': {e}")
        print("This usually means the file is incomplete/corrupted, or isn't a")
        print("service-account key (double check it's from Firebase Console ->")
        print("Project Settings -> Service Accounts -> Generate new private key,")
        print("not the google-services.json used by the Android app).")
        sys.exit(1)
    print("Firebase initialized OK.\n")

    # Sent the same way the real app sends it: data-only, so it exercises
    # MyFirebaseMessagingService's own notification-building code on the
    # device (icon, color, "View Job" button, etc.) instead of relying on
    # a plain auto-generated system notification.
    payload = {
        "title": "Test Push",
        "body": "If you can see this with a custom icon and it vibrates, everything works.",
        "type": "ADMIN_BROADCAST",  # try "JOB_ASSIGNED" or "JOB_RECEIVED" too
    }

    message = messaging.Message(
        data=payload,
        token=FCM_TOKEN,
        android=messaging.AndroidConfig(priority="high"),
    )

    print("Sending push with payload:")
    for k, v in payload.items():
        print(f"  {k}: {v}")
    print()

    try:
        message_id = messaging.send(message)
        print(f"SUCCESS - Firebase accepted the message: {message_id}")
        print("Check the phone now. If nothing appears within a few seconds:")
        print("  - Make sure the app isn't force-stopped (Android kills FCM")
        print("    delivery to force-stopped apps until the user reopens it)")
        print("  - Make sure the FCM_TOKEN above is current (tokens can expire")
        print("    or rotate - grab a fresh one from Logcat or the database)")
        print("  - Check Settings > Apps > Mobile JobCard > Notifications is")
        print("    not blocked for the device")
    except Exception as e:
        print(f"FAILED: {e}")
        print()
        print("Common causes:")
        print("  - messaging/registration-token-not-registered: the token is")
        print("    stale/expired (app was reinstalled, cleared data, or the")
        print("    token you copied is old) - get a fresh one and try again")
        print("  - messaging/invalid-argument: the token string is malformed")
        print("    (make sure you copied the whole thing, no line breaks)")
        print("  - permission/credential errors: the service-account JSON is")
        print("    for the wrong Firebase project, or was revoked/regenerated")
        sys.exit(1)


if __name__ == "__main__":
    main()