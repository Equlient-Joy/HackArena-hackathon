"""Rural welfare scheme catalog service for Sahaayika (सहायिका).

Provides scheme matching, multi-lingual information, and conversational Indic prompts
tailored for rural Indian citizens.
"""

from __future__ import annotations

import re
from typing import Dict, List, Optional
from pydantic import BaseModel, Field

from app.schemas.intent import IntentResponse


class SchemeItem(BaseModel):
    """Catalog item representing a government welfare scheme."""

    id: str = Field(..., description="Unique scheme ID")
    name: str = Field(..., description="Localized scheme name")
    portal_url: str = Field(..., description="Official government web portal URL")
    keywords: List[str] = Field(default_factory=list, description="Keywords for intent matching")
    confirmation_speech: str = Field(..., description="Warm Indic voice confirmation message")
    initial_prompt: str = Field(..., description="First instruction spoken to user")
    description: str = Field(default="", description="Brief description of the scheme")

    def to_intent_response(self) -> IntentResponse:
        """Convert scheme item into an IntentResponse schema."""
        return IntentResponse(
            scheme_id=self.id,
            scheme_name=self.name,
            portal_url=self.portal_url,
            confirmation_speech=self.confirmation_speech,
            initial_prompt=self.initial_prompt,
        )


# Raw catalog definitions
SCHEME_DEFINITIONS: Dict[str, dict] = {
    "ladli_behna": {
        "id": "ladli_behna",
        "portal_url": "https://cmladlibehna.mp.gov.in",
        "keywords": [
            "लाडली बहना",
            "ladli behna",
            "ladli",
            "लाडली",
            "लाड़ली",
            "बहना",
            "बहन",
            "behna",
            "ladli behna yojana",
        ],
        "names": {
            "hi": "मुख्यमंत्री लाडली बहना योजना",
            "en": "Mukhyamantri Ladli Behna Yojana",
        },
        "confirmation_speech": {
            "hi": "लाडली बहना योजना का पोर्टल खोला जा रहा है। मैं आपको फॉर्म भरने और स्थिति देखने में पूरी मदद करूंगी।",
            "en": "Opening the Ladli Behna Yojana portal. I will guide you step-by-step through the process.",
        },
        "initial_prompt": {
            "hi": "लाडली बहना योजना में आवेदन या स्थिति देखने के लिए समग्र आईडी या आवेदन क्रमांक दर्ज करें।",
            "en": "Please enter your Samagra ID or application number to check Ladli Behna status.",
        },
        "description": "Financial assistance program empowering rural women with direct monthly benefit transfers.",
    },
    "pm_kisan": {
        "id": "pm_kisan",
        "portal_url": "https://pmkisan.gov.in",
        "keywords": [
            "पीएम किसान",
            "pm kisan",
            "kisan",
            "किसान",
            "सम्मान निधि",
            "pmkisan",
            "kisan samman",
            "pm-kisan",
            "किस्त",
        ],
        "names": {
            "hi": "पीएम-किसान सम्मान निधि",
            "en": "PM-Kisan Samman Nidhi",
        },
        "confirmation_speech": {
            "hi": "पीएम किसान सम्मान निधि का पोर्टल खोला जा रहा है। आइए आपकी किस्त की स्थिति देखते हैं।",
            "en": "Opening the PM-Kisan portal. Let's check your installment and beneficiary status.",
        },
        "initial_prompt": {
            "hi": "पीएम-किसान सम्मान निधि का स्टेटस देखने के लिए अपना आधार नंबर या रजिस्ट्रेशन नंबर दर्ज करें।",
            "en": "Please enter your Aadhaar number or registration number to view your PM-Kisan status.",
        },
        "description": "Central sector scheme providing income support of Rs. 6000 per year to small and marginal farmers.",
    },
    "ration_card": {
        "id": "ration_card",
        "portal_url": "https://nfsa.gov.in",
        "keywords": [
            "राशन",
            "ration",
            "खाद्य",
            "राशन कार्ड",
            "ration card",
            "nfsa",
            "अनाज",
            "कोटा",
            "खाद्यान्न",
        ],
        "names": {
            "hi": "राष्ट्रीय खाद्य सुरक्षा (राशन कार्ड)",
            "en": "National Food Security Portal (Ration Card)",
        },
        "confirmation_speech": {
            "hi": "राशन कार्ड का पोर्टल खोला जा रहा है। मैं आपको राशन कार्ड की पात्रता और जानकारी देखने में मदद करूंगी।",
            "en": "Opening the Ration Card portal. I will help you find your ration card and entitlement details.",
        },
        "initial_prompt": {
            "hi": "राशन कार्ड की पात्रता या सूची देखने के लिए अपना राशन कार्ड नंबर या जिला चुनें।",
            "en": "Please select your state or enter your ration card number to proceed.",
        },
        "description": "Subsidized food grain distribution under the National Food Security Act (NFSA).",
    },
    "ayushman_bharat": {
        "id": "ayushman_bharat",
        "portal_url": "https://beneficiary.nha.gov.in",
        "keywords": [
            "आयुष्मान",
            "ayushman",
            "इलाज",
            "गोल्डन कार्ड",
            "ayushman bharat",
            "pmjay",
            "हेल्थ कार्ड",
            "जन आरोग्य",
            "golden card",
        ],
        "names": {
            "hi": "आयुष्मान भारत (जन आरोग्य)",
            "en": "Ayushman Bharat (PM-JAY)",
        },
        "confirmation_speech": {
            "hi": "आयुष्मान भारत का पोर्टल खोला जा रहा है। आइए 5 लाख रुपये तक के मुफ्त इलाज के कार्ड की स्थिति जांचते हैं।",
            "en": "Opening the Ayushman Bharat portal. Let's check your free healthcare card eligibility.",
        },
        "initial_prompt": {
            "hi": "आयुष्मान कार्ड बनाने या खोजने के लिए आधार या राशन कार्ड नंबर दर्ज करें।",
            "en": "Please enter your Aadhaar or Ration Card number to search for your Ayushman Card.",
        },
        "description": "Health coverage of up to 5 lakh rupees per family per year for secondary and tertiary care hospitalization.",
    },
    "janani_suraksha": {
        "id": "janani_suraksha",
        "portal_url": "https://nhm.gov.in",
        "keywords": [
            "जननी",
            "janani",
            "मातृत्व",
            "प्रसूति",
            "janani suraksha",
            "गर्भवती",
            "डिलीवरी",
            "jsy",
        ],
        "names": {
            "hi": "जननी सुरक्षा योजना",
            "en": "Janani Suraksha Yojana",
        },
        "confirmation_speech": {
            "hi": "जननी सुरक्षा योजना का पोर्टल खोला जा रहा है। गर्भवती माताओं की आर्थिक सहायता और प्रसूति सुविधा की जानकारी देखते हैं।",
            "en": "Opening the Janani Suraksha Yojana portal for institutional delivery and maternity support.",
        },
        "initial_prompt": {
            "hi": "जननी सुरक्षा योजना के मातृत्व लाभ की जानकारी देखने के लिए आगे बढ़ें।",
            "en": "Let's explore maternity benefits and hospital incentive registration under Janani Suraksha Yojana.",
        },
        "description": "Safe motherhood intervention promoting institutional delivery among poor pregnant women.",
    },
    "pension": {
        "id": "pension",
        "portal_url": "https://nsap.nic.in",
        "keywords": [
            "पेंशन",
            "pension",
            "वृद्धा",
            "विधवा",
            "दिव्यांग",
            "nsap",
            "vridha",
            "vidhwa",
            "बुजुर्ग",
        ],
        "names": {
            "hi": "राष्ट्रीय सामाजिक सहायता (पेंशन)",
            "en": "National Social Assistance Programme (Pension)",
        },
        "confirmation_speech": {
            "hi": "पेंशन योजना का पोर्टल खोला जा रहा है। आइए आपकी वृद्धा, विधवा या दिव्यांग पेंशन की स्थिति जांचते हैं।",
            "en": "Opening the National Pension portal. Let's check your pension payment and beneficiary status.",
        },
        "initial_prompt": {
            "hi": "वृद्धावस्था या विधवा पेंशन की स्थिति देखने के लिए आवेदन क्रमांक या खाता संख्या दर्ज करें।",
            "en": "Please enter your application number or bank account number to track pension status.",
        },
        "description": "Social security welfare pensions for senior citizens, widows, and persons with disabilities.",
    },
}


class SchemeCatalogService:
    """Service to query and match government welfare schemes."""

    def __init__(self, catalog: Dict[str, dict] | None = None) -> None:
        self.catalog = catalog or SCHEME_DEFINITIONS

    def _normalize_lang(self, language: str) -> str:
        """Normalize language tag (e.g. 'hi-IN' -> 'hi', 'en-US' -> 'en')."""
        if not language:
            return "hi"
        lang_lower = language.lower()
        if lang_lower.startswith("en"):
            return "en"
        return "hi"

    def _build_scheme_item(self, raw_data: dict, language: str = "hi-IN") -> SchemeItem:
        """Construct a localized SchemeItem from definition dict."""
        lang_key = self._normalize_lang(language)
        name = raw_data["names"].get(lang_key, raw_data["names"].get("hi", ""))
        conf_speech = raw_data["confirmation_speech"].get(
            lang_key, raw_data["confirmation_speech"].get("hi", "")
        )
        init_prompt = raw_data["initial_prompt"].get(
            lang_key, raw_data["initial_prompt"].get("hi", "")
        )

        return SchemeItem(
            id=raw_data["id"],
            name=name,
            portal_url=raw_data["portal_url"],
            keywords=raw_data["keywords"],
            confirmation_speech=conf_speech,
            initial_prompt=init_prompt,
            description=raw_data.get("description", ""),
        )

    def _keyword_matches(self, keyword: str, text: str) -> bool:
        """Check if keyword matches within query text."""
        kw = keyword.lower().strip()
        if not kw:
            return False

        # If keyword consists of ASCII letters/numbers/spaces, use word boundaries
        if all(ord(c) < 128 for c in kw):
            pattern = r"(?<!\w)" + re.escape(kw) + r"(?!\w)"
            return bool(re.search(pattern, text, re.IGNORECASE))
        # For Devanagari and non-ASCII, substring match provides natural flex
        return kw in text

    def match_scheme(self, query: str, language: str = "hi-IN") -> Optional[SchemeItem]:
        """Match a user query against scheme keywords.

        Args:
            query: User's voice transcript or text query.
            language: Language code (e.g., 'hi-IN', 'en-IN').

        Returns:
            SchemeItem if a match is found, or None if no match.
        """
        if not query or not query.strip():
            return None

        normalized_query = query.strip().lower()

        best_scheme_id: Optional[str] = None
        best_score = 0
        best_longest_kw = 0

        for scheme_id, data in self.catalog.items():
            matched_keywords = [
                kw for kw in data["keywords"]
                if self._keyword_matches(kw, normalized_query)
            ]
            if matched_keywords:
                score = len(matched_keywords)
                longest_kw = max(len(kw) for kw in matched_keywords)
                # Prioritize schemes with longer matched keywords, then count
                if (longest_kw > best_longest_kw) or (
                    longest_kw == best_longest_kw and score > best_score
                ):
                    best_score = score
                    best_longest_kw = longest_kw
                    best_scheme_id = scheme_id

        if best_scheme_id is not None:
            return self._build_scheme_item(self.catalog[best_scheme_id], language)

        return None

    def get_all_schemes(self, language: str = "hi-IN") -> List[SchemeItem]:
        """Retrieve all registered welfare schemes localized to the requested language.

        Args:
            language: Language code (e.g., 'hi-IN', 'en-IN').

        Returns:
            List of localized SchemeItem objects.
        """
        return [
            self._build_scheme_item(data, language)
            for data in self.catalog.values()
        ]

    def get_scheme_by_id(self, scheme_id: str, language: str = "hi-IN") -> Optional[SchemeItem]:
        """Retrieve a specific scheme by ID.

        Args:
            scheme_id: Unique scheme identifier.
            language: Language code.

        Returns:
            Localized SchemeItem if found, else None.
        """
        if scheme_id in self.catalog:
            return self._build_scheme_item(self.catalog[scheme_id], language)
        return None


# Global service instance
scheme_catalog = SchemeCatalogService()
