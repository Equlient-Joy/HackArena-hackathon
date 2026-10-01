"""Pydantic schemas for scheme intent recognition."""

from pydantic import BaseModel, Field


class IntentRequest(BaseModel):
    """Incoming request to detect user intent and match a welfare scheme."""

    query: str = Field(..., description="Voice transcript or text query from user")
    language: str = Field("hi-IN", description="Language code (e.g., hi-IN, en-IN)")


class IntentResponse(BaseModel):
    """Response containing matched welfare scheme details and voice guidance."""

    scheme_id: str = Field(..., description="Unique scheme identifier")
    scheme_name: str = Field(..., description="Display name of the welfare scheme")
    portal_url: str = Field(..., description="Official government portal URL")
    confirmation_speech: str = Field(..., description="Empathetic spoken confirmation message")
    initial_prompt: str = Field(..., description="Initial guiding prompt for the user")
