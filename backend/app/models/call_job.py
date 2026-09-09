import uuid

from sqlalchemy import CheckConstraint, Column, DateTime, String, Text

from ..database import Base
from ..utils.helpers import get_current_ist


class CallJob(Base):
    __tablename__ = "call_jobs"
    __table_args__ = (
        CheckConstraint(
            "status IN ('pending', 'calling', 'connected', 'failed', 'cancelled')",
            name="check_call_job_status",
        ),
    )

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(255), nullable=False)
    contact_name = Column(String(255), nullable=False)
    contact_number = Column(String(20), nullable=False)
    original_message = Column(Text, nullable=False)
    rephrased_message = Column(Text, nullable=True)
    audio_url = Column(Text, nullable=True)
    scheduled_time = Column(DateTime(timezone=True), nullable=False)
    status = Column(String(20), nullable=False, default="pending")
    msg91_request_id = Column(String(255), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, default=get_current_ist)
    updated_at = Column(
        DateTime(timezone=True),
        nullable=False,
        default=get_current_ist,
        onupdate=get_current_ist,
    )

    def to_dict(self):
        return {
            "id": self.id,
            "user_id": self.user_id,
            "contact_name": self.contact_name,
            "contact_number": self.contact_number,
            "original_message": self.original_message,
            "rephrased_message": self.rephrased_message,
            "audio_url": self.audio_url,
            "scheduled_time": self.scheduled_time.isoformat(),
            "status": self.status,
            "msg91_request_id": self.msg91_request_id,
            "created_at": self.created_at.isoformat(),
            "updated_at": self.updated_at.isoformat(),
        }
