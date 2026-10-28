"""Resolve explicit saved-group references without letting a model select phone numbers."""
import re

from ..models import ContactGroup, GroupContact
from ..routes.payments import PaymentError

SELECTED_GROUPS = "selected-groups"


def normalized(value):
    return " ".join(re.findall(r"\w+", value.casefold()))


def resolve_group_recipients(db, user_id, prompt):
    call = re.search(r"\bcall\s+", prompt, re.I)
    if not call:
        return None
    clause = prompt[call.end():]
    # Only the recipient clause is searched. Names in spoken content cannot add recipients.
    clause = re.split(r"\b(?:using|use|tell|say|remind|tomorrow|today|tonight|next|starting|every)\b|\bat\s+(?:\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|noon|midnight)\b|\b(?:in|after)\s+\d+\s*(?:minutes?|hours?|days?)\b|\bon\s+(?:\d+|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b", clause, maxsplit=1, flags=re.I)[0].strip(" ,.")
    # Splitting before "use/tell" leaves the connecting "and" outside the name.
    clause = re.sub(r"\s+and$", "", clause, flags=re.I).rstrip(" ,.")
    groups = db.query(ContactGroup).filter_by(user_id=user_id).all()
    references = list(re.finditer(r"\b(?:from|in)\s+(?:the\s+)?(?:group\s+)?(.+?)(?:\s+group)?(?=\s+and\s+|[,;]|$)", clause, re.I))
    requests = []
    if references:
        previous = 0
        for reference in references:
            names = clause[previous:reference.start()].strip(" ,;&")
            names = re.sub(r"^(?:(?:and\s+also|and|also)\s+)?(?:call\s+)?(?:to\s+)?(?:(?:both|the\s+two)\s+)?", "", names, flags=re.I)
            group_name = requests[-1][0] if normalized(reference[1]) in {"that same group", "that same grp"} and requests else reference[1]
            requests.append((group_name, names))
            previous = reference.end()
        if clause[previous:].strip(" ,.;"):
            raise PaymentError("Specify a group for each person, for example Aarav from Appointments and Riya from Customers.")
    else:
        # Whole groups must be explicitly named in the call clause, not merely mentioned later.
        names = re.split(r"\s+and(?:\s+also)?\s+|[,;]", clause, flags=re.I)
        group_matches = [[group for group in groups if normalized(group.name) == normalized(re.sub(
            r"^(?:the\s+)?group\s+|\s+group$", "", name.strip(), flags=re.I))] for name in names]
        if len(names) > 1 and not any(group_matches) and not re.search(r"\bgroup\b", clause, re.I):
            contacts = db.query(GroupContact, ContactGroup).join(ContactGroup, GroupContact.group_id == ContactGroup.id).filter(
                GroupContact.user_id == user_id, ContactGroup.user_id == user_id).all()
            for name in names:
                key = normalized(re.sub(r"^(?:to\s+)?(?:both\s+)?", "", name.strip(), flags=re.I))
                exact = [(c, g) for c, g in contacts if normalized(c.name) == key]
                found = exact or [(c, g) for c, g in contacts if key and normalized(c.name).startswith(key + " ")]
                phones = {c.phone for c, _ in found}
                if len(phones) != 1:
                    raise PaymentError(f'"{name.strip()}" does not uniquely identify a saved contact. Use their full name and group.')
                contact, group = found[0]
                if contact.consent_status != "approved":
                    raise PaymentError(f"{contact.name} in {group.name} is not approved for automated calls.", 409)
                requests.append((group.name, contact.name))
        else:
            for name in names:
                candidate = re.sub(r"^(?:the\s+)?group\s+|\s+group$", "", name.strip(), flags=re.I)
                matches = [group for group in groups if normalized(group.name) == normalized(candidate)]
                if not matches:
                    if requests or re.search(r"\bgroup\b", clause, re.I):
                        raise PaymentError(f'No saved group matches "{candidate}". Use its saved name.')
                    return None
                requests.append((candidate, "everyone"))
    chosen = []
    group_names = []
    for group_name, people in requests:
        matches = [group for group in groups if normalized(group.name) == normalized(group_name)]
        if len(matches) != 1:
            raise PaymentError(f'Group "{group_name}" is missing or ambiguous. Use a unique saved group name.')
        group = matches[0]
        group_names.append(group.name)
        contacts = db.query(GroupContact).filter_by(group_id=group.id, user_id=user_id).order_by(GroupContact.name, GroupContact.id).all()
        if normalized(people) in {"everyone", "all", "all members", "all contacts", "all people", "the whole group"}:
            selected = [c for c in contacts if c.consent_status == "approved"]
        else:
            selected = []
            for name in re.split(r"\s+and(?:\s+also)?\s+|[,;&]", people, flags=re.I):
                key = normalized(name.strip(' "\''))
                exact = [c for c in contacts if normalized(c.name) == key]
                # A short first name is allowed only when it identifies exactly one saved person.
                matches = exact or [c for c in contacts if key and normalized(c.name).startswith(key + " ")]
                if len(matches) != 1:
                    raise PaymentError(f'"{name.strip()}" does not uniquely identify a contact in {group.name}. Use their full saved name.')
                if matches[0].consent_status != "approved":
                    raise PaymentError(f"{matches[0].name} in {group.name} is not approved for automated calls.", 409)
                selected.append(matches[0])
        for contact in selected:
            if contact.phone not in {c.phone for c, _ in chosen}:
                chosen.append((contact, group.name))
    if not chosen:
        raise PaymentError("No approved contacts matched this request.", 409)
    return dict(id=SELECTED_GROUPS, name=" / ".join(dict.fromkeys(group_names)), contacts=[
        dict(id=c.id, name=c.name, number=c.phone, consent_status=c.consent_status, group_name=name) for c, name in chosen])
