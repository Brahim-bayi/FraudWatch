from sqlalchemy import Column, String, DateTime
from database import Base
from datetime import datetime, timezone

class ImageRecord(Base):
    __tablename__ = "images"

    id          = Column(String, primary_key=True)
    filename    = Column(String, unique=True, nullable=False)
    report_id   = Column(String, nullable=False, default="")
    uploaded_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    url         = Column(String, nullable=False)
