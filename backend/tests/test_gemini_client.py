"""Unit tests for Sahaayika Gemini Grounding Engine, CAPTCHA Reader, and Prompt Engine."""

from __future__ import annotations

import io
from unittest.mock import MagicMock, patch

import pytest
from PIL import Image, ImageDraw

from app.config import settings
from app.schemas.screen import DidiAskResponse, ScreenAnalysisResponse
from app.services.gemini_client import (
    GeminiGroundingEngine,
    analyze_screen,
    ask_didi,
    clamp_coordinates,
)
from app.services.prompt_engine import (
    AADHAAR_PROMPTS,
    CAPTCHA_TEMPLATES,
    PROHIBITED_TECH_TERMS,
    SAMAGRA_PROMPTS,
    SUPPORTED_LANGUAGES,
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


@pytest.fixture
def synthetic_screen_bytes() -> bytes:
    """Create a synthetic mobile screen image for testing."""
    img = Image.new("RGB", (720, 1280), color=(245, 247, 250))
    draw = ImageDraw.Draw(img)

    # Draw header bar
    draw.rectangle([0, 0, 720, 100], fill=(26, 75, 140))
    # Draw a simulated text input box
    draw.rectangle([60, 300, 660, 420], outline=(180, 180, 180), fill=(255, 255, 255), width=2)
    # Draw simulated CAPTCHA box
    draw.rectangle([200, 500, 520, 600], outline=(200, 100, 100), fill=(240, 240, 240), width=2)
    # Draw simulated primary action button
    draw.rectangle([100, 800, 620, 920], fill=(30, 130, 60))

    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=85)
    return buf.getvalue()


# ============================================================================
# 1. PROMPT ENGINE TESTS
# ============================================================================


class TestPromptEngine:
    """Tests for prompt engine persona, multi-language support, and guardrails."""

    def test_supported_languages_presence(self):
        """Verify all 6 required Indic & English languages are supported."""
        required = ["hi-IN", "ta-IN", "bn-IN", "te-IN", "mr-IN", "en-IN"]
        for code in required:
            assert code in SUPPORTED_LANGUAGES
            assert SUPPORTED_LANGUAGES[code]["native_name"]
            assert SUPPORTED_LANGUAGES[code]["persona_title"]

    def test_normalize_language(self):
        """Test normalization of various language formats."""
        assert normalize_language("hi") == "hi-IN"
        assert normalize_language("HI-IN") == "hi-IN"
        assert normalize_language("ta_IN") == "ta-IN"
        assert normalize_language("bn") == "bn-IN"
        assert normalize_language("te") == "te-IN"
        assert normalize_language("mr") == "mr-IN"
        assert normalize_language("en") == "en-IN"
        assert normalize_language("unknown_lang") == "hi-IN"  # fallback
        assert normalize_language(None) == "hi-IN"

    def test_screen_system_instruction_persona(self):
        """Verify system instruction contains elder-sister persona and strict rules."""
        for lang_code in ["hi-IN", "ta-IN", "bn-IN", "te-IN", "mr-IN", "en-IN"]:
            instr = build_screen_system_instruction(lang_code)
            assert "दीदी" in instr or "Didi" in instr
            assert "0 to 1000" in instr or "0-1000" in instr
            assert "MAXIMUM 10-12 words" in instr
            # Ensure prohibition instructions are explicitly in the system instruction
            assert "NEVER use technical computer terms" in instr
            assert "CAPTCHA" in instr

    def test_screen_analysis_prompt_generation(self):
        """Verify screen analysis prompt incorporates scheme, step, and language."""
        prompt = build_screen_analysis_prompt(
            scheme_id="ladli_behna",
            current_step="aadhaar_entry",
            language="hi-IN",
        )
        assert "ladli_behna" in prompt
        assert "aadhaar_entry" in prompt
        assert "Hindi" in prompt
        assert "0-1000" in prompt

    def test_captcha_guidance_formatting(self):
        """Verify exact CAPTCHA voice guidance across languages."""
        assert format_captcha_guidance("4x7k", "hi-IN") == "कैप्चा कोड है 4X7K।"
        assert format_captcha_guidance("ab89", "en-IN") == "The captcha code is AB89."
        assert "4X7K" in format_captcha_guidance("4X7K", "ta-IN")
        assert "4X7K" in format_captcha_guidance("4X7K", "bn-IN")
        assert "4X7K" in format_captcha_guidance("4X7K", "te-IN")
        assert "4X7K" in format_captcha_guidance("4X7K", "mr-IN")

    def test_id_guidance_formatting(self):
        """Verify respectful Aadhaar and Samagra ID consent prompts."""
        # Aadhaar
        assert format_id_guidance("Aadhaar", "hi-IN") == "दीदी, क्या मैं आपका आधार नंबर भर दूँ?"
        assert "Aadhaar" in format_id_guidance("Aadhaar", "en-IN")
        assert format_id_guidance("aadhaar_number", "mr-IN") == "दीदी, मी तुमचा आधार क्रमांक भरू का?"

        # Samagra ID
        assert format_id_guidance("Samagra", "hi-IN") == "दीदी, क्या मैं आपकी समग्र आईडी भर दूँ?"
        assert "Samagra" in format_id_guidance("samagra_id", "en-IN")
        assert format_id_guidance("samagra", "bn-IN") == "দিদি, আমি কি আপনার সমগ্র আইডি পূরণ করব?"

    def test_prohibited_terms_detection(self):
        """Test detection of banned technical computer terms."""
        forbidden_phrases = [
            "Please click the submit button",
            "इस लिंक पर क्लिक करें",
            "ब्राउज़र में नया URL खोलें",
            "सबमिट बटन दबाएं",
            "ड्रॉपडाउन मेनू चुनें",
        ]
        for phrase in forbidden_phrases:
            assert contains_prohibited_terms(phrase) is True

        clean_phrases = [
            "नीचे दिए गए नीले बटन को छुएं।",
            "पीले घेरे में दबाएं।",
            "लाल बक्से के अंदर लिखें।",
            "दीदी, क्या मैं आपका आधार नंबर भर दूँ?",
            "कैप्चा कोड है 4X7K।",
        ]
        for phrase in clean_phrases:
            assert contains_prohibited_terms(phrase) is False

    def test_word_count_validation(self):
        """Verify max word limit (10-12 words) validation."""
        short_text = "नीचे दिए गए नीले बटन को छुएं।"
        valid, issues = validate_guidance_text(short_text, max_words=12)
        assert valid is True
        assert len(issues) == 0

        long_text = "दीदी आप कृपया स्क्रीन के सबसे नीचे जाकर वहां दिखने वाले बड़े नीले बटन को ध्यान से देखें और फिर अपनी उंगली से उस पर दबाएं ताकि फॉर्म आगे बढ़ सके"
        valid, issues = validate_guidance_text(long_text, max_words=12)
        assert valid is False
        assert any("exceeds max" in i for i in issues)

    def test_didi_ask_prompt_and_instruction(self):
        """Verify Ask-Didi prompt generation and system instructions."""
        didi_sys = build_didi_system_instruction("hi-IN")
        assert "दीदी" in didi_sys
        assert "Never use technical jargon" in didi_sys

        didi_p = build_didi_ask_prompt("मेरा पैसा कब आएगा?", "hi-IN")
        assert "मेरा पैसा कब आएगा?" in didi_p


# ============================================================================
# 2. GEMINI GROUNDING ENGINE TESTS (MOCK / OFFLINE MODE)
# ============================================================================


class TestGeminiGroundingEngineOffline:
    """Tests for GeminiGroundingEngine in deterministic mock mode."""

    def test_analyze_screen_general_navigation(self, synthetic_screen_bytes):
        """Verify analyze_screen returns normalized coordinates and valid guidance."""
        engine = GeminiGroundingEngine(force_mock=True)
        res = engine.analyze_screen(
            image_bytes=synthetic_screen_bytes,
            scheme_id="pm_kisan",
            current_step="landing",
            language="hi-IN",
        )

        assert isinstance(res, ScreenAnalysisResponse)
        assert res.action == "HIGHLIGHT"
        assert res.field_type == "BUTTON"
        assert len(res.box_2d) == 4

        # Verify coordinate normalization (0-1000)
        ymin, xmin, ymax, xmax = res.box_2d
        assert 0 <= ymin <= ymax <= 1000
        assert 0 <= xmin <= xmax <= 1000

        # Verify spoken guidance
        assert res.spoken_guidance
        assert res.subtitle_text == res.spoken_guidance
        assert not contains_prohibited_terms(res.spoken_guidance)
        valid, _ = validate_guidance_text(res.spoken_guidance, max_words=12)
        assert valid is True

    def test_analyze_screen_all_languages(self, synthetic_screen_bytes):
        """Verify screen guidance works across all 6 supported languages."""
        engine = GeminiGroundingEngine(force_mock=True)
        for lang in ["hi-IN", "ta-IN", "bn-IN", "te-IN", "mr-IN", "en-IN"]:
            res = engine.analyze_screen(
                image_bytes=synthetic_screen_bytes,
                scheme_id="pm_kisan",
                current_step="start",
                language=lang,
            )
            assert isinstance(res, ScreenAnalysisResponse)
            assert res.spoken_guidance
            assert not contains_prohibited_terms(res.spoken_guidance)

    def test_analyze_screen_captcha_step(self, synthetic_screen_bytes):
        """Verify CAPTCHA detection, transcription, and spoken guidance."""
        engine = GeminiGroundingEngine(force_mock=True)
        res = engine.analyze_screen(
            image_bytes=synthetic_screen_bytes,
            scheme_id="ladli_behna",
            current_step="captcha_verification",
            language="hi-IN",
        )

        assert res.field_type == "CAPTCHA"
        assert res.captcha_code == "4X7K"
        assert res.spoken_guidance == "कैप्चा कोड है 4X7K।"
        assert res.subtitle_text == "कैप्चा कोड है 4X7K।"
        assert len(res.box_2d) == 4
        assert all(0 <= c <= 1000 for c in res.box_2d)

    def test_analyze_screen_aadhaar_step(self, synthetic_screen_bytes):
        """Verify Aadhaar input field detection and respectful prompt."""
        engine = GeminiGroundingEngine(force_mock=True)
        res = engine.analyze_screen(
            image_bytes=synthetic_screen_bytes,
            scheme_id="ladli_behna",
            current_step="enter_aadhaar_number",
            language="hi-IN",
        )

        assert res.field_type == "INPUT_TEXT"
        assert res.captcha_code is None
        assert res.spoken_guidance == "दीदी, क्या मैं आपका आधार नंबर भर दूँ?"
        assert len(res.box_2d) == 4

    def test_analyze_screen_samagra_step(self, synthetic_screen_bytes):
        """Verify Samagra ID detection and respectful prompt."""
        engine = GeminiGroundingEngine(force_mock=True)
        res = engine.analyze_screen(
            image_bytes=synthetic_screen_bytes,
            scheme_id="ladli_behna",
            current_step="enter_samagra_id",
            language="hi-IN",
        )

        assert res.field_type == "INPUT_TEXT"
        assert res.spoken_guidance == "दीदी, क्या मैं आपकी समग्र आईडी भर दूँ?"

    def test_analyze_screen_success_step(self, synthetic_screen_bytes):
        """Verify successful completion status and message."""
        engine = GeminiGroundingEngine(force_mock=True)
        res = engine.analyze_screen(
            image_bytes=synthetic_screen_bytes,
            scheme_id="pm_kisan",
            current_step="completed_success",
            language="hi-IN",
        )

        assert res.action == "SUCCESS"
        assert res.status == "SUCCESS"
        assert res.box_2d == []
        assert "बधाई" in res.spoken_guidance or "पूरा" in res.spoken_guidance

    def test_ask_didi_questions(self, synthetic_screen_bytes):
        """Verify Ask-Didi conversational answers in mock mode."""
        engine = GeminiGroundingEngine(force_mock=True)

        # Question about what to do
        q1 = engine.ask_didi("दीदी, मुझे क्या करना है?", language="hi-IN")
        assert isinstance(q1, DidiAskResponse)
        assert "दीदी" in q1.spoken_answer
        assert not contains_prohibited_terms(q1.spoken_answer)

        # Question about Aadhaar
        q2 = engine.ask_didi("आधार कार्ड का क्या करूँ?", language="hi-IN")
        assert "आधार" in q2.spoken_answer

        # Question about CAPTCHA
        q3 = engine.ask_didi("कैप्चा कोड समझ नहीं आ रहा", language="hi-IN")
        assert "कोड" in q3.spoken_answer or "कैप्चा" in q3.spoken_answer

        # Multi-language ask didi
        q_en = engine.ask_didi("What should I do now?", language="en-IN")
        assert "Didi" in q_en.spoken_answer

    def test_convenience_functions(self, synthetic_screen_bytes):
        """Verify module-level analyze_screen and ask_didi functions."""
        res = analyze_screen(
            image_bytes=synthetic_screen_bytes,
            scheme_id="pm_kisan",
            current_step="test",
            language="hi-IN",
        )
        assert isinstance(res, ScreenAnalysisResponse)

        didi_res = ask_didi("दीदी क्या करूं?", language="hi-IN")
        assert isinstance(didi_res, DidiAskResponse)

    def test_coordinate_clamping(self):
        """Verify coordinate clamping function bounds to [0, 1000] and orders min/max."""
        assert clamp_coordinates([]) == []
        assert clamp_coordinates([10, 20]) == []
        assert clamp_coordinates([-50, 1200, 500, 300]) == [0, 300, 500, 1000]
        assert clamp_coordinates([700, 200, 100, 800]) == [100, 200, 700, 800]


# ============================================================================
# 3. GEMINI MODEL CONFIGURATION & LIVE API MOCK TESTS
# ============================================================================


class TestGeminiModelConfigurationAndLiveMocking:
    """Test model configuration (gemini-2.5-flash / gemini-3.8-flash) and live API invocation."""

    def test_model_configuration_defaults_and_override(self):
        """Verify default model is gemini-2.5-flash and gemini-3.8-flash is supported."""
        engine_default = GeminiGroundingEngine(force_mock=True)
        assert engine_default.model_name == settings.GEMINI_MODEL

        engine_38 = GeminiGroundingEngine(model_name="gemini-3.8-flash", force_mock=True)
        assert engine_38.model_name == "gemini-3.8-flash"

        engine_25 = GeminiGroundingEngine(model_name="gemini-2.5-flash", force_mock=True)
        assert engine_25.model_name == "gemini-2.5-flash"

    @patch("google.genai.Client")
    def test_live_analyze_screen_structured_output_flow(self, mock_client_cls, synthetic_screen_bytes):
        """Verify live screen analysis makes correct genai call with structured output config."""
        mock_client = MagicMock()
        mock_client_cls.return_value = mock_client

        # Mock generate_content response
        mock_resp = MagicMock()
        mock_resp.parsed = ScreenAnalysisResponse(
            action="HIGHLIGHT",
            box_2d=[400, 100, 480, 600],
            spoken_guidance="नीचे दिए गए नीले बटन को छुएं।",
            subtitle_text="नीचे दिए गए नीले बटन को छुएं।",
            field_type="BUTTON",
            captcha_code=None,
            status="IN_PROGRESS",
        )
        mock_resp.text = mock_resp.parsed.model_dump_json()
        mock_client.models.generate_content.return_value = mock_resp

        engine = GeminiGroundingEngine(
            api_key="test_dummy_key_12345",
            model_name="gemini-3.8-flash",
            force_mock=False,
        )

        res = engine.analyze_screen(
            image_bytes=synthetic_screen_bytes,
            scheme_id="pm_kisan",
            current_step="step_1",
            language="hi-IN",
        )

        assert isinstance(res, ScreenAnalysisResponse)
        assert res.action == "HIGHLIGHT"
        assert res.box_2d == [400, 100, 480, 600]
        assert mock_client.models.generate_content.called

        # Inspect call kwargs
        call_kwargs = mock_client.models.generate_content.call_args.kwargs
        assert call_kwargs["model"] == "gemini-3.8-flash"
        assert call_kwargs["config"].response_mime_type == "application/json"
        assert call_kwargs["config"].response_schema == ScreenAnalysisResponse

    @patch("google.genai.Client")
    def test_live_ask_didi_structured_output_flow(self, mock_client_cls):
        """Verify live Ask-Didi call with structured DidiAskResponse."""
        mock_client = MagicMock()
        mock_client_cls.return_value = mock_client

        mock_resp = MagicMock()
        mock_resp.parsed = DidiAskResponse(
            spoken_answer="दीदी, चिंता न करें। मैं आपकी पूरी मदद करूंगी।",
            subtitle_text="दीदी, चिंता न करें। मैं आपकी पूरी मदद करूंगी।",
        )
        mock_resp.text = mock_resp.parsed.model_dump_json()
        mock_client.models.generate_content.return_value = mock_resp

        engine = GeminiGroundingEngine(
            api_key="test_dummy_key_12345",
            model_name="gemini-2.5-flash",
            force_mock=False,
        )

        res = engine.ask_didi("दीदी, फॉर्म कैसे भरें?", language="hi-IN")
        assert isinstance(res, DidiAskResponse)
        assert "मदद" in res.spoken_answer
        assert mock_client.models.generate_content.called

    @patch("google.genai.Client")
    def test_live_api_exception_fallback(self, mock_client_cls, synthetic_screen_bytes):
        """Verify graceful fallback to mock response when Gemini API raises an error."""
        mock_client = MagicMock()
        mock_client_cls.return_value = mock_client
        mock_client.models.generate_content.side_effect = RuntimeError("API connection timeout")

        engine = GeminiGroundingEngine(
            api_key="test_dummy_key_12345",
            model_name="gemini-2.5-flash",
            force_mock=False,
        )

        # Should NOT raise, but return fallback mock response
        res = engine.analyze_screen(
            image_bytes=synthetic_screen_bytes,
            scheme_id="pm_kisan",
            current_step="landing",
            language="hi-IN",
        )
        assert isinstance(res, ScreenAnalysisResponse)
        assert res.action == "HIGHLIGHT"
        assert len(res.box_2d) == 4

        didi_res = engine.ask_didi("दीदी क्या करना है?", language="hi-IN")
        assert isinstance(didi_res, DidiAskResponse)
        assert didi_res.spoken_answer
