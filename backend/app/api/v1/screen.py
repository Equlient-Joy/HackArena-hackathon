"""Screen analysis and visual grounding endpoints."""

import io
import logging
from typing import Optional

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from PIL import Image
from starlette.concurrency import run_in_threadpool

from app.schemas.screen import ScreenAnalysisResponse
from app.services.gemini_client import analyze_screen

logger = logging.getLogger(__name__)

router = APIRouter(tags=["screen"])

MAX_IMAGE_SIZE_BYTES = 20 * 1024 * 1024  # 20 MB


@router.post("/analyze-screen", response_model=ScreenAnalysisResponse)
async def analyze_screen_endpoint(
    image: UploadFile = File(..., description="Screen capture image file"),
    scheme_id: Optional[str] = Form(None, description="Active scheme identifier"),
    current_step: Optional[str] = Form(None, description="Current workflow step context"),
    language: str = Form("hi-IN", description="Language code for spoken guidance"),
) -> ScreenAnalysisResponse:
    """Analyze a mobile screen screenshot and provide visual grounding coordinates and spoken guidance."""
    # Read file content
    image_bytes = await image.read()

    # Validate image presence and non-empty content
    if not image_bytes or len(image_bytes) == 0:
        raise HTTPException(status_code=400, detail="Empty image file provided.")

    if len(image_bytes) > MAX_IMAGE_SIZE_BYTES:
        raise HTTPException(
            status_code=400,
            detail=f"Image size exceeds maximum limit of {MAX_IMAGE_SIZE_BYTES // (1024 * 1024)}MB.",
        )

    # Validate image format using PIL
    try:
        with Image.open(io.BytesIO(image_bytes)) as img:
            img.verify()
    except Exception as exc:
        logger.warning(f"Uploaded file failed image verification: {exc}")
        raise HTTPException(
            status_code=400,
            detail="Uploaded file is corrupted or not a valid image format.",
        )

    # Execute screen analysis via Gemini grounding engine
    try:
        response = await run_in_threadpool(
            analyze_screen,
            image_bytes=image_bytes,
            scheme_id=scheme_id,
            current_step=current_step,
            language=language,
        )
        return response
    except Exception as exc:
        logger.exception(f"Screen analysis failed unexpectedly: {exc}")
        raise HTTPException(
            status_code=500,
            detail=f"Failed to analyze screen: {str(exc)}",
        )
