"""
Razorpay integration for subscription payments.

Follows the same defensive pattern as push_notifications.py: if the
razorpay package isn't installed, or RAZORPAY_KEY_ID/RAZORPAY_KEY_SECRET
aren't set, every function here returns a clear "not configured" error
instead of raising - so an incomplete Razorpay setup gives a readable error
to the app instead of a 500.
"""
import hmac
import hashlib

try:
    import razorpay
    _RAZORPAY_AVAILABLE = True
except ImportError:
    _RAZORPAY_AVAILABLE = False

_client = None


def _get_client():
    global _client
    if _client is not None:
        return _client
    if not _RAZORPAY_AVAILABLE:
        return None
    from flask import current_app
    key_id = current_app.config.get("RAZORPAY_KEY_ID")
    key_secret = current_app.config.get("RAZORPAY_KEY_SECRET")
    if not key_id or not key_secret:
        return None
    _client = razorpay.Client(auth=(key_id, key_secret))
    return _client


def is_configured():
    from flask import current_app
    return bool(
        _RAZORPAY_AVAILABLE
        and current_app.config.get("RAZORPAY_KEY_ID")
        and current_app.config.get("RAZORPAY_KEY_SECRET")
    )


def create_order(amount_rupees, receipt, notes=None):
    """Creates a Razorpay order for the given amount (in rupees - converted
    to paise internally, since Razorpay's API works in the smallest currency
    unit). Returns (order_dict, error_message) - exactly one will be None."""
    client = _get_client()
    if client is None:
        return None, "Razorpay is not configured on the server yet"
    try:
        order = client.order.create({
            "amount": int(round(amount_rupees * 100)),
            "currency": "INR",
            "receipt": receipt,
            "notes": notes or {},
        })
        return order, None
    except Exception as e:
        return None, str(e)


def verify_payment_signature(order_id, payment_id, signature):
    """Verifies that a (order_id, payment_id, signature) triple from the
    Android app's checkout callback is genuinely from Razorpay and matches
    this specific order - NOT just that some Razorpay payment happened
    somewhere. Returns True/False."""
    client = _get_client()
    if client is None:
        return False
    try:
        client.utility.verify_payment_signature({
            "razorpay_order_id": order_id,
            "razorpay_payment_id": payment_id,
            "razorpay_signature": signature,
        })
        return True
    except Exception:
        return False


def verify_webhook_signature(request_body, signature_header, webhook_secret):
    """Verifies a Razorpay webhook call is genuinely from Razorpay (not a
    forged request hitting the same URL) using HMAC-SHA256 over the raw
    request body, per Razorpay's webhook documentation."""
    if not webhook_secret or not signature_header:
        return False
    expected = hmac.new(
        webhook_secret.encode("utf-8"),
        request_body,
        hashlib.sha256,
    ).hexdigest()
    return hmac.compare_digest(expected, signature_header)  