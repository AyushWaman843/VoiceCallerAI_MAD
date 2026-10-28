import json
import re
import uuid
from collections import Counter
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from flask import Blueprint, current_app, g, jsonify, request

from ..database import get_db
from ..models import CallJob, Campaign, ContactGroup, GroupContact, MessageTemplate
from ..services.auth_service import require_user
from ..services.llm_service import process_message
from ..services.plans import require_plan, reserve, settle
from ..services.tts_service import generate_audio
from ..services.campaign_timing import interval_from_instruction, recipient_time
from ..services.group_recipients import SELECTED_GROUPS, resolve_group_recipients
from .templates import resolve_template
from ..utils.helpers import ensure_ist_datetime, get_current_ist, is_at_least_two_minutes_ahead
from .calls import _schedule_job, _is_public_url
from .payments import PaymentError

campaigns_bp = Blueprint("campaigns", __name__, url_prefix="/campaigns")


def body():
    value = request.get_json(silent=True)
    if not isinstance(value, dict):
        raise PaymentError("Provide campaign details as JSON.")
    return value


def owned_group(db, group_id):
    group = db.query(ContactGroup).filter_by(id=group_id, user_id=g.auth_user["id"]).first()
    if not group:
        raise PaymentError("Group not found.", 404)
    return group


def owned_campaign(db, campaign_id):
    campaign = db.query(Campaign).filter_by(id=campaign_id, user_id=g.auth_user["id"]).with_for_update().first()
    if not campaign:
        raise PaymentError("Campaign not found.", 404)
    return campaign


def eligible(db, group_id, selected_ids=None):
    query = db.query(GroupContact).filter_by(user_id=g.auth_user["id"], consent_status="approved")
    if group_id == SELECTED_GROUPS:
        if selected_ids is None:
            raise PaymentError("Select the exact contacts to call.")
    else:
        query = query.filter_by(group_id=group_id)
    contacts = query.all()
    if selected_ids is not None:
        if not isinstance(selected_ids, list) or any(not isinstance(x, str) for x in selected_ids):
            raise PaymentError("Choose valid recipients.")
        by_id = {c.id: c for c in contacts}
        contacts = [by_id[key] for key in dict.fromkeys(selected_ids) if key in by_id]
        if len(contacts) != len(set(selected_ids)):
            raise PaymentError("Some recipients are no longer eligible. Review again.", 409)
    unique = {}
    for contact in contacts:
        unique.setdefault(contact.phone, contact)
    return list(unique.values())


def local_time(value, zone):
    try:
        timezone = ZoneInfo(zone)
        if not isinstance(value, str):
            raise ValueError()
        parsed = datetime.fromisoformat(value)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone)
        return ensure_ist_datetime(parsed.isoformat())
    except (ValueError, ZoneInfoNotFoundError):
        raise PaymentError("Choose a valid scheduled time and timezone.")


def summary(campaign, recipients, balance):
    count = len(recipients)
    rate = current_app.config.get("EXOTEL_ESTIMATED_COST_PAISE")
    return dict(id=campaign.id, group_id=campaign.group_id, title=campaign.title,
                message=campaign.message, audio_url=campaign.audio_url,
                scheduled_time=campaign.scheduled_time.isoformat(), timezone=campaign.timezone,
                interval=json.loads(campaign.interval_json or "{}"),
                status=campaign.status, recipients=[dict(id=c.id, name=c.name, number=c.phone,
                    scheduled_time=recipient_time(campaign.scheduled_time, json.loads(campaign.interval_json or "{}"), index, campaign.timezone).isoformat()) for index, c in enumerate(recipients)],
                eligible_count=count, estimated_credits=count, balance=balance,
                estimated_provider_cost_paise=count * int(rate) if rate else None)


@campaigns_bp.post("/resolve")
@require_user
def resolve_recipients():
    prompt = body().get("raw_prompt")
    if not isinstance(prompt, str) or not 1 <= len(prompt.strip()) <= 8000:
        raise PaymentError("Enter a calling instruction.")
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="campaigns")
        selection = resolve_group_recipients(db, g.auth_user["id"], prompt)
        return jsonify(status="selected" if selection else "not_group", selection=selection)


@campaigns_bp.post("/preview")
@require_user
def preview():
    value = body()
    prompt = value.get("raw_prompt")
    with get_db() as db:
        account = require_plan(db, g.auth_user["id"], business=True, entitlement="campaigns")
        group_id = value.get("group_id")
        if not group_id and isinstance(prompt, str):
            # Group matching is deterministic and scoped to the signed-in business.
            names = db.query(ContactGroup).filter_by(user_id=g.auth_user["id"]).all()
            found = [group for group in names if re.search(r"(?<!\w)" + re.escape(group.name) + r"(?!\w)", prompt, re.I)]
            if len(found) == 1:
                group_id = found[0].id
        if not isinstance(group_id, str):
            raise PaymentError("Select exactly one saved group.")
        group = owned_group(db, group_id) if group_id != SELECTED_GROUPS else None
        contacts = eligible(db, group_id, value.get("recipient_ids"))
        if not contacts:
            raise PaymentError("No consent-approved recipients in this group.", 409)
        message = value.get("message")
        time_value = value.get("scheduled_time")
        interval = {}
        template_message = None
        resolved_template = {}
        template_id = value.get("template_id")
        if template_id is not None:
            if not isinstance(template_id, str):
                raise PaymentError("Choose a valid saved template.")
            template = db.query(MessageTemplate).filter_by(id=template_id, user_id=g.auth_user["id"]).first()
            if template is None:
                raise PaymentError("Saved template not found.", 404)
            template_message = template.message
            resolved_template["name"] = template.name
        elif isinstance(prompt, str):
            prompt, template_message = resolve_template(db, g.auth_user["id"], prompt, resolved=resolved_template)
        if isinstance(prompt, str) and prompt.strip() and (not message or not time_value):
            try:
                agent = process_message(prompt, account.organization_name or g.auth_user["full_name"], [],
                    get_current_ist().astimezone(ZoneInfo(account.timezone)), account.timezone, campaign=True)
            except Exception:
                current_app.logger.exception("Campaign message preparation failed")
                raise PaymentError("Unable to understand the instruction right now. Please try again.", 502) from None
            message = message or agent.get("message")
            time_value = time_value or agent.get("scheduled_time")
            interval = interval_from_instruction(prompt, agent.get("interval"), model_interpreted=True)
        elif isinstance(prompt, str):
            interval = interval_from_instruction(prompt)
        message = template_message or message
        if not isinstance(message, str) or not message.strip() or len(message) > 2000:
            raise PaymentError("Enter a message to speak to the group.")
        if not time_value:
            raise PaymentError("Add when the first call should start to your instruction, for example tomorrow at 3pm.")
        schedule = local_time(time_value, account.timezone)
        if not is_at_least_two_minutes_ahead(schedule):
            raise PaymentError("Schedule at least two minutes ahead.")
        recipient_time(schedule, interval, len(contacts) - 1, account.timezone)
        if account.credits_available < len(contacts):
            raise PaymentError(f"You need {len(contacts)} test credits for this campaign; {account.credits_available} remain.", 409)
        voice = account.organization_name or g.auth_user["full_name"]
        spoken = f"Hello, this is an automated message on behalf of {voice}. {message.strip()}"
        try:
            # Editing just the recipients or schedule must not spend more TTS credits.
            cached = db.query(Campaign).filter_by(user_id=g.auth_user["id"], message=spoken).filter(
                Campaign.created_at > get_current_ist() - timedelta(hours=1)).order_by(Campaign.created_at.desc()).first()
            audio_url = cached.audio_url if cached else generate_audio(spoken)
        except Exception:
            current_app.logger.exception("Campaign audio generation failed")
            raise PaymentError("Unable to generate the campaign audio right now.", 502)
        if not _is_public_url(audio_url):
            raise PaymentError("Audio was generated, but PUBLIC_BASE_URL must point to a public HTTPS server.", 503)
        title = group.name if group else "Selected contacts"
        campaign = Campaign(user_id=g.auth_user["id"], group_id=group_id, title=title,
                            message=spoken, audio_url=audio_url, scheduled_time=schedule,
                            timezone=account.timezone, contact_ids_json=json.dumps([c.id for c in contacts]),
                            interval_json=json.dumps(interval))
        db.add(campaign)
        db.commit()
        result = summary(campaign, contacts, account.credits_available)
        result["template_name"] = resolved_template.get("name")
        return jsonify(status="needs_confirmation", campaign=result), 200


@campaigns_bp.patch("/<campaign_id>")
@require_user
def edit_draft(campaign_id):
    value = body()
    with get_db() as db:
        account = require_plan(db, g.auth_user["id"], business=True, entitlement="campaigns")
        campaign = owned_campaign(db, campaign_id)
        if campaign.status != "draft":
            raise PaymentError("Only draft campaigns can be edited.", 409)
        if "recipient_ids" in value:
            campaign.contact_ids_json = json.dumps([c.id for c in eligible(db, campaign.group_id, value["recipient_ids"])])
        contacts = eligible(db, campaign.group_id, json.loads(campaign.contact_ids_json))
        if not contacts:
            raise PaymentError("Select at least one eligible recipient.")
        if "scheduled_time" in value:
            campaign.scheduled_time = local_time(value["scheduled_time"], account.timezone)
            if not is_at_least_two_minutes_ahead(campaign.scheduled_time):
                raise PaymentError("Schedule at least two minutes ahead.")
        if "message" in value:
            message = value["message"]
            if not isinstance(message, str) or not 1 <= len(message.strip()) <= 2000:
                raise PaymentError("Enter a valid message.")
            voice = account.organization_name or g.auth_user["full_name"]
            campaign.message = f"Hello, this is an automated message on behalf of {voice}. {message.strip()}"
            try:
                campaign.audio_url = generate_audio(campaign.message)
            except Exception:
                raise PaymentError("Unable to update the audio preview.", 502)
        db.commit()
        return jsonify(campaign=summary(campaign, contacts, account.credits_available))


@campaigns_bp.post("/<campaign_id>/schedule")
@require_user
def schedule(campaign_id):
    if body().get("confirmed") is not True:
        raise PaymentError("Confirm the recipient list and audio preview first.")
    with get_db() as db:
        account = require_plan(db, g.auth_user["id"], business=True, entitlement="campaigns")
        campaign = owned_campaign(db, campaign_id)
        if campaign.status != "draft":
            raise PaymentError("This campaign has already been scheduled.", 409)
        if not is_at_least_two_minutes_ahead(campaign.scheduled_time):
            raise PaymentError("Choose a later time and preview again.")
        contacts = eligible(db, campaign.group_id, json.loads(campaign.contact_ids_json))
        if not contacts:
            raise PaymentError("No eligible recipients remain.", 409)
        if account.credits_available < len(contacts):
            raise PaymentError("Not enough test credits for all selected recipients.", 409)
        jobs = []
        for index, contact in enumerate(contacts):
            job = CallJob(id=str(uuid.uuid4()), user_id=g.auth_user["id"], contact_name=contact.name,
                          contact_number=contact.phone, original_message=campaign.message,
                          rephrased_message=campaign.message, audio_url=campaign.audio_url,
                          scheduled_time=recipient_time(campaign.scheduled_time, json.loads(campaign.interval_json or "{}"), index, campaign.timezone), status="pending", campaign_id=campaign.id,
                          credit_state="reserved")
            job.credit_reservation_id = job.id
            reserve(db, account, job.id)
            db.add(job)
            jobs.append(job)
        campaign.status = "scheduled"
        scheduled_ids = []
        try:
            for job in jobs:
                _schedule_job(job.id, job.scheduled_time)
                scheduled_ids.append(job.id)
            db.commit()
        except Exception:
            db.rollback()
            scheduler = current_app.extensions.get("scheduler")
            for job_id in scheduled_ids:
                if scheduler and scheduler.get_job(job_id):
                    scheduler.remove_job(job_id)
            current_app.logger.exception("Unable to schedule campaign %s", campaign_id)
            raise PaymentError("Unable to schedule this campaign. Your credits were not charged.", 503) from None
    return jsonify(status="scheduled", campaign_id=campaign_id, jobs=len(jobs)), 201


@campaigns_bp.get("")
@require_user
def list_campaigns():
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="campaigns")
        campaigns = db.query(Campaign).filter_by(user_id=g.auth_user["id"]).order_by(Campaign.created_at.desc()).all()
        jobs = db.query(CallJob).filter_by(user_id=g.auth_user["id"]).filter(CallJob.campaign_id.isnot(None)).all()
        return jsonify(campaigns=[dict(id=c.id, title=c.title, scheduled_time=c.scheduled_time.isoformat(),
            status=("finished" if c.status == "scheduled" and any(j.campaign_id == c.id for j in jobs)
                    and all(j.status in {"connected", "missed", "failed", "cancelled"} for j in jobs if j.campaign_id == c.id)
                    else c.status), total=sum(j.campaign_id == c.id for j in jobs),
            results=dict(Counter(j.status for j in jobs if j.campaign_id == c.id))) for c in campaigns])


@campaigns_bp.post("/<campaign_id>/cancel")
@require_user
def cancel_campaign(campaign_id):
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="campaigns")
        campaign = owned_campaign(db, campaign_id)
        jobs = db.query(CallJob).filter_by(user_id=g.auth_user["id"], campaign_id=campaign.id, status="pending").all()
        for job in jobs:
            job.status = "cancelled"
            settle(db, job, "cancelled")
            scheduler = current_app.extensions.get("scheduler")
            if scheduler and scheduler.get_job(job.id):
                scheduler.remove_job(job.id)
        campaign.status = "cancelled"
        db.commit()
        return jsonify(status="cancelled", refunded=len(jobs))
