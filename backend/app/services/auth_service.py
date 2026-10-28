from functools import wraps
import logging
from time import sleep

import requests
from flask import current_app, g, make_response, request

# Share HTTPS connections, but keep headers and cookies isolated for every request.
_AUTH_ADAPTER = requests.adapters.HTTPAdapter(pool_connections=4, pool_maxsize=20)
LOGGER = logging.getLogger(__name__)


class AuthError(Exception):
    def __init__(self, message, status=401):
        super().__init__(message)
        self.status = status


def supabase_request(method, path, payload=None, token=None):
    url = current_app.config.get("SUPABASE_URL", "")
    key = current_app.config.get("SUPABASE_PUBLISHABLE_KEY", "")
    if not url.startswith("https://") or not key:
        raise AuthError("Account service is not configured. Please contact the app administrator.", 503)
    headers = {"apikey": key}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    session = requests.Session()
    session.mount("https://", _AUTH_ADAPTER)
    # The adapter lives for the process lifetime; closing this session would close its shared pool.
    for attempt in range(2 if method == "GET" else 1):
        try:
            response = session.request(method, f"{url}/auth/v1/{path}", headers=headers,
                                       json=payload, timeout=(5, 15))
            break
        except requests.RequestException as exc:
            LOGGER.warning("Supabase %s %s failed (attempt %s): %s", method, path, attempt + 1, exc)
            if method != "GET" or attempt == 1:
                raise AuthError("Account service is temporarily unavailable. Please try again.", 503) from None
            sleep(0.2)
    if response.status_code == 204:
        return {}
    try:
        data = response.json()
    except ValueError:
        raise AuthError("Account service returned an invalid response.", 502) from None
    if not isinstance(data, dict):
        raise AuthError("Account service returned an invalid response.", 502)
    if not response.ok:
        code = data.get("error_code") or data.get("code")
        provider_message = str(data.get("msg") or data.get("message") or data.get("error_description") or "")
        normalized_message = provider_message.lower()
        messages = {
            "invalid_credentials": "Email or password is incorrect.",
            "email_not_confirmed": "Please confirm your email before signing in.",
            "weak_password": "Choose a stronger password with at least 8 characters.",
            "user_already_exists": "An account already exists. Please sign in.",
            "email_exists": "An account already exists. Please sign in.",
            "over_email_send_rate_limit": "Too many emails requested. Please wait before trying again.",
            "email_address_not_authorized": "Email delivery is not configured for this address. Please contact the app administrator.",
            "email_provider_disabled": "Email sign-in is disabled in Supabase. Enable the Email provider in Authentication settings.",
            "signup_disabled": "Account creation is currently unavailable.",
        }
        status = 429 if response.status_code == 429 else (503 if response.status_code >= 500 else 400)
        if path == "user" or "grant_type=refresh_token" in path:
            status = 401 if response.status_code < 500 and response.status_code != 429 else status
        if code in messages:
            message = messages[code]
        elif "already registered" in normalized_message or "already exists" in normalized_message:
            message = "An account already exists. Use Sign in instead."
        elif "email not confirmed" in normalized_message or "email_not_confirmed" in normalized_message:
            message = "Please confirm your email before signing in."
        elif "invalid login credentials" in normalized_message or "invalid credentials" in normalized_message:
            message = "Email or password is incorrect."
        elif "rate limit" in normalized_message:
            message = "Too many attempts. Please wait before trying again."
        else:
            message = "Unable to authenticate. Check your details and try again."
        raise AuthError(message, status)
    return data


def public_user(user):
    metadata = user.get("user_metadata") or {}
    name = metadata.get("full_name")
    if not isinstance(name, str) or not name.strip():
        name = "Caller"
    return {"id": user["id"], "email": user.get("email", ""),
            "full_name": name.strip()[:100]}


def require_user(handler):
    @wraps(handler)
    def wrapped(*args, **kwargs):
        scheme, _, token = request.headers.get("Authorization", "").partition(" ")
        if scheme.lower() != "bearer" or not token.strip():
            raise AuthError("Please sign in to continue.")
        user = supabase_request("GET", "user", token=token.strip())
        if not user.get("id"):
            raise AuthError("Please sign in again.")
        g.auth_user = public_user(user)
        g.access_token = token.strip()
        response = make_response(handler(*args, **kwargs))
        response.headers["Cache-Control"] = "no-store"
        return response
    return wrapped
