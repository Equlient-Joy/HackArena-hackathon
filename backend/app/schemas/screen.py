"""Pydantic schemas for screen analysis, visual grounding, and Didi conversational assistance."""

from typing import List, Optional
from pydantic import BaseModel, Field


class ScreenAnalysisResponse(BaseModel):
    """Visual grounding response guiding the user on the current screen."""

    action: str = Field(
        ...,
        description="Navigation action to perform: HIGHLIGHT, SCROLL_DOWN, WAIT, SUCCESS, etc.",
    )
    box_2d: List[int] = Field(
        default_factory=list,
        description="Target element bounding box [ymin, xmin, ymax, xmax] normalized to 0-1000",
    )
    spoken_guidance: str = Field(
        ...,
        description="Spoken vernacular guidance in compassionate elder sister (दीदी) tone",
    )
    subtitle_text: str = Field(
        ...,
        description="Vernacular subtitle corresponding to spoken guidance",
    )
    field_type: Optional[str] = Field(
        default=None,
        description="Type of the highlighted field: e.g., BUTTON, INPUT_TEXT, CAPTCHA, DROPDOWN",
    )
    captcha_code: Optional[str] = Field(
        default=None,
        description="Extracted or solved captcha characters if field_type is CAPTCHA",
    )
    status: str = Field(
        default="IN_PROGRESS",
        description="Workflow status: IN_PROGRESS, COMPLETED, SUCCESS, ERROR",
    )


class DidiAskRequest(BaseModel):
    """User query asked directly to Didi during form filling or portal navigation."""

    user_query: str = Field(..., description="User question or clarification request")
    language: str = Field("hi-IN", description="User language code (default hi-IN)")


class DidiAskResponse(BaseModel):
    """Didi's compassionate answer and subtitle."""

    spoken_answer: str = Field(
        ...,
        description="Empathetic, clear answer tailored for rural users",
    )
    subtitle_text: str = Field(
        ...,
        description="Text subtitle of the answer for display",
    )
