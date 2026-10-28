import json

from flask import Blueprint, g, jsonify, request
from sqlalchemy import func
from sqlalchemy.exc import IntegrityError

from ..database import get_db
from ..models import CallJob, Campaign, ContactGroup, GroupContact, ImportBatch
from ..services.auth_service import require_user
from ..services.contact_import import choose_columns, inspect_rows, parse_csv
from ..services.llm_service import normalize_indian_number
from ..services.plans import require_plan
from .payments import PaymentError

groups_bp = Blueprint("groups", __name__, url_prefix="/groups")


def data():
    value = request.get_json(silent=True)
    if not isinstance(value, dict):
        raise PaymentError("Provide a JSON object.")
    return value


def owned_group(db, group_id):
    group = db.query(ContactGroup).filter_by(id=group_id, user_id=g.auth_user["id"]).first()
    if group is None:
        raise PaymentError("Group not found.", 404)
    return group


def group_dict(group, contacts):
    return dict(id=group.id, name=group.name, contacts=[dict(id=c.id, name=c.name, number=c.phone,
        consent_status=c.consent_status) for c in contacts])


@groups_bp.get("")
@require_user
def list_groups():
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="groups")
        groups = db.query(ContactGroup).filter_by(user_id=g.auth_user["id"]).order_by(ContactGroup.name).all()
        contacts = db.query(GroupContact).filter_by(user_id=g.auth_user["id"]).all()
        return jsonify(groups=[group_dict(group, [c for c in contacts if c.group_id == group.id]) for group in groups])


@groups_bp.post("")
@require_user
def create_group():
    name = data().get("name")
    if not isinstance(name, str) or not 1 <= len(name.strip()) <= 120:
        raise PaymentError("Enter a group name (up to 120 characters).")
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="groups")
        if db.query(ContactGroup).filter(ContactGroup.user_id == g.auth_user["id"],
                func.lower(ContactGroup.name) == name.strip().lower()).first():
            raise PaymentError("A group with this name already exists.", 409)
        group = ContactGroup(user_id=g.auth_user["id"], name=name.strip())
        db.add(group)
        db.commit()
        return jsonify(group=group_dict(group, [])), 201


@groups_bp.patch("/<group_id>")
@require_user
def rename_group(group_id):
    name = data().get("name")
    if not isinstance(name, str) or not 1 <= len(name.strip()) <= 120:
        raise PaymentError("Enter a group name (up to 120 characters).")
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="groups")
        group = owned_group(db, group_id)
        duplicate = db.query(ContactGroup).filter(ContactGroup.user_id == g.auth_user["id"],
            ContactGroup.id != group_id, func.lower(ContactGroup.name) == name.strip().lower()).first()
        if duplicate:
            raise PaymentError("A group with this name already exists.", 409)
        group.name = name.strip()
        db.commit()
        return jsonify(group=group_dict(group, db.query(GroupContact).filter_by(group_id=group.id, user_id=g.auth_user["id"]).all()))


@groups_bp.delete("/<group_id>")
@require_user
def delete_group(group_id):
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="groups")
        group = owned_group(db, group_id)
        member_ids = {row.id for row in db.query(GroupContact).filter_by(group_id=group_id, user_id=g.auth_user["id"]).all()}
        pending = db.query(Campaign).join(CallJob, CallJob.campaign_id == Campaign.id).filter(
            Campaign.user_id == g.auth_user["id"], CallJob.user_id == g.auth_user["id"],
            CallJob.status == "pending").distinct().all()
        if any(c.group_id == group_id or member_ids.intersection(json.loads(c.contact_ids_json)) for c in pending):
            raise PaymentError("Cancel this group's pending campaign before deleting it.", 409)
        db.query(GroupContact).filter_by(group_id=group.id, user_id=g.auth_user["id"]).delete()
        db.delete(group)
        db.commit()
        return jsonify(status="deleted")


@groups_bp.post("/<group_id>/contacts")
@require_user
def add_contact(group_id):
    value = data()
    name, phone = value.get("name"), normalize_indian_number(value.get("number"))
    if not isinstance(name, str) or not 1 <= len(name.strip()) <= 120 or not phone:
        raise PaymentError("Enter a name and a valid Indian mobile number.")
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="groups")
        owned_group(db, group_id)
        contact = GroupContact(group_id=group_id, user_id=g.auth_user["id"], name=name.strip(), phone=phone,
                               consent_status="approved" if value.get("consent_confirmed") is True else "pending")
        db.add(contact)
        try:
            db.commit()
        except IntegrityError:
            db.rollback()
            raise PaymentError("This number is already in the group.", 409)
        return jsonify(contact=dict(id=contact.id, name=contact.name, number=phone,
                                    consent_status=contact.consent_status)), 201


@groups_bp.patch("/<group_id>/contacts/<contact_id>")
@require_user
def edit_contact(group_id, contact_id):
    value = data()
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="groups")
        owned_group(db, group_id)
        contact = db.query(GroupContact).filter_by(id=contact_id, group_id=group_id, user_id=g.auth_user["id"]).first()
        if not contact:
            raise PaymentError("Contact not found.", 404)
        if "name" in value:
            if not isinstance(value["name"], str) or not 1 <= len(value["name"].strip()) <= 120:
                raise PaymentError("Enter a valid name.")
            contact.name = value["name"].strip()
        if "number" in value:
            phone = normalize_indian_number(value["number"])
            if not phone:
                raise PaymentError("Enter a valid Indian mobile number.")
            if phone != contact.phone:
                contact.consent_status = "pending"
            contact.phone = phone
        if value.get("opt_out") is True:
            contact.consent_status = "opted_out"
        elif value.get("consent_confirmed") is True:
            contact.consent_status = "approved"
        try:
            db.commit()
        except IntegrityError:
            db.rollback()
            raise PaymentError("This number is already in the group.", 409)
        return jsonify(contact=dict(id=contact.id, name=contact.name, number=contact.phone,
                                    consent_status=contact.consent_status))


@groups_bp.delete("/<group_id>/contacts/<contact_id>")
@require_user
def remove_contact(group_id, contact_id):
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="groups")
        owned_group(db, group_id)
        removed = db.query(GroupContact).filter_by(id=contact_id, group_id=group_id, user_id=g.auth_user["id"]).delete()
        if not removed:
            raise PaymentError("Contact not found.", 404)
        db.commit()
        return jsonify(status="removed")


@groups_bp.post("/imports/preview")
@require_user
def preview_import():
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="groups")
        form = request.form if request.files else data()
        group_id = form.get("group_id")
        group_name = owned_group(db, group_id).name if group_id else str(form.get("group_name") or "").strip()
        if not 1 <= len(group_name) <= 120:
            raise PaymentError("Choose or name a group.")
        source = "csv" if request.files else "sheets"
        if source == "csv":
            upload = request.files.get("file")
            if upload is None or not upload.filename.lower().endswith(".csv"):
                raise PaymentError("Choose a CSV file.")
            try:
                rows = parse_csv(upload.stream.read(2_000_001))
            except ValueError as exc:
                raise PaymentError(str(exc)) from None
        else:
            rows = form.get("rows")
            if not isinstance(rows, list) or len(rows) > 1001 or any(not isinstance(row, list) for row in rows):
                raise PaymentError("Provide up to 1000 sheet rows.")
        name_col = form.get("name_column")
        phone_col = form.get("phone_column")
        try:
            name_index, phone_index, headers, data_rows = choose_columns(rows,
                int(name_col) if name_col is not None else None,
                int(phone_col) if phone_col is not None else None)
        except (TypeError, ValueError) as exc:
            raise PaymentError(str(exc) if str(exc).startswith("Choose") else "Choose valid columns.")
        if name_index is None:
            return jsonify(status="needs_columns", headers=headers, group_name=group_name), 200
        if not group_id:
            matching = db.query(ContactGroup).filter(ContactGroup.user_id == g.auth_user["id"],
                func.lower(ContactGroup.name) == group_name.lower()).first()
            group_id = matching.id if matching else None
        existing = [c.phone for c in db.query(GroupContact).filter_by(group_id=group_id, user_id=g.auth_user["id"]).all()] if group_id else []
        contacts = inspect_rows(data_rows, name_index, phone_index, existing)
        batch = ImportBatch(user_id=g.auth_user["id"], group_id=group_id,
                            group_name=group_name, source=source, rows_json=json.dumps(contacts))
        db.add(batch)
        db.commit()
        return jsonify(status="review", import_id=batch.id, group_name=group_name, contacts=contacts,
                       found=len(contacts), duplicates=sum(c["status"] == "duplicate" for c in contacts),
                       invalid=sum(c["status"] == "invalid" for c in contacts)), 200


@groups_bp.post("/imports/<import_id>/approve")
@require_user
def approve_import(import_id):
    value = data()
    selected = value.get("contacts")
    if not isinstance(selected, list) or not selected or len(selected) > 1000:
        raise PaymentError("Select at least one contact to import.")
    with get_db() as db:
        require_plan(db, g.auth_user["id"], business=True, entitlement="groups")
        batch = db.query(ImportBatch).filter_by(id=import_id, user_id=g.auth_user["id"]).with_for_update().first()
        if not batch or batch.status != "review":
            raise PaymentError("Import review not found or already saved.", 404)
        preview = {row["row"]: row for row in json.loads(batch.rows_json)}
        phones = set()
        approved = []
        for item in selected:
            if not isinstance(item, dict) or not isinstance(item.get("row"), int) or item["row"] not in preview:
                raise PaymentError("A selected row was not in this import.")
            original = preview[item["row"]]
            name = str(item.get("name", original["name"])).strip()[:120]
            phone = normalize_indian_number(item.get("number", original["phone"]))
            if not name or not phone or phone in phones:
                raise PaymentError("Correct or deselect invalid and duplicate contacts before saving.")
            phones.add(phone)
            approved.append((name, phone))
        group = owned_group(db, batch.group_id) if batch.group_id else db.query(ContactGroup).filter(
            ContactGroup.user_id == g.auth_user["id"],
            func.lower(ContactGroup.name) == batch.group_name.lower()).first()
        if group is None:
            group = ContactGroup(user_id=g.auth_user["id"], name=batch.group_name)
            db.add(group)
            db.flush()
        existing = {c.phone for c in db.query(GroupContact).filter_by(group_id=group.id, user_id=g.auth_user["id"]).all()}
        if phones & existing:
            raise PaymentError("Some numbers are already in this group. Refresh the review.", 409)
        consent = value.get("consent_confirmed") is True
        for name, phone in approved:
            db.add(GroupContact(group_id=group.id, user_id=g.auth_user["id"], name=name, phone=phone,
                                consent_status="approved" if consent else "pending"))
        batch.status = "approved"
        batch.group_id = group.id
        try:
            db.commit()
        except IntegrityError:
            db.rollback()
            raise PaymentError("These contacts or group were saved by another request. Refresh and retry.", 409) from None
        return jsonify(status="saved", group_id=group.id, imported=len(approved),
                       callable=len(approved) if consent else 0), 201
