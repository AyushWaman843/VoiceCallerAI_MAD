import re

from flask import Blueprint, g, jsonify, request

from ..database import get_db
from ..services.plans import account_plan, catalog
from ..services.auth_service import AuthError, public_user, require_user, supabase_request


auth_bp = Blueprint("auth", __name__, url_prefix="/auth")


@auth_bp.app_errorhandler(AuthError)
def auth_error(error):
    return jsonify(status="error", message=str(error)), error.status


@auth_bp.after_request
def prevent_auth_caching(response):
    response.headers["Cache-Control"] = "no-store"
    return response


def credentials(signup=False):
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise AuthError("Provide account details as a JSON object.", 400)
    email = data.get("email")
    password = data.get("password")
    name = data.get("full_name", "")
    if not isinstance(email, str) or len(email) > 254 or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email.strip()):
        raise AuthError("Enter a valid email address.", 400)
    if not isinstance(password, str) or not password or len(password) > 256:
        raise AuthError("Enter a password of at most 256 characters.", 400)
    if signup and (len(password) < 8 or not isinstance(name, str) or not 1 <= len(name.strip()) <= 100):
        raise AuthError("Enter your name and a password of at least 8 characters.", 400)
    result = {"email": email.strip().lower(), "password": password}
    if signup:
        result["data"] = {"full_name": name.strip()}
    return result


def session_response(data):
    if not data.get("access_token"):
        # Confirm-email projects do not create a session until the email link is used.
        return jsonify(status="confirmation_required",
                       message="Check your email to confirm your account, then come back and sign in.")
    return jsonify(status="authenticated", access_token=data["access_token"],
                   refresh_token=data["refresh_token"], expires_in=data.get("expires_in", 3600),
                   user=public_user(data["user"]))


@auth_bp.post("signup")
def signup():
    return session_response(supabase_request("POST", "signup", credentials(signup=True)))


@auth_bp.post("login")
def login():
    return session_response(supabase_request("POST", "token?grant_type=password", credentials()))


@auth_bp.post("refresh")
def refresh():
    data = request.get_json(silent=True)
    token = data.get("refresh_token") if isinstance(data, dict) else None
    if not isinstance(token, str) or not token.strip() or len(token) > 4096:
        raise AuthError("Please sign in again.")
    return session_response(supabase_request("POST", "token?grant_type=refresh_token", {"refresh_token": token}))


@auth_bp.get("me")
@require_user
def me():
    return jsonify(status="ok", user=g.auth_user)


@auth_bp.patch("profile")
@require_user
def update_profile():
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise AuthError("Provide profile details.", 400)
    name = data.get("full_name")
    organization = data.get("organization_name")
    timezone = data.get("timezone")
    if name is not None and (not isinstance(name, str) or not 1 <= len(name.strip()) <= 100):
        raise AuthError("Enter a display name of up to 100 characters.", 400)
    if organization is not None and (not isinstance(organization, str) or len(organization.strip()) > 120):
        raise AuthError("Enter an organization name of up to 120 characters.", 400)
    if timezone is not None:
        from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
        try:
            ZoneInfo(timezone)
        except (TypeError, ZoneInfoNotFoundError):
            raise AuthError("Choose a valid timezone.", 400) from None
    with get_db() as db:
        plan = account_plan(db, g.auth_user["id"])
        if (organization is not None or timezone is not None) and plan is None:
            raise AuthError("Choose a plan first.", 403)
        if organization is not None:
            if catalog()[plan.plan_id]["edition"] != "business":
                raise AuthError("Organization settings require a Business plan.", 403)
            plan.organization_name = organization.strip() or None
        if timezone is not None:
            plan.timezone = timezone
        updated = None
        if name is not None:
            updated = supabase_request("PUT", "user", {"data": {"full_name": name.strip()}}, token=g.access_token)
            if updated.get("id") != g.auth_user["id"]:
                raise AuthError("Could not save your name.", 502)
        db.commit()
        return jsonify(status="ok", user=public_user(updated) if updated else g.auth_user,
                       organization_name=plan.organization_name if plan else None,
                       timezone=plan.timezone if plan else "Asia/Kolkata")


@auth_bp.post("logout")
@require_user
def logout():
    supabase_request("POST", "logout?scope=local", token=g.access_token)
    return jsonify(status="ok")
