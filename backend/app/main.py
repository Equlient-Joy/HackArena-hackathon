"""FastAPI application entrypoint for Sahaayika (सहायिका) Brain API."""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1.router import api_v1_router
from app.config import settings

app = FastAPI(
    title="Sahaayika (सहायिका) Brain API",
    version="1.0.0",
    description="Multimodal Indic visual grounding and scheme navigation backend for rural citizens",
)

# Enable CORS for local Android emulators (10.0.2.2), physical USB devices, and web frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount API v1 endpoints
app.include_router(api_v1_router, prefix="/api/v1")


@app.get("/health", tags=["system"])
def health_check() -> dict:
    """System health check endpoint."""
    return {
        "status": "ok",
        "service": "sahaayika-brain",
        "version": "1.0.0",
    }


from fastapi.responses import HTMLResponse

@app.get("/portal", response_class=HTMLResponse, tags=["portal"])
def get_test_portal() -> str:
    """Mock government welfare portal page for testing visual grounding and auto-fill."""
    return """<!DOCTYPE html>
<html lang="hi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>मुख्यमंत्री लाडली बहना योजना - आवेदन स्थिति</title>
    <style>
        body { font-family: sans-serif; background-color: #f7f9fa; margin: 0; padding: 16px; color: #222; }
        .header { background: #1a4f8b; color: white; padding: 18px; text-align: center; border-radius: 8px; margin-bottom: 24px; }
        .header h1 { font-size: 20px; margin: 0 0 6px 0; }
        .header p { font-size: 13px; margin: 0; opacity: 0.9; }
        .card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
        .field { margin-bottom: 20px; }
        label { display: block; font-weight: 600; font-size: 15px; margin-bottom: 8px; color: #333; }
        input[type="text"] { width: 100%; box-sizing: border-box; height: 52px; font-size: 18px; padding: 8px 14px; border: 2px solid #ccc; border-radius: 8px; }
        .captcha-box { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
        .captcha-badge { background: #fee; color: #b00; font-weight: bold; font-size: 24px; letter-spacing: 6px; padding: 8px 16px; border: 2px dashed #d33; border-radius: 6px; font-family: monospace; }
        .btn-submit { display: block; width: 100%; height: 56px; background-color: #0d6efd; color: white; border: none; border-radius: 8px; font-size: 18px; font-weight: bold; cursor: pointer; margin-top: 24px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>मुख्यमंत्री लाडली बहना योजना</h1>
        <p>मध्य प्रदेश शासन | आवेदन एवं भुगतान स्थिति</p>
    </div>
    <div class="card">
        <div class="field">
            <label for="samagra">समग्र सदस्य आईडी दर्ज करें (Samagra Member ID):</label>
            <input type="text" id="samagra" placeholder="9 अंकों की समग्र आईडी" maxlength="9">
        </div>
        <div class="field">
            <label for="captcha">कैप्चा कोड दर्ज करें (Enter CAPTCHA):</label>
            <div class="captcha-box">
                <span class="captcha-badge">4X7K</span>
            </div>
            <input type="text" id="captcha" placeholder="कैप्चा कोड लिखें" maxlength="6">
        </div>
        <button class="btn-submit" type="button">खोजें एवं आगे बढ़ें (Submit)</button>
    </div>
</body>
</html>
"""


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host=settings.HOST, port=settings.PORT, reload=True)
