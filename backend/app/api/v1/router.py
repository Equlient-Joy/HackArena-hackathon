"""API v1 consolidated router aggregating intent, screen, and didi endpoints."""

from fastapi import APIRouter

from app.api.v1.intent import router as intent_router
from app.api.v1.screen import router as screen_router
from app.api.v1.didi import router as didi_router

api_v1_router = APIRouter()

api_v1_router.include_router(intent_router)
api_v1_router.include_router(screen_router)
api_v1_router.include_router(didi_router)

router = api_v1_router
