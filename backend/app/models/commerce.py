import uuid

from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint

from ..database import Base
from ..utils.helpers import get_current_ist


def new_id():
    return str(uuid.uuid4())


class AccountPlan(Base):
    __tablename__ = "account_plans"
    user_id = Column(String(255), primary_key=True)
    plan_id = Column(String(32), nullable=False)
    credits_available = Column(Integer, nullable=False, default=0)
    organization_name = Column(String(120), nullable=True)
    timezone = Column(String(60), nullable=False, default="Asia/Kolkata")
    created_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist)
    updated_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist, onupdate=get_current_ist)


class CreditEvent(Base):
    __tablename__ = "credit_events"
    __table_args__ = (UniqueConstraint("source_type", "source_id", name="credit_source_once"),)
    id = Column(String(36), primary_key=True, default=new_id)
    user_id = Column(String(255), nullable=False, index=True)
    change = Column(Integer, nullable=False)
    source_type = Column(String(32), nullable=False)
    source_id = Column(String(100), nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist)
    updated_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist, onupdate=get_current_ist)


class ContactGroup(Base):
    __tablename__ = "contact_groups"
    id = Column(String(36), primary_key=True, default=new_id)
    user_id = Column(String(255), nullable=False, index=True)
    name = Column(String(120), nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist)
    updated_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist, onupdate=get_current_ist)


class GroupContact(Base):
    __tablename__ = "group_contacts"
    __table_args__ = (UniqueConstraint("group_id", "phone", name="group_phone_once"),)
    id = Column(String(36), primary_key=True, default=new_id)
    group_id = Column(String(36), ForeignKey("contact_groups.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(String(255), nullable=False, index=True)
    name = Column(String(120), nullable=False)
    phone = Column(String(20), nullable=False)
    consent_status = Column(String(20), nullable=False, default="pending")
    created_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist)
    updated_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist, onupdate=get_current_ist)


class ImportBatch(Base):
    __tablename__ = "contact_imports"
    id = Column(String(36), primary_key=True, default=new_id)
    user_id = Column(String(255), nullable=False, index=True)
    group_id = Column(String(36), nullable=True)
    group_name = Column(String(120), nullable=False)
    source = Column(String(20), nullable=False)
    status = Column(String(20), nullable=False, default="review")
    rows_json = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist)
    updated_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist, onupdate=get_current_ist)


class Campaign(Base):
    __tablename__ = "campaigns"
    id = Column(String(36), primary_key=True, default=new_id)
    user_id = Column(String(255), nullable=False, index=True)
    group_id = Column(String(36), nullable=False)
    title = Column(String(120), nullable=False)
    message = Column(Text, nullable=False)
    audio_url = Column(Text, nullable=False)
    scheduled_time = Column(DateTime(timezone=True), nullable=False)
    timezone = Column(String(60), nullable=False)
    contact_ids_json = Column(Text, nullable=False)
    interval_json = Column(Text, nullable=False, default="{}")
    status = Column(String(20), nullable=False, default="draft")
    created_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist)
    updated_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist, onupdate=get_current_ist)


class AudioPreview(Base):
    __tablename__ = "audio_previews"
    id = Column(String(36), primary_key=True, default=new_id)
    user_id = Column(String(255), nullable=False, index=True)
    message = Column(Text, nullable=False)
    audio_url = Column(Text, nullable=False)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist)
    updated_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist, onupdate=get_current_ist)


class MessageTemplate(Base):
    __tablename__ = "message_templates"
    __table_args__ = (UniqueConstraint("user_id", "name_key", name="template_name_per_account"),)
    id = Column(String(36), primary_key=True, default=new_id)
    user_id = Column(String(255), nullable=False, index=True)
    name = Column(String(120), nullable=False)
    name_key = Column(String(120), nullable=False)
    message = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist)
    updated_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist, onupdate=get_current_ist)
