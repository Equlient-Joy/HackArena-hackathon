"""Intent recognition and scheme catalog endpoints."""

from typing import List
from fastapi import APIRouter, Query

from app.schemas.intent import IntentRequest, IntentResponse
from app.services.scheme_catalog import SchemeItem, scheme_catalog

router = APIRouter(tags=["intent"])

GENERAL_ASSISTANCE_DATA = {
    "hi": {
        "scheme_id": "general_assistance",
        "scheme_name": "जन सेवा पोर्टल खोज",
        "portal_url": "https://services.india.gov.in",
        "confirmation_speech": "मैं आपको राष्ट्रीय जन सेवा पोर्टल पर ले जा रही हूँ, जहाँ आप अपनी आवश्यकतानुसार सरकारी योजना खोज सकते हैं।",
        "initial_prompt": "आप किस सरकारी योजना या सहायता के बारे में जानना चाहते हैं? कृपया बोलकर या लिखकर बताएं।",
    },
    "en": {
        "scheme_id": "general_assistance",
        "scheme_name": "Public Service Portal Search",
        "portal_url": "https://services.india.gov.in",
        "confirmation_speech": "Opening the national public service portal. I will help you search for the right scheme or service.",
        "initial_prompt": "Which government scheme or service would you like help with? Please speak or enter your query.",
    },
}


def _get_general_assistance_response(language: str = "hi-IN") -> IntentResponse:
    """Build a fallback IntentResponse directing the user to the national public services portal."""
    lang_key = "en" if language and language.lower().startswith("en") else "hi"
    data = GENERAL_ASSISTANCE_DATA[lang_key]
    return IntentResponse(
        scheme_id=data["scheme_id"],
        scheme_name=data["scheme_name"],
        portal_url=data["portal_url"],
        confirmation_speech=data["confirmation_speech"],
        initial_prompt=data["initial_prompt"],
    )


@router.post("/intent", response_model=IntentResponse)
def match_intent(request: IntentRequest) -> IntentResponse:
    """Match citizen voice/text query to a known welfare scheme or general assistance."""
    matched = scheme_catalog.match_scheme(query=request.query, language=request.language)
    if matched is not None:
        return matched.to_intent_response()

    return _get_general_assistance_response(language=request.language)


@router.get("/schemes", response_model=List[SchemeItem])
def get_schemes(
    language: str = Query(default="hi-IN", description="Language code (e.g., hi-IN, en-IN)"),
) -> List[SchemeItem]:
    """Return all available welfare schemes localized to the specified language."""
    return scheme_catalog.get_all_schemes(language=language)
