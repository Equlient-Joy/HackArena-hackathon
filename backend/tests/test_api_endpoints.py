"""Integration tests for Sahaayika FastAPI endpoints."""

import io
import json
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.main import app

client = TestClient(app)


def _generate_synthetic_jpeg(width: int = 200, height: int = 400, color: str = "white") -> bytes:
    """Helper to generate valid JPEG image bytes in memory."""
    img = Image.new("RGB", (width, height), color=color)
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


class TestHealthEndpoint:
    """Tests for system health check endpoint."""

    def test_health_check_returns_200(self):
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data == {
            "status": "ok",
            "service": "sahaayika-brain",
            "version": "1.0.0",
        }


class TestIntentEndpoints:
    """Tests for intent recognition and scheme listing endpoints."""

    def test_post_intent_matched_ladli_behna(self):
        payload = {
            "query": "मुझे लाडली बहना योजना का फॉर्म भरना है",
            "language": "hi-IN",
        }
        response = client.post("/api/v1/intent", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["scheme_id"] == "ladli_behna"
        assert "लाडली बहना" in data["scheme_name"]
        assert "cmladlibehna.mp.gov.in" in data["portal_url"]
        assert "लाडली बहना" in data["confirmation_speech"]
        assert len(data["initial_prompt"]) > 0

    def test_post_intent_matched_pm_kisan(self):
        payload = {
            "query": "PM kisan installment status",
            "language": "en-IN",
        }
        response = client.post("/api/v1/intent", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["scheme_id"] == "pm_kisan"
        assert "PM-Kisan" in data["scheme_name"]
        assert "pmkisan.gov.in" in data["portal_url"]

    def test_post_intent_unmatched_fallback_to_general_assistance(self):
        payload = {
            "query": "कोई अज्ञात सेवा या अनजानी खोज",
            "language": "hi-IN",
        }
        response = client.post("/api/v1/intent", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["scheme_id"] == "general_assistance"
        assert "सेवा" in data["scheme_name"]
        assert "services.india.gov.in" in data["portal_url"]
        assert len(data["confirmation_speech"]) > 0

    def test_post_intent_unmatched_fallback_english(self):
        payload = {
            "query": "some completely unknown service query",
            "language": "en-IN",
        }
        response = client.post("/api/v1/intent", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["scheme_id"] == "general_assistance"
        assert "Public Service" in data["scheme_name"]
        assert "services.india.gov.in" in data["portal_url"]

    def test_get_schemes_returns_catalog(self):
        response = client.get("/api/v1/schemes?language=hi-IN")
        assert response.status_code == 200
        schemes = response.json()
        assert isinstance(schemes, list)
        assert len(schemes) >= 5

        scheme_ids = [s["id"] for s in schemes]
        assert "ladli_behna" in scheme_ids
        assert "pm_kisan" in scheme_ids
        assert "ration_card" in scheme_ids

        # Check structure of a scheme item
        ladli = next(s for s in schemes if s["id"] == "ladli_behna")
        assert ladli["portal_url"] == "https://cmladlibehna.mp.gov.in"
        assert len(ladli["confirmation_speech"]) > 0
        assert len(ladli["initial_prompt"]) > 0

    def test_get_schemes_english_localization(self):
        response = client.get("/api/v1/schemes?language=en-IN")
        assert response.status_code == 200
        schemes = response.json()
        assert len(schemes) >= 5
        pm_kisan = next(s for s in schemes if s["id"] == "pm_kisan")
        assert "PM-Kisan" in pm_kisan["name"]


class TestScreenAnalysisEndpoint:
    """Tests for /api/v1/analyze-screen endpoint."""

    def test_analyze_screen_success_with_synthetic_jpeg(self):
        jpeg_bytes = _generate_synthetic_jpeg(width=300, height=600, color="lightblue")
        response = client.post(
            "/api/v1/analyze-screen",
            files={"image": ("screen.jpg", jpeg_bytes, "image/jpeg")},
            data={
                "scheme_id": "ladli_behna",
                "current_step": "samagra_id_entry",
                "language": "hi-IN",
            },
        )
        assert response.status_code == 200
        data = response.json()

        assert "action" in data
        assert data["action"] in ["HIGHLIGHT", "SCROLL_DOWN", "WAIT", "SUCCESS"]
        assert "box_2d" in data
        assert isinstance(data["box_2d"], list)
        assert "spoken_guidance" in data
        assert len(data["spoken_guidance"]) > 0
        assert "subtitle_text" in data
        assert len(data["subtitle_text"]) > 0
        assert data["status"] in ["IN_PROGRESS", "SUCCESS", "COMPLETED"]

    def test_analyze_screen_captcha_detection(self):
        jpeg_bytes = _generate_synthetic_jpeg()
        response = client.post(
            "/api/v1/analyze-screen",
            files={"image": ("captcha_screen.jpg", jpeg_bytes, "image/jpeg")},
            data={
                "scheme_id": "ladli_behna",
                "current_step": "captcha_verification",
                "language": "hi-IN",
            },
        )
        assert response.status_code == 200
        data = response.json()
        assert data["field_type"] == "CAPTCHA"
        assert data["captcha_code"] is not None
        assert len(data["captcha_code"]) > 0
        assert len(data["box_2d"]) == 4

    def test_analyze_screen_with_field_states(self):
        jpeg_bytes = _generate_synthetic_jpeg()
        field_states_json = json.dumps([
            {"label": "Name", "is_filled": True},
            {"label": "Aadhaar", "is_filled": False},
        ])
        response = client.post(
            "/api/v1/analyze-screen",
            files={"image": ("screen.jpg", jpeg_bytes, "image/jpeg")},
            data={
                "scheme_id": "ladli_behna",
                "language": "hi-IN",
                "field_states": field_states_json,
            },
        )
        assert response.status_code == 200
        data = response.json()
        assert data["field_type"] == "INPUT_TEXT"
        assert "आधार" in data["spoken_guidance"]
        assert len(data["box_2d"]) == 4

    def test_analyze_screen_empty_image_fails_validation(self):
        response = client.post(
            "/api/v1/analyze-screen",
            files={"image": ("empty.jpg", b"", "image/jpeg")},
            data={"language": "hi-IN"},
        )
        assert response.status_code == 400
        assert "Empty image" in response.json()["detail"]

    def test_analyze_screen_corrupted_image_fails_validation(self):
        response = client.post(
            "/api/v1/analyze-screen",
            files={"image": ("corrupted.jpg", b"not-a-valid-image-content", "image/jpeg")},
            data={"language": "hi-IN"},
        )
        assert response.status_code == 400
        assert "not a valid image format" in response.json()["detail"]


class TestDidiAskEndpoint:
    """Tests for /api/v1/didi/ask conversational assistance endpoint."""

    def test_ask_didi_hindi_query(self):
        payload = {
            "user_query": "मुझे आधार कार्ड कहां से मिलेगा?",
            "language": "hi-IN",
        }
        response = client.post("/api/v1/didi/ask", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert "spoken_answer" in data
        assert len(data["spoken_answer"]) > 0
        assert "subtitle_text" in data
        assert len(data["subtitle_text"]) > 0
        # In mock mode, query with "आधार" returns aadhaar_help with 'आधार'
        assert "आधार" in data["spoken_answer"]

    def test_ask_didi_english_query(self):
        payload = {
            "user_query": "What should I do on this page?",
            "language": "en-IN",
        }
        response = client.post("/api/v1/didi/ask", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert len(data["spoken_answer"]) > 0
        assert len(data["subtitle_text"]) > 0

    def test_ask_didi_empty_query_fails(self):
        payload = {
            "user_query": "   ",
            "language": "hi-IN",
        }
        response = client.post("/api/v1/didi/ask", json=payload)
        assert response.status_code == 400
        assert "cannot be empty" in response.json()["detail"]


class TestCorsAndMiddleware:
    """Tests ensuring CORS headers allow mobile emulators and physical devices."""

    def test_cors_preflight_options(self):
        response = client.options(
            "/health",
            headers={
                "Origin": "http://localhost:3000",
                "Access-Control-Request-Method": "GET",
            },
        )
        assert response.status_code == 200
        assert response.headers.get("access-control-allow-origin") in ["*", "http://localhost:3000"]
