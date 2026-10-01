package com.sahaayika.app.ui.hub

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.sahaayika.databinding.ItemSchemeCardBinding
import com.sahaayika.app.data.model.SchemeItem

/**
 * Adapter presenting a horizontal carousel of curated welfare scheme cards.
 *
 * Binds the 6 core government welfare schemes for quick-tap selection:
 * 1. Ladli Behna (मुख्यमंत्री लाडली बहना योजना)
 * 2. PM-Kisan (पीएम-किसान सम्मान निधि)
 * 3. Ration Card (राष्ट्रीय खाद्य सुरक्षा / राशन कार्ड)
 * 4. Ayushman Bharat (आयुष्मान भारत / जन आरोग्य)
 * 5. Janani Suraksha (जननी सुरक्षा योजना)
 * 6. Pension (राष्ट्रीय सामाजिक सहायता / पेंशन)
 */
class SchemeCardAdapter(
    val schemes: List<SchemeItem> = DEFAULT_CURATED_SCHEMES,
    private val onSchemeSelected: (SchemeItem) -> Unit
) : RecyclerView.Adapter<SchemeCardAdapter.SchemeViewHolder>() {

    companion object {
        val DEFAULT_CURATED_SCHEMES: List<SchemeItem> = listOf(
            SchemeItem(
                schemeId = "ladli_behna",
                schemeName = "मुख्यमंत्री लाडली बहना योजना",
                portalUrl = "https://cmladlibehna.mp.gov.in",
                confirmationSpeech = "लाडली बहना योजना का पोर्टल खोला जा रहा है। मैं आपको फॉर्म भरने और स्थिति देखने में पूरी मदद करूंगी।",
                initialPrompt = "लाडली बहना योजना में आवेदन या स्थिति देखने के लिए समग्र आईडी या आवेदन क्रमांक दर्ज करें।",
                description = "मासिक आर्थिक सहायता एवं महिला सशक्तिकरण (₹1250/माह)"
            ),
            SchemeItem(
                schemeId = "pm_kisan",
                schemeName = "पीएम-किसान सम्मान निधि",
                portalUrl = "https://pmkisan.gov.in",
                confirmationSpeech = "पीएम किसान सम्मान निधि का पोर्टल खोला जा रहा है। आइए आपकी किस्त की स्थिति देखते हैं।",
                initialPrompt = "पीएम-किसान सम्मान निधि का स्टेटस देखने के लिए अपना आधार नंबर या रजिस्ट्रेशन नंबर दर्ज करें।",
                description = "किसानों के लिए ₹6,000 वार्षिक सहायता राशि"
            ),
            SchemeItem(
                schemeId = "ration_card",
                schemeName = "राष्ट्रीय खाद्य सुरक्षा (राशन कार्ड)",
                portalUrl = "https://nfsa.gov.in",
                confirmationSpeech = "राशन कार्ड का पोर्टल खोला जा रहा है। मैं आपको राशन कार्ड की पात्रता और जानकारी देखने में मदद करूंगी।",
                initialPrompt = "राशन कार्ड की पात्रता या सूची देखने के लिए अपना राशन कार्ड नंबर या जिला चुनें।",
                description = "सस्ता अनाज, राशन कार्ड पात्रता एवं ऑनलाइन सूची"
            ),
            SchemeItem(
                schemeId = "ayushman_bharat",
                schemeName = "आयुष्मान भारत (जन आरोग्य)",
                portalUrl = "https://beneficiary.nha.gov.in",
                confirmationSpeech = "आयुष्मान भारत का पोर्टल खोला जा रहा है। आइए 5 लाख रुपये तक के मुफ्त इलाज के कार्ड की स्थिति जांचते हैं।",
                initialPrompt = "आयुष्मान कार्ड बनाने या खोजने के लिए आधार या राशन कार्ड नंबर दर्ज करें।",
                description = "₹5 लाख तक का मुफ्त इलाज एवं आयुष्मान कार्ड"
            ),
            SchemeItem(
                schemeId = "janani_suraksha",
                schemeName = "जननी सुरक्षा योजना (JSY)",
                portalUrl = "https://nhm.gov.in",
                confirmationSpeech = "जननी सुरक्षा योजना का पोर्टल खोला जा रहा है। गर्भवती माताओं की आर्थिक सहायता और प्रसूति सुविधा की जानकारी देखते हैं।",
                initialPrompt = "जननी सुरक्षा योजना के मातृत्व लाभ की जानकारी देखने के लिए आगे बढ़ें।",
                description = "गर्भवती माताओं के लिए सुरक्षित प्रसव सहायता"
            ),
            SchemeItem(
                schemeId = "pension",
                schemeName = "राष्ट्रीय सामाजिक सहायता (पेंशन)",
                portalUrl = "https://nsap.nic.in",
                confirmationSpeech = "पेंशन योजना का पोर्टल खोला जा रहा है। आइए आपकी वृद्धा, विधवा या दिव्यांग पेंशन की स्थिति जांचते हैं।",
                initialPrompt = "वृद्धावस्था या विधवा पेंशन की स्थिति देखने के लिए आवेदन क्रमांक या खाता संख्या दर्ज करें।",
                description = "वृद्धा, विधवा एवं दिव्यांगजनों के लिए मासिक पेंशन"
            )
        )

        fun getCuratedSchemesForLanguage(languageCode: String): List<SchemeItem> {
            val prefix = languageCode.lowercase().substringBefore("-")
            return if (prefix == "en") {
                listOf(
                    SchemeItem(
                        schemeId = "ladli_behna",
                        schemeName = "Mukhyamantri Ladli Behna Yojana",
                        portalUrl = "https://cmladlibehna.mp.gov.in",
                        confirmationSpeech = "Opening the Ladli Behna Yojana portal. I will guide you step-by-step through the process.",
                        initialPrompt = "Please enter your Samagra ID or application number to check Ladli Behna status.",
                        description = "Direct monthly assistance for rural women (₹1250/mo)"
                    ),
                    SchemeItem(
                        schemeId = "pm_kisan",
                        schemeName = "PM-Kisan Samman Nidhi",
                        portalUrl = "https://pmkisan.gov.in",
                        confirmationSpeech = "Opening the PM-Kisan portal. Let's check your installment and beneficiary status.",
                        initialPrompt = "Please enter your Aadhaar number or registration number to view your PM-Kisan status.",
                        description = "Direct income support of ₹6,000 per year for farmers"
                    ),
                    SchemeItem(
                        schemeId = "ration_card",
                        schemeName = "National Food Security (Ration Card)",
                        portalUrl = "https://nfsa.gov.in",
                        confirmationSpeech = "Opening the Ration Card portal. I will help you find your ration card and entitlement details.",
                        initialPrompt = "Please select your state or enter your ration card number to proceed.",
                        description = "Subsidized food grain distribution and NFSA cards"
                    ),
                    SchemeItem(
                        schemeId = "ayushman_bharat",
                        schemeName = "Ayushman Bharat (PM-JAY)",
                        portalUrl = "https://beneficiary.nha.gov.in",
                        confirmationSpeech = "Opening the Ayushman Bharat portal. Let's check your free healthcare card eligibility.",
                        initialPrompt = "Please enter your Aadhaar or Ration Card number to search for your Ayushman Card.",
                        description = "Free healthcare coverage up to ₹5 Lakhs per family"
                    ),
                    SchemeItem(
                        schemeId = "janani_suraksha",
                        schemeName = "Janani Suraksha Yojana (JSY)",
                        portalUrl = "https://nhm.gov.in",
                        confirmationSpeech = "Opening the Janani Suraksha Yojana portal for institutional delivery and maternity support.",
                        initialPrompt = "Let's explore maternity benefits and hospital incentive registration under Janani Suraksha Yojana.",
                        description = "Safe motherhood and hospital delivery assistance"
                    ),
                    SchemeItem(
                        schemeId = "pension",
                        schemeName = "National Social Assistance (Pension)",
                        portalUrl = "https://nsap.nic.in",
                        confirmationSpeech = "Opening the National Pension portal. Let's check your pension payment and beneficiary status.",
                        initialPrompt = "Please enter your application number or bank account number to track pension status.",
                        description = "Old age, widow, and disability social pensions"
                    )
                )
            } else {
                DEFAULT_CURATED_SCHEMES
            }
        }

        fun getBadgeForScheme(schemeId: String, isEnglish: Boolean = false): String {
            return if (isEnglish) {
                when (schemeId) {
                    "ladli_behna" -> "LB"
                    "pm_kisan" -> "PK"
                    "ration_card" -> "RC"
                    "ayushman_bharat" -> "AB"
                    "janani_suraksha" -> "JS"
                    "pension" -> "PN"
                    else -> "SC"
                }
            } else {
                when (schemeId) {
                    "ladli_behna" -> "ला"
                    "pm_kisan" -> "कि"
                    "ration_card" -> "रा"
                    "ayushman_bharat" -> "आ"
                    "janani_suraksha" -> "ज"
                    "pension" -> "पें"
                    else -> "य"
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SchemeViewHolder {
        val binding = ItemSchemeCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SchemeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SchemeViewHolder, position: Int) {
        holder.bind(schemes[position])
    }

    override fun getItemCount(): Int = schemes.size

    inner class SchemeViewHolder(
        private val binding: ItemSchemeCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(scheme: SchemeItem) {
            val isEnglish = scheme.schemeName.all { it.code < 128 }
            binding.tvSchemeBadge.text = getBadgeForScheme(scheme.schemeId, isEnglish)
            binding.tvSchemeTitle.text = scheme.schemeName
            binding.tvSchemeDesc.text = scheme.description.ifEmpty {
                "योजना विवरण एवं ऑनलाइन आवेदन"
            }

            binding.cardScheme.setOnClickListener {
                onSchemeSelected(scheme)
            }

            binding.chipViewScheme.setOnClickListener {
                onSchemeSelected(scheme)
            }
        }
    }
}
