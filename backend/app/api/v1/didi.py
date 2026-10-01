"""Conversational assistance endpoint with Didi persona."""

import logging
from fastapi import APIRouter, HTTPException
from starlette.concurrency import run_in_threadpool

from app.schemas.screen import DidiAskRequest, DidiAskResponse
from app.services.gemini_client import ask_didi

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/didi", tags=["didi"])


@router.post("/ask", response_model=DidiAskResponse)
async def ask_didi_endpoint(request: DidiAskRequest) -> DidiAskResponse:
    """Provide compassionate Indic voice assistance in Didi's elder-sister persona."""
    cleaned_query = request.user_query.strip() if request.user_query else ""
    if not cleaned_query:
        raise HTTPException(status_code=400, detail="User query cannot be empty.")

    try:
        response = await run_in_threadpool(
            ask_didi,
            user_query=cleaned_query,
            language=request.language,
        )
        return response
    except Exception as exc:
        logger.exception(f"Ask-Didi interaction failed unexpectedly: {exc}")
        raise HTTPException(
            status_code=500,
            detail=f"Didi assistance failed: {str(exc)}",
        )
