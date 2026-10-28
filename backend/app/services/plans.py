import json

from flask import current_app

from ..models import AccountPlan, Campaign, CallJob, CreditEvent
from ..routes.payments import CreditPurchase

DEFAULT_PLANS = {
    "starter": {"name": "Personal Starter", "edition": "personal", "price_paise": 4900,
                "credits": 10, "groups": False, "campaigns": False},
    "plus": {"name": "Personal Plus", "edition": "personal", "price_paise": 19900,
             "credits": 50, "groups": False, "campaigns": False},
    "business": {"name": "Business", "edition": "business", "price_paise": 69900,
                 "credits": 200, "groups": True, "campaigns": True},
}


def catalog():
    configured = current_app.config.get("PLAN_CATALOG_JSON")
    plans = json.loads(configured) if configured else DEFAULT_PLANS
    if set(plans) != set(DEFAULT_PLANS):
        raise ValueError("Plan catalog must contain starter, plus and business.")
    for key, item in plans.items():
        if type(item.get("price_paise")) is not int or item["price_paise"] < 100 or type(item.get("credits")) is not int or item["credits"] < 1:
            raise ValueError("Plan prices and credits must be positive integers.")
        if item.get("edition") != ("business" if key == "business" else "personal") or not isinstance(item.get("name"), str):
            raise ValueError("Plan names and editions must match the three tiers.")
        if not isinstance(item.get("groups"), bool) or not isinstance(item.get("campaigns"), bool):
            raise ValueError("Plan group and campaign entitlements must be booleans.")
    return plans


def account_plan(db, user_id):
    account = db.query(AccountPlan).filter_by(user_id=user_id).with_for_update().first()
    if account:
        return account
    # Existing verified sandbox purchases remain valid after this schema upgrade.
    purchases = db.query(CreditPurchase).filter_by(user_id=user_id).filter(CreditPurchase.payment_id.isnot(None)).order_by(CreditPurchase.created_at).all()
    if not purchases:
        return None
    account = AccountPlan(user_id=user_id, plan_id=purchases[-1].pack,
                          credits_available=sum(p.credits for p in purchases))
    db.add(account)
    for purchase in purchases:
        db.add(CreditEvent(user_id=user_id, change=purchase.credits, source_type="purchase", source_id=purchase.order_id))
    db.flush()
    return account


def require_plan(db, user_id, business=False, entitlement=None):
    account = account_plan(db, user_id)
    plan = catalog().get(account.plan_id) if account else None
    if plan is None or (business and plan["edition"] != "business") or (entitlement and not plan.get(entitlement)):
        from ..routes.payments import PaymentError
        raise PaymentError("An active Business plan with this feature is required." if business else "Choose a plan before scheduling calls.", 403)
    return account


def reserve(db, account, job_id):
    from ..routes.payments import PaymentError
    if account.credits_available < 1:
        raise PaymentError("Not enough test credits. Add a pack before scheduling.", 409)
    account.credits_available -= 1
    db.add(CreditEvent(user_id=account.user_id, change=-1, source_type="reserve", source_id=job_id))


def settle(db, job, outcome):
    if job.credit_state == "reserved":
        account = db.query(AccountPlan).filter_by(user_id=job.user_id).with_for_update().first()
        if account:
            if outcome in ("failed", "missed", "cancelled"):
                account.credits_available += 1
                db.add(CreditEvent(user_id=job.user_id, change=1, source_type="refund",
                                   source_id=job.credit_reservation_id or job.id))
                job.credit_state = "refunded"
            else:
                job.credit_state = "consumed"

    if job.campaign_id and outcome in ("connected", "failed", "missed", "cancelled"):
        db.flush()
        remaining = db.query(CallJob.id).filter(
            CallJob.campaign_id == job.campaign_id,
            CallJob.status.in_(("pending", "calling")),
        ).first()
        if not remaining:
            campaign = db.query(Campaign).filter_by(id=job.campaign_id, user_id=job.user_id).first()
            if campaign and campaign.status == "scheduled":
                campaign.status = "finished"
