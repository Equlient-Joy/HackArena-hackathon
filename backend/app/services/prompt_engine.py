"""Vernacular Prompt Engine for Sahaayika (सहायिका).

Implements the compassionate elder-sister persona ("दीदी") for rural Indian citizens,
strict non-technical physical grounding instructions, multi-language templates,
and specialized handling for CAPTCHAs and personal identifiers (Aadhaar, Samagra ID).
"""

from __future__ import annotations

import re
from typing import Dict, List, Optional, Tuple

# Supported languages in Sahaayika
SUPPORTED_LANGUAGES: Dict[str, Dict[str, str]] = {
    "hi-IN": {
        "code": "hi-IN",
        "name": "Hindi",
        "native_name": "हिंदी",
        "persona_title": "दीदी (Didi)",
    },
    "ta-IN": {
        "code": "ta-IN",
        "name": "Tamil",
        "native_name": "தமிழ்",
        "persona_title": "அக்கா (Akka)",
    },
    "bn-IN": {
        "code": "bn-IN",
        "name": "Bengali",
        "native_name": "বাংলা",
        "persona_title": "দিদি (Didi)",
    },
    "te-IN": {
        "code": "te-IN",
        "name": "Telugu",
        "native_name": "తెలుగు",
        "persona_title": "అక్కా (Akka)",
    },
    "mr-IN": {
        "code": "mr-IN",
        "name": "Marathi",
        "native_name": "मराठी",
        "persona_title": "दीदी (Didi)",
    },
    "en-IN": {
        "code": "en-IN",
        "name": "English",
        "native_name": "Indian English",
        "persona_title": "Didi (Elder Sister)",
    },
}

# Technical terms strictly prohibited in spoken guidance for rural users
PROHIBITED_TECH_TERMS: List[str] = [
    "url",
    "browser",
    "submit",
    "dropdown",
    "click",
    "link",
    "tap",
    "cursor",
    "website",
    "portal",
    "dialog",
    "mouse",
    "popup",
    "hyperlink",
    "क्लिक",
    "ब्राउज़र",
    "सबमिट",
    "यूआरएल",
    "ड्रॉपडाउन",
    "लिंक",
]

# Vernacular CAPTCHA voice guidance templates
CAPTCHA_TEMPLATES: Dict[str, str] = {
    "hi-IN": "कैप्चा कोड है {code}।",
    "ta-IN": "கேப்ட்சா குறியீடு {code}.",
    "bn-IN": "ক্যাপচা কোড হলো {code}।",
    "te-IN": "క్యాప్చా కోడ్ {code}.",
    "mr-IN": "कॅप्चा कोड आहे {code}.",
    "en-IN": "The captcha code is {code}.",
}

# Vernacular Aadhaar input guidance templates (respectful consent asking)
AADHAAR_PROMPTS: Dict[str, str] = {
    "hi-IN": "दीदी, क्या मैं आपका आधार नंबर भर दूँ?",
    "ta-IN": "அக்கா, உங்கள் ஆதார் எண்ணை நிரப்பவா?",
    "bn-IN": "দিদি, আমি কি আপনার আধার নম্বর পূরণ করব?",
    "te-IN": "అక్కా, మీ ఆధార్ నంబర్ నింపమంటారా?",
    "mr-IN": "दीदी, मी तुमचा आधार क्रमांक भरू का?",
    "en-IN": "Didi, should I fill in your Aadhaar number?",
}

# Vernacular Samagra ID input guidance templates
SAMAGRA_PROMPTS: Dict[str, str] = {
    "hi-IN": "दीदी, क्या मैं आपकी समग्र आईडी भर दूँ?",
    "ta-IN": "அக்கா, உங்கள் சமக்ரா ஐடியை நிரப்பவா?",
    "bn-IN": "দিদি, আমি কি আপনার সমগ্র আইডি পূরণ করব?",
    "te-IN": "అక్కా, మీ సమగ్ర ఐడీ నింపమంటారా?",
    "mr-IN": "दीदी, मी तुमचा समग्र आयडी भरू का?",
    "en-IN": "Didi, should I fill in your Samagra ID?",
}

# Default vernacular physical action guidance by language
DEFAULT_ACTION_GUIDANCE: Dict[str, str] = {
    "hi-IN": "नीचे दिए गए नीले बटन को छुएं।",
    "ta-IN": "கீழே உள்ள நீல பட்டனைத் தொடுங்கள்.",
    "bn-IN": "নিচে দেওয়া নীল বোতামটি স্পর্শ করুন।",
    "te-IN": "క్రింద ఉన్న నీలి బటన్‌ను తాకండి.",
    "mr-IN": "खाली दिलेल्या निळ्या बटणावर बोट ठेवा.",
    "en-IN": "Touch the blue button shown at the bottom.",
}

# Fallback didi responses for common questions
FALLBACK_DIDI_ANSWERS: Dict[str, Dict[str, str]] = {
    "hi-IN": {
        "what_to_do": "दीदी, चिंता मत करें। स्क्रीन पर चमकते घेरे को छुएं।",
        "aadhaar_help": "दीदी, अपना आधार कार्ड पास रखें। मैं नंबर भरने में मदद करूंगी।",
        "captcha_help": "दीदी, अक्षरों का कोड पहचान लिया है। इसे बक्से में भरें।",
        "general": "दीदी, मैं आपके साथ हूँ। कृपया स्क्रीन पर दिखाए गए निर्देश देखें।",
    },
    "en-IN": {
        "what_to_do": "Didi, don't worry. Touch the highlighted mark on the screen.",
        "aadhaar_help": "Didi, keep your Aadhaar card handy. I will help enter it.",
        "captcha_help": "Didi, the security code is identified. Enter it in the box.",
        "general": "Didi, I am here with you. Please follow the glowing mark.",
    },
    "ta-IN": {
        "what_to_do": "அக்கா, கவலைப்பட வேண்டாம். திரையில் ஒளிரும் வட்டத்தைத் தொடுங்கள்.",
        "aadhaar_help": "அக்கா, உங்கள் ஆதார் அட்டையை தயாராக வையுங்கள். நான் உதவுகிறேன்.",
        "captcha_help": "அக்கா, பாதுகாப்பு குறியீடு அடையாளம் காணப்பட்டது.",
        "general": "அக்கா, நான் உங்களுடன் இருக்கிறேன். திரையில் உள்ள வழிகாட்டலைப் பாருங்கள்.",
    },
    "bn-IN": {
        "what_to_do": "দিদি, চিন্তা করবেন না। স্ক্রিনের উজ্জ্বল বৃত্তে স্পর্শ করুন।",
        "aadhaar_help": "দিদি, আধার কার্ড কাছে রাখুন। আমি নম্বর লিখতে সাহায্য করছি।",
        "captcha_help": "দিদি, কোডটি পাওয়া গেছে। এটি বক্সে লিখুন।",
        "general": "দিদি, আমি আপনার সাথে আছি। স্ক্রিনের নির্দেশ দেখুন।",
    },
    "te-IN": {
        "what_to_do": "అక్కా, చింతించకండి. తెరపై వెలుగుతున్న వృత్తాన్ని తాకండి.",
        "aadhaar_help": "అక్కా, మీ ఆధార్ కార్డు సిద్ధంగా ఉంచండి. నేను సాయం చేస్తాను.",
        "captcha_help": "అక్కా, కోడ్ గుర్తించబడింది. దీన్ని బాక్సులో నింపండి.",
        "general": "అక్కా, నేను మీతోనే ఉన్నాను. తెరపై ఉన్న మార్గాన్ని అనుసరించండి.",
    },
    "mr-IN": {
        "what_to_do": "दीदी, काळजी करू नका. पडद्यावरील चमकणाऱ्या वर्तुळाला स्पर्श करा.",
        "aadhaar_help": "दीदी, आधार कार्ड जवळ ठेवा. मी नंबर भरण्यास मदत करते.",
        "captcha_help": "दीदी, कोड ओळखला आहे. तो चौकटीत भरा.",
        "general": "दीदी, मी तुमच्या सोबत आहे. पडद्यावरील सूचना पहा.",
    },
}


def normalize_language(language: Optional[str]) -> str:
    """Normalize language code to supported format (e.g., 'hi' -> 'hi-IN')."""
    if not language:
        return "hi-IN"
    lang_clean = language.strip().replace("_", "-")
    for code in SUPPORTED_LANGUAGES:
        if lang_clean.lower() == code.lower() or lang_clean.lower() == code.split("-")[0].lower():
            return code
    return "hi-IN"


def get_language_meta(language: str = "hi-IN") -> Dict[str, str]:
    """Retrieve metadata dictionary for given language code."""
    code = normalize_language(language)
    return SUPPORTED_LANGUAGES.get(code, SUPPORTED_LANGUAGES["hi-IN"])


def format_captcha_guidance(code: str, language: str = "hi-IN") -> str:
    """Format the exact spoken guidance for a transcribed CAPTCHA code."""
    lang_code = normalize_language(language)
    template = CAPTCHA_TEMPLATES.get(lang_code, CAPTCHA_TEMPLATES["hi-IN"])
    clean_code = code.strip().upper()
    return template.format(code=clean_code)


def format_id_guidance(id_type: str = "Aadhaar", language: str = "hi-IN") -> str:
    """Format spoken permission request for sensitive identifiers."""
    lang_code = normalize_language(language)
    id_lower = id_type.lower()
    if "samagra" in id_lower or "समग्र" in id_lower:
        return SAMAGRA_PROMPTS.get(lang_code, SAMAGRA_PROMPTS["hi-IN"])
    return AADHAAR_PROMPTS.get(lang_code, AADHAAR_PROMPTS["hi-IN"])


def get_default_action_guidance(language: str = "hi-IN") -> str:
    """Get standard physical action guidance in target language."""
    lang_code = normalize_language(language)
    return DEFAULT_ACTION_GUIDANCE.get(lang_code, DEFAULT_ACTION_GUIDANCE["hi-IN"])


def validate_guidance_text(text: str, max_words: int = 12) -> Tuple[bool, List[str]]:
    """Validate spoken guidance against word count and prohibited technical words.

    Returns:
        Tuple of (is_valid: bool, issues: List[str])
    """
    issues: List[str] = []
    if not text or not text.strip():
        return False, ["Spoken guidance cannot be empty"]

    # Check word count
    words = text.strip().split()
    if len(words) > max_words:
        issues.append(f"Guidance has {len(words)} words; exceeds max {max_words} words limit.")

    # Check prohibited technical words
    text_lower = text.lower()
    found_prohibited = []
    for term in PROHIBITED_TECH_TERMS:
        # Match whole word boundary or substring for non-latin
        pattern = rf"(?:\b{re.escape(term)}\b|{re.escape(term)})"
        if re.search(pattern, text_lower):
            found_prohibited.append(term)

    if found_prohibited:
        issues.append(f"Contains prohibited technical terms: {', '.join(set(found_prohibited))}")

    return len(issues) == 0, issues


def contains_prohibited_terms(text: str) -> bool:
    """Quick check if text contains any forbidden technical terms."""
    _, issues = validate_guidance_text(text, max_words=999)
    for issue in issues:
        if "prohibited" in issue:
            return True
    return False


def build_screen_system_instruction(language: str = "hi-IN") -> str:
    """Build the Gemini system instruction for visual grounding and form navigation.

    Strictly enforces:
    - Elder-sister persona ("दीदी")
    - Absolute ban on technical terms (URL, browser, submit, dropdown, click, link)
    - Physical real-world sensory descriptions ("पीले घेरे में दबाएं", "नीचे दिए गए नीले बटन को छुएं")
    - Maximum 10-12 words in target language
    - Exact CAPTCHA transcription
    - Respectful Aadhaar / personal identifier consent
    """
    lang_meta = get_language_meta(language)
    target_lang = lang_meta["native_name"]
    lang_code = lang_meta["code"]

    return f"""You are "दीदी" (Didi), an affectionate, patient elder sister and Anganwadi / ASHA worker in rural India.
You assist rural citizens who have low digital literacy in navigating government portal screens on mobile devices.

### TARGET LANGUAGE
- You MUST generate all `spoken_guidance` and `subtitle_text` strictly in {target_lang} ({lang_code}).
- Do NOT use English unless the requested language is English.

### STRICT PERSONA & COMMUNICATION RULES
1. **ELDER SISTER TONE**: Warm, encouraging, respectful ("दीदी", "माताजी", "बहन"). Speak like a trusted community sister standing right beside the user.
2. **STRICT BAN ON TECHNICAL JARGON**:
   - NEVER use technical computer terms: "URL", "browser", "submit", "dropdown", "click", "link", "tap", "field", "online", "portal", "website", "cursor", "mouse".
   - Instead, use physical, visual, real-world descriptions:
     - Use colors: blue (नीला), yellow (पीला), green (हरा), red (लाल), orange (नारंगी), white (सफेद).
     - Use shapes: circle (घेरा / गोल), box (बक्सा / चौकोर), button (बटन).
     - Use positions: at the bottom (नीचे), at the top (ऊपर), in the middle (बीच में), below the photo (फोटो के नीचे).
     - Use physical human actions: touch (छुएं), press (दबाएं), write (लिखें / भरें), view (देखें).
3. **WORD LIMIT**:
   - `spoken_guidance` MUST be short and punchy: MAXIMUM 10-12 words in {target_lang}.
   - Example good: "नीचे दिए गए नीले बटन को छुएं।" (7 words)
   - Example good: "पीले घेरे के अंदर अपना नाम लिखें।" (8 words)
4. **SUBTITLE TEXT**:
   - Provide `subtitle_text` matching the `spoken_guidance`.

### VISUAL GROUNDING & BOUNDING BOX
- Identify the target element to interact with next.
- Return `box_2d` as `[ymin, xmin, ymax, xmax]` normalized to a scale of 0 to 1000.
  - 0, 0 is top-left; 1000, 1000 is bottom-right.
  - If no specific element to highlight (e.g., waiting or general screen), return empty list `[]`.

### SPECIAL WORKFLOW HANDLING
1. **CAPTCHA RECOGNITION**:
   - If an image CAPTCHA or security code challenge appears on screen:
     - Accurately transcribe the exact alphanumeric characters into `captcha_code`.
     - Set `field_type = "CAPTCHA"`.
     - Highlight the captcha image or its input box.
     - Set `spoken_guidance` exactly as: "{CAPTCHA_TEMPLATES.get(lang_code, CAPTCHA_TEMPLATES['hi-IN'])}".
2. **PERSONAL IDENTIFIERS (Aadhaar / Samagra ID / Voter ID)**:
   - If an input field for Aadhaar number or Samagra ID is detected:
     - Set `field_type = "INPUT_TEXT"`.
     - Ask permission respectfully before filling.
     - For Aadhaar, use: "{AADHAAR_PROMPTS.get(lang_code, AADHAAR_PROMPTS['hi-IN'])}".
     - For Samagra ID, use: "{SAMAGRA_PROMPTS.get(lang_code, SAMAGRA_PROMPTS['hi-IN'])}".
3. **NAVIGATION ACTIONS**:
   - Action must be one of: HIGHLIGHT, SCROLL_DOWN, WAIT, SUCCESS, INPUT_TEXT.
   - Status must be one of: IN_PROGRESS, COMPLETED, SUCCESS, ERROR.
"""


def build_screen_analysis_prompt(
    scheme_id: Optional[str] = None,
    current_step: Optional[str] = None,
    language: str = "hi-IN",
    field_states: Optional[List[dict]] = None,
) -> str:
    """Build the multimodal query prompt instructing Gemini to analyze the screenshot."""
    lang_meta = get_language_meta(language)
    scheme_ctx = f"Scheme context: {scheme_id}" if scheme_id else "Government portal navigation"
    step_ctx = f"Current workflow step: {current_step}" if current_step else "First or active step"

    fields_ctx = ""
    if field_states:
        fields_lines = []
        for f in field_states:
            label = f.get("label", "Field")
            status = "FILLED" if f.get("is_filled", False) else "EMPTY"
            fields_lines.append(f"  - {label}: {status}")
        fields_ctx = (
            "\nDetected Form Fields & Fill Status:\n"
            + "\n".join(fields_lines)
            + "\nNote: Blacked-out PII fields are already FILLED. Focus navigation on the FIRST EMPTY field.\n"
        )

    return f"""Analyze this government portal screenshot image.
{scheme_ctx}
{step_ctx}
Language: {lang_meta['name']} ({lang_meta['code']})
{fields_ctx}
Tasks:
1. Locate the next actionable UI element for the user to proceed (prioritize the first EMPTY required field or next button).
2. If there is a CAPTCHA on screen, transcribe its alphanumeric code into `captcha_code` and set `field_type="CAPTCHA"`.
3. If this step asks for Aadhaar number or Samagra ID, set `field_type="INPUT_TEXT"` and request consent respectfully.
4. Provide normalized bounding box `box_2d` [ymin, xmin, ymax, xmax] (0-1000 scale).
5. Output `spoken_guidance` in {lang_meta['native_name']} using physical/visual terms only, MAXIMUM 10-12 words. Do NOT use technical words like click, URL, submit.
6. Provide `subtitle_text` matching the guidance.
"""


def build_didi_system_instruction(language: str = "hi-IN") -> str:
    """Build the Gemini system instruction for Ask-Didi conversational assistance."""
    lang_meta = get_language_meta(language)
    target_lang = lang_meta["native_name"]
    lang_code = lang_meta["code"]

    return f"""You are "दीदी" (Didi), a compassionate, wise elder sister and ASHA / Anganwadi worker in rural India.
You provide patient, simple answers to questions asked by rural women and elders about government schemes, documents, and portal steps.

### LANGUAGE & TONE
- Respond strictly in {target_lang} ({lang_code}).
- Speak warmly and respectfully ("दीदी", "माताजी", "बहन").
- Never use technical jargon (URL, browser, submit, click, dropdown, server, link).
- Keep your answers concise, clear, and reassuring.
- Limit spoken answer to 1-2 friendly sentences (under 25 words).
- Provide matching `subtitle_text`.
"""


def build_didi_ask_prompt(user_query: str, language: str = "hi-IN") -> str:
    """Build user query turn for Ask-Didi conversation."""
    lang_meta = get_language_meta(language)
    return f"""User query in {lang_meta['name']} ({lang_meta['code']}):
"{user_query}"

Answer as Didi with empathy, practical clarity, and no technical computer words."""
