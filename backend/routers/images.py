import os
import uuid
import shutil
from fastapi import APIRouter, UploadFile, File, Form, HTTPException, Depends
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session
from database import get_db
from models import ImageRecord

router = APIRouter()
UPLOAD_DIR = "uploads"


@router.post("/upload")
async def upload_image(
    file: UploadFile = File(...),
    report_id: str = Form(default=""),
    db: Session = Depends(get_db)
):
    image_id = str(uuid.uuid4())
    ext = os.path.splitext(file.filename or "image.jpg")[1] or ".jpg"
    filename = f"{image_id}{ext}"
    filepath = os.path.join(UPLOAD_DIR, filename)

    with open(filepath, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    url = f"/api/images/{image_id}"

    db.add(ImageRecord(id=image_id, filename=filename, report_id=report_id, url=url))
    db.commit()

    return {"id": image_id, "url": url, "filename": filename}


@router.get("/{image_id}")
def get_image(image_id: str, db: Session = Depends(get_db)):
    record = db.query(ImageRecord).filter(ImageRecord.id == image_id).first()
    if not record:
        raise HTTPException(status_code=404, detail="Image non trouvée")

    filepath = os.path.join(UPLOAD_DIR, record.filename)
    if not os.path.exists(filepath):
        raise HTTPException(status_code=404, detail="Fichier introuvable sur le disque")

    return FileResponse(filepath, media_type="image/jpeg")


@router.delete("/{image_id}")
def delete_image(image_id: str, db: Session = Depends(get_db)):
    record = db.query(ImageRecord).filter(ImageRecord.id == image_id).first()
    if not record:
        raise HTTPException(status_code=404, detail="Image non trouvée")

    filepath = os.path.join(UPLOAD_DIR, record.filename)
    if os.path.exists(filepath):
        os.remove(filepath)

    db.delete(record)
    db.commit()
    return {"message": f"Image {image_id} supprimée"}


@router.get("/")
def list_images(db: Session = Depends(get_db)):
    records = db.query(ImageRecord).order_by(ImageRecord.uploaded_at.desc()).all()
    return [{"id": r.id, "url": r.url, "report_id": r.report_id, "uploaded_at": str(r.uploaded_at)} for r in records]
