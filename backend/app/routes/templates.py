import re
from difflib import SequenceMatcher
from dataclasses import dataclass

from flask import Blueprint, g, jsonify, request
from sqlalchemy.exc import IntegrityError

from ..database import get_db
from ..models import MessageTemplate
from ..services.auth_service import require_user
from .payments import PaymentError

templates_bp = Blueprint("templates", __name__, url_prefix="/templates")


@dataclass
class TemplateInput:
    name: str
    message: str

    @classmethod
    def parse(cls, value):
        if not isinstance(value, dict):
            raise PaymentError("Provide a template name and message.")
        name, message = value.get("name"), value.get("message")
        if not isinstance(name, str) or not 1 <= len(name.strip()) <= 120:
            raise PaymentError("Use a template name of 1–120 characters.")
        if not isinstance(message, str) or not 1 <= len(message.strip()) <= 2000:
            raise PaymentError("Use a template message of 1–2000 characters.")
        return cls(name.strip(), message.strip())


def serialize(template):
    return dict(id=template.id, name=template.name, message=template.message)


def _match_template(templates, name):
    key = " ".join(name.split()).casefold()
    exact = [row for row in templates if row.name_key == key]
    if len(exact) == 1:
        return exact[0]
    ranked = sorted(((SequenceMatcher(None, key, row.name_key).ratio(), row)
                     for row in templates), key=lambda item: item[0], reverse=True)
    # Correct a distinctive spelling slip, never silently choose between near ties.
    if len(key) >= 5 and ranked and ranked[0][0] >= .84:
        if len(ranked) == 1 or ranked[0][0] - ranked[1][0] >= .08:
            return ranked[0][1]
        raise PaymentError(f'Template "{name}" is ambiguous. Use its full saved name.')
    raise PaymentError(f'Template "{name}" was not found in your saved templates.', 404)


def resolve_template(db, user_id, prompt, resolved=None):
    # Resolve only an explicit reference; never let the LLM invent a saved template.
    prefix = re.search(r'\b(?:use|using)\s+(?:the\s+)?template\s+', prompt, re.I)
    if prefix:
        # Match saved names longest-first so "Reminder" cannot swallow "Reminder Follow Up".
        templates = db.query(MessageTemplate).filter_by(user_id=user_id).all()
        for template in sorted(templates, key=lambda row: len(row.name_key), reverse=True):
            pattern = r'["\'\s]*' + r'\s+'.join(re.escape(part) for part in template.name_key.split()) + r'\b["\']?'
            name = re.match(pattern, prompt[prefix.end():], re.I)
            if name:
                if resolved is not None:
                    resolved["name"] = template.name
                instruction = prompt[:prefix.start()] + "deliver the saved message" + prompt[prefix.end() + name.end():]
                return instruction, template.message
        tail = prompt[prefix.end():]
        name = re.match(r'''["']([^"']+)["']|(.+?)(?=\s+(?:to|for|at|on|tomorrow|today|tonight|every|starting|in|after|and)\b|[,.!?]|$)''', tail, re.I)
        if not name:
            raise PaymentError("Specify the saved template name.")
        template = _match_template(templates, name.group(1) or name.group(2))
        if resolved is not None:
            resolved["name"] = template.name
        return prompt[:prefix.start()] + "deliver the saved message" + tail[name.end():], template.message
    match = re.search(r'\b(?:use|using)\s+(?:the\s+)?["“\'](.+?)["”\']\s+template\b', prompt, re.I)
    if not match:
        match = re.search(r'\b(?:use|using)\s+(?:the\s+)?([\w][\w -]*?)\s+template\b', prompt, re.I)
    if not match:
        return prompt, None
    key = " ".join(match.group(1).split()).casefold()
    template = _match_template(db.query(MessageTemplate).filter_by(user_id=user_id).all(), key)
    if resolved is not None:
        resolved["name"] = template.name
    # Keep template text out of time extraction: it can contain unrelated dates or numbers.
    instruction = prompt[:match.start()] + "deliver the saved message" + prompt[match.end():]
    return instruction, template.message


@templates_bp.get("")
@require_user
def list_templates():
    with get_db() as db:
        rows = db.query(MessageTemplate).filter_by(user_id=g.auth_user["id"]).order_by(MessageTemplate.name_key).all()
        return jsonify(templates=[serialize(row) for row in rows])


@templates_bp.post("")
@templates_bp.patch("/<template_id>")
@require_user
def save_template(template_id=None):
    value = TemplateInput.parse(request.get_json(silent=True))
    with get_db() as db:
        row = db.query(MessageTemplate).filter_by(id=template_id, user_id=g.auth_user["id"]).first() if template_id else MessageTemplate(user_id=g.auth_user["id"])
        if row is None:
            raise PaymentError("Template not found.", 404)
        row.name, row.message = value.name, value.message
        row.name_key = " ".join(value.name.split()).casefold()
        db.add(row)
        try:
            db.commit()
        except IntegrityError:
            db.rollback()
            raise PaymentError("You already have a template with that name.", 409) from None
        return jsonify(template=serialize(row)), 200 if template_id else 201


@templates_bp.delete("/<template_id>")
@require_user
def delete_template(template_id):
    with get_db() as db:
        row = db.query(MessageTemplate).filter_by(id=template_id, user_id=g.auth_user["id"]).first()
        if row is None:
            raise PaymentError("Template not found.", 404)
        db.delete(row)
        db.commit()
        return jsonify(status="deleted")
