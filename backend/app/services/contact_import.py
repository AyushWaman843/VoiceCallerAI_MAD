import csv
import io
import re

from .llm_service import normalize_indian_number

NAME_HEADERS = {"name", "full name", "contact name", "parent name", "recipient name", "customer name"}
PHONE_HEADERS = {"number", "phone", "phone number", "mobile", "mobile no", "mobile num", "contact number", "mobile number"}


def tidy(value):
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", str(value or "").lower())).strip()


def parse_csv(blob):
    if len(blob) > 2_000_000:
        raise ValueError("File is too large (2 MB maximum).")
    try:
        text = blob.decode("utf-8-sig")
    except UnicodeDecodeError:
        raise ValueError("Save the CSV as UTF-8 and try again.") from None
    if not text.strip():
        raise ValueError("The CSV is empty.")
    try:
        dialect = csv.Sniffer().sniff(text[:4096], delimiters=",;\t")
    except csv.Error:
        dialect = csv.excel
    return list(csv.reader(io.StringIO(text, newline=""), dialect))[:1002]


def choose_columns(rows, name_column=None, phone_column=None):
    if not rows or not rows[0]:
        raise ValueError("No spreadsheet columns were found.")
    headers = [str(cell or "").strip() for cell in rows[0]]
    names = [i for i, header in enumerate(headers) if tidy(header) in NAME_HEADERS]
    phones = [i for i, header in enumerate(headers) if tidy(header) in PHONE_HEADERS]
    headerless = len(headers) == 2 and not names and not phones and bool(normalize_indian_number(headers[1]))
    if len(headers) == 2 and not names and not phones:
        names, phones = [0], [1]
    data_rows = rows if headerless else rows[1:]
    if name_column is not None and phone_column is not None:
        if not isinstance(name_column, int) or not isinstance(phone_column, int) or name_column == phone_column or min(name_column, phone_column) < 0 or max(name_column, phone_column) >= len(headers):
            raise ValueError("Choose distinct name and phone columns.")
        return name_column, phone_column, headers, data_rows
    if len(names) != 1 or len(phones) != 1 or names[0] == phones[0]:
        return None, None, headers, data_rows
    return names[0], phones[0], headers, data_rows


def inspect_rows(data_rows, name_index, phone_index, existing=()):
    existing = set(existing)
    seen = set()
    output = []
    for index, cells in enumerate(data_rows[:1000]):
        if not any(str(value or "").strip() for value in cells):
            continue
        name = str(cells[name_index] if len(cells) > name_index else "").strip()[:120]
        raw_number = str(cells[phone_index] if len(cells) > phone_index else "").strip()
        phone = normalize_indian_number(raw_number)
        status = "invalid" if not name or not phone else "duplicate" if phone in seen or phone in existing else "valid"
        if phone:
            seen.add(phone)
        output.append({"row": index, "name": name, "phone": phone, "raw_number": raw_number,
                       "status": status, "selected": status == "valid"})
    return output
