# Sahaayika (सहायिका) 🌸
### Multimodal AI Companion & Visual Grounding Guide for Digital Public Infrastructure

[![Gemini](https://img.shields.io/badge/Model-Gemini%203.8%20Flash-blueviolet?style=for-the-badge&logo=google)](https://ai.google.dev/)
[![Android](https://img.shields.io/badge/Platform-Android%2014%20(API%2034)-green?style=for-the-badge&logo=android)](https://developer.android.com/)
[![FastAPI](https://img.shields.io/badge/Backend-FastAPI%200.110-009688?style=for-the-badge&logo=fastapi)](https://fastapi.tiangolo.com/)
[![Render](https://img.shields.io/badge/Deployed%20on-Render-46E3B7?style=for-the-badge&logo=render)](https://sahaayika-brain.onrender.com)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

> **"दीदी, चिंता मत करो, मैं हर कदम पर आपके साथ हूँ।"**  
> *(Sister, do not worry, I am with you at every step.)*

---

## 📖 Overview

**Sahaayika (सहायिका)** is a multimodal, vernacular AI assistant designed to bridge India's digital divide. Millions of rural citizens—especially women and first-time smartphone users—are structurally excluded from life-changing welfare schemes (such as *Ladli Behna Yojana*, *PM-Kisan*, *Ration Card*, and *Janani Suraksha*) because government portals are dense, unlabelled, and filled with bureaucratic jargon.

Traditional text-prompting chatbots fail these users. **Sahaayika flips the paradigm to Proactive Visual & Audio Guidance**:
1. **Speaks First in Vernacular**: Immediately greets and guides in native regional languages (Hindi, Tamil, Bengali, Telugu, Marathi, English).
2. **Visual Spotlight Aperture**: Ingests browser screens and uses **Google Gemini 3.8 Flash** to physically highlight exact input fields with a glowing gold spotlight on top of the live web browser.
3. **Empathetic Companion Persona ("Didi")**: Answers citizen doubts in simple, encouraging terms without confusing technical jargon.

---

## 🚀 Live Cloud Deployment

The Sahaayika Brain backend is deployed live on **Render**:

* **Base URL**: [`https://sahaayika-brain.onrender.com`](https://sahaayika-brain.onrender.com)
* **API Documentation (Swagger UI)**: [`https://sahaayika-brain.onrender.com/docs`](https://sahaayika-brain.onrender.com/docs)
* **System Health Check**: [`https://sahaayika-brain.onrender.com/health`](https://sahaayika-brain.onrender.com/health)
* **Interactive Welfare Simulation Portal**: [`https://sahaayika-brain.onrender.com/portal`](https://sahaayika-brain.onrender.com/portal)

---

## 📱 Mobile App (Android Client)

Download the pre-compiled APK directly from the GitHub Releases:
📦 **[Download Sahaayika v1.0.0 APK](https://github.com/Equlient-Joy/HackArena-hackathon/releases/tag/v1.0.0)**

### Required Android Permissions:
1. **Accessibility Service** (`SahaayikaAccessibilityService`): Enables passive screenshot capture when navigating scheme portals to compute grounding coordinates without breaking user flow.
2. **Display Over Other Apps** (`TYPE_APPLICATION_OVERLAY`): Renders the floating "Didi" avatar and the dynamic visual spotlight cutout over web browsers.
3. **Microphone** (`RECORD_AUDIO`): Enables vernacular voice search and intent resolution.

---

## 🏛️ System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│               Android Thin Client (Edge / Kotlin)               │
│                                                                 │
│  [LanguageActivity] ──> [GuidedHubActivity] ──> [Scheme Portal] │
│          │                       │                     │        │
│    (Vernacular TTS)       (Voice Intent Mic)   (Chrome / Web)   │
│                                                        │        │
│  ┌───────────────────────┐             ┌───────────────▼──────┐ │
│  │ FloatingDidiAvatar    │             │ SahaayikaA11yService │ │
│  │ (Draggable & Speech)  │             │ (Silent Snapshot)    │ │
│  └───────────▲───────────┘             └───────────────┬──────┘ │
│              │                                         │        │
│  ┌───────────┴───────────┐                             │        │
│  │ SpotlightCanvasView   │◄────────[box_2d & TTS]──────┤        │
│  │ (Clear Aperture + Ring)│                             │        │
│  └───────────────────────┘                             │        │
└────────────────────────────────────────────────────────┼────────┘
                                                         │
                                        HTTPS / REST API │ (Multipart / JSON)
                                                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                 FastAPI Backend (Cloud / Render)                │
│                                                                 │
│  • POST /api/v1/intent         ──> Scheme matching engine       │
│  • POST /api/v1/analyze-screen ──> Gemini 3.8 Flash Vision      │
│  • POST /api/v1/didi/ask       ──> Empathetic Conversational QA │
│  • GET  /api/v1/schemes        ──> Multilingual catalog         │
│                                                                 │
│                     ┌────────────────────┐                      │
│                     │  Google Gemini API │                      │
│                     │ (gemini-3.8-flash) │                      │
│                     └────────────────────┘                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## ✨ Key Features & Technical Highlights

### 1. Multimodal Visual Grounding via Gemini 3.8 Flash
- Uses high-speed screen understanding with normalized `box_2d` coordinates: `[ymin, xmin, ymax, xmax]`.
- The Android client translates normalized coordinates into device physical pixels via `SpotlightCanvasView`.
- Clears a transparent portal aperture using `PorterDuff.Mode.CLEAR` and animates a pulsing 24K gold border around the active field.

### 2. Zero-Prompting Voice Intent Hub
- Citizens simply tap the glowing microphone button and speak freely in their native dialect (e.g., *"मेरी लाडली बहना के पैसे देखने हैं"*).
- The intent pipeline resolves the goal, suggests matching official schemes, and automatically launches the official government portal.

### 3. Native Indic Audio Pipeline
- Integrates `IndicTextToSpeech` with regional locales (`hi-IN`, `mr-IN`, `ta-IN`, `te-IN`, `bn-IN`, `en-IN`).
- Automatically narrates every step and guides first-time users before they even need to ask.

### 4. Privacy-by-Design Local Scrim
- Passively monitors UI element types to mask sensitive password or financial inputs before screenshot dispatch.
- Debounced accessibility event queue (1500ms) with concurrency mutex prevents duplicate network calls.

---

## 🛠️ Local Development & Running

### Prerequisites
- Python 3.11+
- Android Studio / Android SDK 34 (Java 17+)
- Docker & Docker Compose (Optional)
- Google Gemini API Key ([Get one here](https://aistudio.google.com/))

### Backend Setup (FastAPI)

1. **Navigate to the backend directory**:
   ```bash
   cd backend
   ```

2. **Create and activate a virtual environment**:
   ```bash
   python -m venv .venv
   # Windows:
   .venv\Scripts\activate
   # macOS/Linux:
   source .venv/bin/activate
   ```

3. **Install dependencies**:
   ```bash
   pip install -r requirements.txt
   ```

4. **Configure environment variables**:
   Create a `.env` file in `backend/`:
   ```env
   GEMINI_API_KEY=your_gemini_api_key_here
   GEMINI_MODEL=gemini-3.8-flash
   DEFAULT_LANGUAGE=hi-IN
   PORT=8000
   ```

5. **Start the server**:
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```

6. **Run Backend Test Suite**:
   ```bash
   pytest -v
   # 71 passed in ~5.5s
   ```

---

### Docker Deployment

To run the containerized backend locally:

```bash
docker compose up -d --build
```

Health check verification:
```bash
curl http://localhost:8000/health
# {"status":"ok","service":"sahaayika-brain","version":"1.0.0"}
```

---

### Android Client Setup

1. Open the `android` folder in **Android Studio**.
2. Sync Gradle dependencies.
3. Verify or update backend URL in `SahaayikaApiService.kt`:
   ```kotlin
   // For live cloud:
   const val DEFAULT_BASE_URL = "https://sahaayika-brain.onrender.com/api/v1"
   // For local emulator:
   // const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/api/v1"
   ```
4. Build and run unit tests:
   ```bash
   cd android
   ./gradlew testDebugUnitTest
   # 27 tests passed
   ```
5. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   # Output: android/app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🧪 Verification & Test Coverage

| Component | Test Suite | Tests | Result |
| :--- | :--- | :--- | :--- |
| **Backend API** | `pytest tests/` | **71 Tests** | ✅ Passing |
| **Android Client** | `testDebugUnitTest` | **27 Tests** | ✅ Passing |
| **E2E Visual Grounding** | Live Android 14 Emulator | Tested | ✅ Aperture Drawn |
| **Audio Vocalization** | `IndicTextToSpeech` | Tested | ✅ Vernacular Audio |

---

## 📜 License

This project is licensed under the [MIT License](LICENSE).