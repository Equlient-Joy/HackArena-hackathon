"""Gemini Grounding Engine & CAPTCHA Reader for Sahaayika (सहायिका).

Integrates with the Google GenAI SDK (`google-genai`), providing multimodal visual
grounding on mobile screenshots, elder-sister ("दीदी") vernacular guidance,
CAPTCHA transcription, and conversational voice assistance.
"""

from __future__ import annotations

import json
import logging
from typing import Optional, Union

from google import genai
from google.genai import types

from app.config import settings
from app.schemas.screen import DidiAskResponse, ScreenAnalysisResponse
from app.services.prompt_engine import (
    FALLBACK_DIDI_ANSWERS,
    build_didi_ask_prompt,
    build_didi_system_instruction,
    build_screen_analysis_prompt,
    build_screen_system_instruction,
    contains_prohibited_terms,
    format_captcha_guidance,
    format_id_guidance,
    get_default_action_guidance,
    normalize_language,
    validate_guidance_text,
)

logger = logging.getLogger(__name__)


def clamp_coordinates(coords: list[int]) -> list[int]:
    """Clamp box_2d coordinates to valid 0-1000 bounds."""
    if not coords or len(coords) != 4:
        return []
    clamped = [max(0, min(1000, int(c))) for c in coords]
    # Ensure ymin <= ymax and xmin <= xmax
    ymin, xmin, ymax, xmax = clamped
    if ymin > ymax:
        ymin, ymax = ymax, ymin
    if xmin > xmax:
        xmin, xmax = xmax, xmin
    return [ymin, xmin, ymax, xmax]


class GeminiGroundingEngine:
    """Multimodal grounding client powered by Gemini and google-genai."""

    def __init__(
        self,
        api_key: Optional[str] = None,
        model_name: Optional[str] = None,
        force_mock: bool = False,
    ):
        self.api_key = api_key if api_key is not None else settings.GEMINI_API_KEY
        self.model_name = model_name if model_name is not None else settings.GEMINI_MODEL
        self.force_mock = force_mock
        self._client: Optional[genai.Client] = None

        if self.is_live():
            try:
                self._client = genai.Client(api_key=self.api_key)
            except Exception as e:
                logger.warning(f"Could not initialize google-genai client ({e}); using mock mode.")
                self._client = None

    def is_live(self) -> bool:
        """Return True if live Gemini API is configured and mock mode is not forced."""
        return bool(self.api_key and not self.force_mock and self.api_key.strip() != "")

    def _mock_analyze_screen(
        self,
        image_bytes: bytes,
        scheme_id: Optional[str] = None,
        current_step: Optional[str] = None,
        language: str = "hi-IN",
    ) -> ScreenAnalysisResponse:
        """Deterministic fallback and offline mock for screen analysis."""
        norm_lang = normalize_language(language)
        step_lower = (current_step or "").lower()
        scheme_lower = (scheme_id or "").lower()

        # Check for CAPTCHA step or image hints
        if "captcha" in step_lower or "कैप्चा" in step_lower:
            captcha_code = "4X7K"
            spoken = format_captcha_guidance(captcha_code, norm_lang)
            return ScreenAnalysisResponse(
                action="HIGHLIGHT",
                box_2d=[520, 310, 580, 480],
                spoken_guidance=spoken,
                subtitle_text=spoken,
                field_type="CAPTCHA",
                captcha_code=captcha_code,
                status="IN_PROGRESS",
            )

        # Check for Aadhaar or Citizen ID input step
        if any(term in step_lower or term in scheme_lower for term in ["aadhaar", "आधार", "samagra", "समग्र", "id_number", "citizen_id"]):
            is_samagra = "samagra" in step_lower or "समग्र" in step_lower
            spoken = format_id_guidance("Samagra" if is_samagra else "Aadhaar", norm_lang)
            return ScreenAnalysisResponse(
                action="HIGHLIGHT",
                box_2d=[340, 210, 410, 680],
                spoken_guidance=spoken,
                subtitle_text=spoken,
                field_type="INPUT_TEXT",
                captcha_code=None,
                status="IN_PROGRESS",
            )

        # Check for final / success step
        if any(term in step_lower for term in ["success", "completed", "done", "finish", "सफल"]):
            success_messages = {
                "hi-IN": "दीदी, बधाई हो! आपका काम पूरा हो गया है।",
                "ta-IN": "அக்கா, வாழ்த்துகள்! உங்கள் பணி முடிந்தது.",
                "bn-IN": "দিদি, অভিনন্দন! আপনার কাজ সম্পূর্ণ হয়েছে।",
                "te-IN": "అక్కా, అభినందనలు! మీ పని పూర్తయింది.",
                "mr-IN": "दीदी, अभिनंदन! तुमचे काम पूर्ण झाले आहे.",
                "en-IN": "Didi, congratulations! Your work is completed.",
            }
            spoken = success_messages.get(norm_lang, success_messages["hi-IN"])
            return ScreenAnalysisResponse(
                action="SUCCESS",
                box_2d=[],
                spoken_guidance=spoken,
                subtitle_text=spoken,
                field_type=None,
                captcha_code=None,
                status="SUCCESS",
            )

        # Default action: highlight navigation button or first field
        guidance = get_default_action_guidance(norm_lang)
        return ScreenAnalysisResponse(
            action="HIGHLIGHT",
            box_2d=[620, 350, 690, 650],
            spoken_guidance=guidance,
            subtitle_text=guidance,
            field_type="BUTTON",
            captcha_code=None,
            status="IN_PROGRESS",
        )

    def _mock_ask_didi(
        self,
        user_query: str,
        language: str = "hi-IN",
        screen_image: Optional[bytes] = None,
    ) -> DidiAskResponse:
        """Deterministic fallback and offline mock for Ask-Didi conversation."""
        norm_lang = normalize_language(language)
        answers = FALLBACK_DIDI_ANSWERS.get(norm_lang, FALLBACK_DIDI_ANSWERS["hi-IN"])
        q_lower = user_query.lower()

        if any(k in q_lower for k in ["आधार", "aadhaar", "card", "कार्ड"]):
            answer = answers.get("aadhaar_help", answers["general"])
        elif any(k in q_lower for k in ["क्या करना", "what to do", "मदद", "help", "समझाएं"]):
            answer = answers.get("what_to_do", answers["general"])
        elif any(k in q_lower for k in ["कैप्चा", "captcha", "कोड", "code"]):
            answer = answers.get("captcha_help", answers["general"])
        else:
            answer = answers.get("general", "दीदी, मैं आपके साथ हूँ। कृपया स्क्रीन पर दिए गए निर्देश देखें।")

        return DidiAskResponse(
            spoken_answer=answer,
            subtitle_text=answer,
        )

    def _parse_screen_response(
        self,
        response: types.GenerateContentResponse,
        language: str,
    ) -> ScreenAnalysisResponse:
        """Safely parse ScreenAnalysisResponse from Gemini response."""
        # 1. Try response.parsed if populated by google-genai
        parsed_obj = getattr(response, "parsed", None)
        if isinstance(parsed_obj, ScreenAnalysisResponse):
            return parsed_obj

        # 2. Try JSON parse from response.text
        text_content = getattr(response, "text", "") or ""
        text_content = text_content.strip()

        # Handle markdown fences if present
        if text_content.startswith("```json"):
            text_content = text_content[7:]
        elif text_content.startswith("```"):
            text_content = text_content[3:]
        if text_content.endswith("```"):
            text_content = text_content[:-3]
        text_content = text_content.strip()

        if text_content:
            data = json.loads(text_content)
            return ScreenAnalysisResponse.model_validate(data)

        raise ValueError("Empty or unparseable response from Gemini API")

    def _sanitize_and_validate_screen_response(
        self,
        res: ScreenAnalysisResponse,
        language: str,
    ) -> ScreenAnalysisResponse:
        """Ensure bounds are 0-1000 clamped and CAPTCHA / ID guidelines are met."""
        # Clamp coordinates
        clamped_box = clamp_coordinates(res.box_2d)

        # Enforce CAPTCHA voice guidance convention if CAPTCHA detected
        spoken = res.spoken_guidance
        subtitle = res.subtitle_text or spoken
        captcha_code = res.captcha_code

        if res.field_type == "CAPTCHA" and captcha_code:
            spoken = format_captcha_guidance(captcha_code, language)
            subtitle = spoken

        # Validate word count / prohibited words
        valid, issues = validate_guidance_text(spoken, max_words=14)
        if not valid and contains_prohibited_terms(spoken):
            logger.info(f"Replacing prohibited wording in guidance: {spoken}")
            spoken = get_default_action_guidance(language)
            subtitle = spoken

        return ScreenAnalysisResponse(
            action=res.action,
            box_2d=clamped_box,
            spoken_guidance=spoken,
            subtitle_text=subtitle,
            field_type=res.field_type,
            captcha_code=captcha_code,
            status=res.status,
        )

    def _parse_didi_response(
        self,
        response: types.GenerateContentResponse,
        language: str,
    ) -> DidiAskResponse:
        """Safely parse DidiAskResponse from Gemini response."""
        parsed_obj = getattr(response, "parsed", None)
        if isinstance(parsed_obj, DidiAskResponse):
            return parsed_obj

        text_content = getattr(response, "text", "") or ""
        text_content = text_content.strip()

        if text_content.startswith("```json"):
            text_content = text_content[7:]
        elif text_content.startswith("```"):
            text_content = text_content[3:]
        if text_content.endswith("```"):
            text_content = text_content[:-3]
        text_content = text_content.strip()

        if text_content:
            data = json.loads(text_content)
            return DidiAskResponse.model_validate(data)

        # Fallback to direct text if JSON parsing fails
        fallback_text = getattr(response, "text", "") or ""
        if fallback_text:
            return DidiAskResponse(
                spoken_answer=fallback_text.strip(),
                subtitle_text=fallback_text.strip(),
            )

        raise ValueError("Empty response from Ask-Didi API")

    def analyze_screen(
        self,
        image_bytes: bytes,
        scheme_id: Optional[str] = None,
        current_step: Optional[str] = None,
        language: str = "hi-IN",
    ) -> ScreenAnalysisResponse:
        """Analyze a screen image using Gemini visual grounding or fallback mock.

        Args:
            image_bytes: Raw JPEG/PNG image bytes of the mobile screen.
            scheme_id: Optional identifier of the active scheme.
            current_step: Optional current step in workflow.
            language: Indic language code (e.g. 'hi-IN', 'ta-IN').

        Returns:
            ScreenAnalysisResponse with normalized box_2d, spoken guidance, and CAPTCHA data.
        """
        norm_lang = normalize_language(language)

        if not self.is_live() or not self._client:
            return self._mock_analyze_screen(
                image_bytes=image_bytes,
                scheme_id=scheme_id,
                current_step=current_step,
                language=norm_lang,
            )

        try:
            prompt_text = build_screen_analysis_prompt(scheme_id, current_step, norm_lang)
            system_instruction = build_screen_system_instruction(norm_lang)

            contents = [
                types.Part.from_bytes(data=image_bytes, mime_type="image/jpeg"),
                prompt_text,
            ]

            config = types.GenerateContentConfig(
                system_instruction=system_instruction,
                response_mime_type="application/json",
                response_schema=ScreenAnalysisResponse,
                temperature=0.2,
            )

            response = self._client.models.generate_content(
                model=self.model_name,
                contents=contents,
                config=config,
            )

            parsed = self._parse_screen_response(response, norm_lang)
            return self._sanitize_and_validate_screen_response(parsed, norm_lang)
        except Exception as e:
            logger.warning(
                f"Live Gemini screen analysis failed with error: {e}. Falling back to deterministic mock."
            )
            return self._mock_analyze_screen(
                image_bytes=image_bytes,
                scheme_id=scheme_id,
                current_step=current_step,
                language=norm_lang,
            )

    def ask_didi(
        self,
        user_query: str,
        language: str = "hi-IN",
        screen_image: Optional[bytes] = None,
    ) -> DidiAskResponse:
        """Provide empathetic conversational assistance in Didi's elder-sister persona.

        Args:
            user_query: The question asked by the rural citizen.
            language: Indic language code.
            screen_image: Optional screenshot bytes for multimodal visual context.

        Returns:
            DidiAskResponse with spoken answer and subtitle.
        """
        norm_lang = normalize_language(language)

        if not self.is_live() or not self._client:
            return self._mock_ask_didi(
                user_query=user_query,
                language=norm_lang,
                screen_image=screen_image,
            )

        try:
            system_instruction = build_didi_system_instruction(norm_lang)
            prompt_text = build_didi_ask_prompt(user_query, norm_lang)

            contents = []
            if screen_image:
                contents.append(types.Part.from_bytes(data=screen_image, mime_type="image/jpeg"))
            contents.append(prompt_text)

            config = types.GenerateContentConfig(
                system_instruction=system_instruction,
                response_mime_type="application/json",
                response_schema=DidiAskResponse,
                temperature=0.3,
            )

            response = self._client.models.generate_content(
                model=self.model_name,
                contents=contents,
                config=config,
            )

            return self._parse_didi_response(response, norm_lang)
        except Exception as e:
            logger.warning(
                f"Live Ask-Didi failed with error: {e}. Falling back to deterministic mock."
            )
            return self._mock_ask_didi(
                user_query=user_query,
                language=norm_lang,
                screen_image=screen_image,
            )


# Default singleton instance
gemini_engine = GeminiGroundingEngine()


def analyze_screen(
    image_bytes: bytes,
    scheme_id: Optional[str] = None,
    current_step: Optional[str] = None,
    language: str = "hi-IN",
) -> ScreenAnalysisResponse:
    """Analyze a screen image using the default GeminiGroundingEngine."""
    return gemini_engine.analyze_screen(
        image_bytes=image_bytes,
        scheme_id=scheme_id,
        current_step=current_step,
        language=language,
    )


def ask_didi(
    user_query: str,
    language: str = "hi-IN",
    screen_image: Optional[bytes] = None,
) -> DidiAskResponse:
    """Ask Didi a question using the default GeminiGroundingEngine."""
    return gemini_engine.ask_didi(
        user_query=user_query,
        language=language,
        screen_image=screen_image,
    )
