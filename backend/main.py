import os
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from database import engine
import models
from routers import images

# Créer les tables SQLite au démarrage
models.Base.metadata.create_all(bind=engine)

# Créer le dossier d'uploads s'il n'existe pas
os.makedirs("uploads", exist_ok=True)

app = FastAPI(
    title="FraudWatch Backend",
    description="API personnelle pour le stockage des images de fraude",
    version="1.0.0"
)

app.include_router(images.router, prefix="/api/images", tags=["Images"])


@app.get("/")
def root():
    return {
        "status": "FraudWatch Backend actif",
        "version": "1.0.0",
        "endpoints": {
            "upload_image":  "POST /api/images/upload",
            "get_image":     "GET  /api/images/{id}",
            "delete_image":  "DELETE /api/images/{id}",
            "list_images":   "GET  /api/images/",
            "docs":          "GET  /docs"
        }
    }
