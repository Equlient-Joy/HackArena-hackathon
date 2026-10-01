"""Unit tests for SchemeCatalogService, Configuration, and Schemas."""

import pytest
from app.config import Settings, get_settings
from app.schemas.intent import IntentRequest, IntentResponse
from app.schemas.screen import ScreenAnalysisResponse, DidiAskRequest, DidiAskResponse
from app.services.scheme_catalog import SchemeCatalogService, scheme_catalog


class TestSchemeCatalog:
    """Test suite for scheme catalog lookup and matching."""

    @pytest.mark.parametrize(
        "query, expected_id",
        [
            ("लाडली बहना", "ladli_behna"),
            ("लाडली बहना योजना फॉर्म भरना है", "ladli_behna"),
            ("मुझे बहना योजना में पैसा मिला या नहीं देखना है", "ladli_behna"),
            ("राशन देखना है", "ration_card"),
            ("नया राशन कार्ड बनवाना है", "ration_card"),
            ("आयुष्मान भारत कार्ड कैसे बनाएं", "ayushman_bharat"),
            ("गोल्डन कार्ड निकालना है", "ayushman_bharat"),
            ("जननी सुरक्षा योजना", "janani_suraksha"),
            ("गर्भवती महिला के लिए जननी योजना", "janani_suraksha"),
            ("वृद्धा पेंशन की स्थिति बताएं", "pension"),
            ("विधवा पेंशन कब आएगी", "pension"),
            ("पीएम किसान सम्मान निधि", "pm_kisan"),
        ],
    )
    def test_match_hindi_queries(self, query: str, expected_id: str):
        scheme = scheme_catalog.match_scheme(query, language="hi-IN")
        assert scheme is not None, f"Query '{query}' failed to match any scheme"
        assert scheme.id == expected_id
        assert scheme.portal_url.startswith("https://")
        assert len(scheme.confirmation_speech) > 0
        assert len(scheme.initial_prompt) > 0

    @pytest.mark.parametrize(
        "query, expected_id",
        [
            ("pm kisan status", "pm_kisan"),
            ("pmkisan payment check", "pm_kisan"),
            ("ladli behna yojana portal", "ladli_behna"),
            ("ration card application", "ration_card"),
            ("ayushman bharat golden card", "ayushman_bharat"),
            ("janani suraksha scheme details", "janani_suraksha"),
            ("nsap pension status check", "pension"),
        ],
    )
    def test_match_english_queries(self, query: str, expected_id: str):
        scheme = scheme_catalog.match_scheme(query, language="en")
        assert scheme is not None, f"Query '{query}' failed to match any scheme"
        assert scheme.id == expected_id
        assert "Opening" in scheme.confirmation_speech or "portal" in scheme.confirmation_speech.lower()

    @pytest.mark.parametrize(
        "unrelated_query",
        [
            ("weather in Delhi"),
            ("what is the cricket score today"),
            ("play music on youtube"),
            ("1234567890"),
            ("xyz random non matching words"),
            (""),
            ("   "),
        ],
    )
    def test_fallback_unrelated_queries(self, unrelated_query: str):
        scheme = scheme_catalog.match_scheme(unrelated_query)
        assert scheme is None, f"Expected None for unrelated query '{unrelated_query}', got {scheme}"

    def test_get_all_schemes(self):
        schemes = scheme_catalog.get_all_schemes()
        assert len(schemes) == 6
        scheme_ids = {s.id for s in schemes}
        expected_ids = {
            "ladli_behna",
            "pm_kisan",
            "ration_card",
            "ayushman_bharat",
            "janani_suraksha",
            "pension",
        }
        assert scheme_ids == expected_ids
        for scheme in schemes:
            assert scheme.id
            assert scheme.name
            assert scheme.portal_url.startswith("https://")
            assert scheme.confirmation_speech
            assert scheme.initial_prompt

    def test_get_scheme_by_id(self):
        scheme = scheme_catalog.get_scheme_by_id("pm_kisan", language="hi-IN")
        assert scheme is not None
        assert scheme.id == "pm_kisan"
        assert "किसान" in scheme.name

        non_existent = scheme_catalog.get_scheme_by_id("non_existent_scheme")
        assert non_existent is None

    def test_to_intent_response(self):
        scheme = scheme_catalog.match_scheme("लाडली बहना")
        assert scheme is not None
        intent_resp = scheme.to_intent_response()
        assert isinstance(intent_resp, IntentResponse)
        assert intent_resp.scheme_id == "ladli_behna"
        assert intent_resp.portal_url == "https://cmladlibehna.mp.gov.in"
        assert "लाडली बहना" in intent_resp.scheme_name


class TestSchemasAndConfig:
    """Test suite for Pydantic schemas and configuration loading."""

    def test_config_settings_defaults(self):
        cfg = get_settings()
        assert isinstance(cfg, Settings)
        assert isinstance(cfg.PORT, int)
        assert cfg.PORT > 0
        assert isinstance(cfg.HOST, str)
        assert cfg.DEFAULT_LANGUAGE == "hi-IN"

    def test_intent_schemas(self):
        req = IntentRequest(query="राशन कार्ड देखना है")
        assert req.query == "राशन कार्ड देखना है"
        assert req.language == "hi-IN"

        resp = IntentResponse(
            scheme_id="ration_card",
            scheme_name="राष्ट्रीय खाद्य सुरक्षा (राशन कार्ड)",
            portal_url="https://nfsa.gov.in",
            confirmation_speech="राशन कार्ड का पोर्टल खोला जा रहा है।",
            initial_prompt="राशन कार्ड नंबर दर्ज करें।",
        )
        assert resp.scheme_id == "ration_card"
        assert resp.portal_url == "https://nfsa.gov.in"

    def test_screen_analysis_response_schema(self):
        resp = ScreenAnalysisResponse(
            action="HIGHLIGHT",
            box_2d=[100, 200, 300, 400],
            spoken_guidance="दीदी, यहां अपना समग्र आईडी नंबर भरें।",
            subtitle_text="समग्र आईडी नंबर भरें",
            field_type="INPUT_TEXT",
            captcha_code=None,
            status="IN_PROGRESS",
        )
        assert resp.action == "HIGHLIGHT"
        assert resp.box_2d == [100, 200, 300, 400]
        assert "समग्र आईडी" in resp.spoken_guidance
        assert resp.status == "IN_PROGRESS"

    def test_didi_ask_schemas(self):
        ask_req = DidiAskRequest(user_query="समग्र आईडी कहां मिलेगी?")
        assert ask_req.language == "hi-IN"
        assert ask_req.user_query == "समग्र आईडी कहां मिलेगी?"

        ask_resp = DidiAskResponse(
            spoken_answer="समग्र आईडी आपके राशन कार्ड या परिवार के पर्ची पर लिखी होती है।",
            subtitle_text="समग्र आईडी राशन कार्ड पर लिखी होती है।",
        )
        assert "राशन कार्ड" in ask_resp.spoken_answer
