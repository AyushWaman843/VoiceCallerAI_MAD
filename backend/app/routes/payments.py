"""Authenticated, test-only credit purchases; the paid order is the credit ledger."""
import hashlib
import hmac
import re
import uuid

import requests
from flask import Blueprint, current_app, g, jsonify, request
from sqlalchemy import Column, DateTime, Integer, String

from ..database import Base, get_db
from ..services.auth_service import require_user
from ..utils.helpers import get_current_ist

payments_bp = Blueprint("payments", __name__, url_prefix="/payments")


class CreditPurchase(Base):
    __tablename__ = "test_credit_purchases"
    order_id = Column(String(100), primary_key=True)
    user_id = Column(String(255), nullable=False, index=True)
    pack = Column(String(20), nullable=False)
    credits = Column(Integer, nullable=False)
    amount = Column(Integer, nullable=False)
    payment_id = Column(String(100), unique=True, nullable=True)
    created_at = Column(DateTime(timezone=True), default=get_current_ist, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_current_ist, onupdate=get_current_ist, nullable=False)


class PaymentError(Exception):
    def __init__(self, message, status=400):
        self.message, self.status = message, status


@payments_bp.app_errorhandler(PaymentError)
def payment_error(error):
    return jsonify(message=error.message), error.status


def keys():
    key, secret = current_app.config.get("RAZORPAY_KEY_ID", ""), current_app.config.get("RAZORPAY_KEY_SECRET", "")
    if not key.startswith("rzp_test_") or not secret:
        raise PaymentError("Configure Razorpay TEST keys in backend .env and restart Flask.", 503)
    return key, secret


def razorpay(method, path, payload=None):
    try:
        result = requests.request(method, "https://api.razorpay.com/v1/" + path,
                                  auth=keys(), json=payload, timeout=(5, 20))
        result.raise_for_status()
        return result.json()
    except (requests.RequestException, ValueError):
        # Provider bodies can include private payment information.
        raise PaymentError("Razorpay could not complete the request. Please retry.", 502)


def body():
    value = request.get_json(silent=True)
    if not isinstance(value, dict):
        raise PaymentError("A JSON object is required.")
    return value


def receipt(db, purchase):
    from ..services.plans import account_plan
    account = account_plan(db, g.auth_user["id"])
    return jsonify(status="paid", test_mode=True, credits=purchase.credits,
                   amount=purchase.amount, payment_id=purchase.payment_id, balance=account.credits_available,
                   plan_id=account.plan_id)


@payments_bp.get("/plans")
def list_plans():
    from ..services.plans import catalog
    return jsonify(plans=[{"id": key, **plan} for key, plan in catalog().items()], test_mode=True)


@payments_bp.post("/orders")
@require_user
def create_order():
    from ..services.plans import catalog
    plans = catalog()
    pack = body().get("pack")
    if not isinstance(pack, str) or pack not in plans:
        raise PaymentError("Choose Starter, Plus or Business.")
    key, _ = keys()
    credits, amount = plans[pack]["credits"], plans[pack]["price_paise"]
    order = razorpay("POST", "orders", {"amount": amount, "currency": "INR", "receipt": uuid.uuid4().hex})
    if not order.get("id") or order.get("amount") != amount or order.get("currency") != "INR":
        raise PaymentError("Unexpected Razorpay order. Please retry.", 502)
    with get_db() as db:
        db.add(CreditPurchase(order_id=order["id"], user_id=g.auth_user["id"], pack=pack,
                              credits=credits, amount=amount))
        db.commit()
    return jsonify(order_id=order["id"], key_id=key, amount=amount, credits=credits, currency="INR"), 201


@payments_bp.get("/status")
@require_user
def payment_status():
    from ..services.plans import account_plan, catalog
    # Sign-in must use verified purchases, not the unrelated saved Personal/Business choice.
    with get_db() as db:
        purchases = db.query(CreditPurchase).filter(
            CreditPurchase.user_id == g.auth_user["id"], CreditPurchase.payment_id.isnot(None)).order_by(CreditPurchase.created_at.desc()).all()
        account = account_plan(db, g.auth_user["id"])
        db.commit()
        plan = catalog().get(account.plan_id) if account else None
        return jsonify(has_purchase=bool(account), test_mode=True, plan_id=account.plan_id if account else None,
                       edition=plan["edition"] if plan else None, balance=account.credits_available if account else 0,
                       organization_name=account.organization_name if account else None,
                       timezone=account.timezone if account else "Asia/Kolkata",
                       purchases=[dict(pack=p.pack, credits=p.credits, amount=p.amount,
                                       payment_id=p.payment_id, created_at=p.created_at.isoformat()) for p in purchases])


@payments_bp.post("/verify")
@require_user
def verify_payment():
    from ..models import CreditEvent
    from ..services.plans import account_plan
    data = body()
    order_id, payment_id, signature = (data.get(key, "") for key in ("order_id", "payment_id", "signature"))
    if not all(isinstance(v, str) for v in (order_id, payment_id, signature)) or not re.fullmatch(r"pay_[A-Za-z0-9]+", payment_id):
        raise PaymentError("Invalid payment details.")
    if not re.fullmatch(r"[0-9a-f]{64}", signature):
        raise PaymentError("Invalid payment signature.")
    _, secret = keys()
    with get_db() as db:
        # Lock the owned order: concurrent verification cannot credit it twice.
        purchase = db.query(CreditPurchase).filter_by(order_id=order_id, user_id=g.auth_user["id"]).with_for_update().first()
        if purchase is None:
            raise PaymentError("Order not found.", 404)
        expected = hmac.new(secret.encode(), f"{purchase.order_id}|{payment_id}".encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expected, signature):
            raise PaymentError("Payment signature could not be verified.")
        if purchase.payment_id:
            if purchase.payment_id != payment_id:
                raise PaymentError("This order already has a different payment.", 409)
            response = receipt(db, purchase)
            db.commit()
            return response
        payment = razorpay("GET", f"payments/{payment_id}")
        if payment.get("order_id") != purchase.order_id or payment.get("amount") != purchase.amount or payment.get("currency") != "INR":
            raise PaymentError("Payment does not match this order.")
        if payment.get("status") == "authorized":
            payment = razorpay("POST", f"payments/{payment_id}/capture", {"amount": purchase.amount, "currency": "INR"})
        if payment.get("status") != "captured":
            raise PaymentError("Payment is not captured yet. Retry verification shortly.", 409)
        account = account_plan(db, g.auth_user["id"])
        if account is None:
            from ..models import AccountPlan
            account = AccountPlan(user_id=g.auth_user["id"], plan_id=purchase.pack, credits_available=0)
            db.add(account)
        account.plan_id = purchase.pack
        account.credits_available += purchase.credits
        db.add(CreditEvent(user_id=g.auth_user["id"], change=purchase.credits,
                           source_type="purchase", source_id=purchase.order_id))
        purchase.payment_id = payment_id
        db.commit()
        return receipt(db, purchase)
