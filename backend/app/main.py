"""FastAPI application entrypoint for Sahaayika (सहायिका) Brain API."""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1.router import api_v1_router
from app.config import settings

app = FastAPI(
    title="Sahaayika (सहायिका) Brain API",
    version="1.0.0",
    description="Multimodal Indic visual grounding and scheme navigation backend for rural citizens",
)

# Enable CORS for local Android emulators (10.0.2.2), physical USB devices, and web frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount API v1 endpoints
app.include_router(api_v1_router, prefix="/api/v1")


@app.get("/health", tags=["system"])
def health_check() -> dict:
    """System health check endpoint."""
    return {
        "status": "ok",
        "service": "sahaayika-brain",
        "version": "1.0.0",
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host=settings.HOST, port=settings.PORT, reload=True)
