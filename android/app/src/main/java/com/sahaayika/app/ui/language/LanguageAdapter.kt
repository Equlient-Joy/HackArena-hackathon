package com.sahaayika.app.ui.language

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.sahaayika.databinding.ItemLanguageBinding
import com.sahaayika.app.data.model.Language

/**
 * RecyclerView Adapter presenting the core Indic languages for citizen onboarding.
 *
 * Provides large touch target items (76dp+ min height) displaying native script badges,
 * native titles, English translations, and speaker assist triggers.
 */
class LanguageAdapter(
    val languages: List<Language> = Language.CORE_LANGUAGES,
    private val onLanguageSelected: (Language) -> Unit,
    private val onSpeakerAssist: ((Language) -> Unit)? = null
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    companion object {
        val DEFAULT_LANGUAGES: List<Language> = Language.CORE_LANGUAGES
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ItemLanguageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LanguageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(languages[position])
    }

    override fun getItemCount(): Int = languages.size

    inner class LanguageViewHolder(
        val binding: ItemLanguageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(language: Language) {
            binding.tvBadge.text = language.badge
            binding.tvNativeName.text = language.nativeName
            binding.tvEnglishName.text = language.englishName

            binding.cardLanguage.setOnClickListener {
                onLanguageSelected(language)
            }

            binding.ivSpeakerAssist.setOnClickListener {
                if (onSpeakerAssist != null) {
                    onSpeakerAssist.invoke(language)
                } else {
                    onLanguageSelected(language)
                }
            }
        }
    }
}
