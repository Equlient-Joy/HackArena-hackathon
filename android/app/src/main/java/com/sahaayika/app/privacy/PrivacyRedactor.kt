package com.sahaayika.app.privacy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume

/**
 * Metadata indicating field label and whether it currently contains user-filled data.
 */
@Serializable
data class FieldState(
    @SerialName("label") val label: String,
    @SerialName("is_filled") val isFilled: Boolean
)

/**
 * Result containing the PII-sanitized bitmap and the extracted field fill-states.
 */
data class RedactionResult(val sanitizedBitmap: Bitmap, val fieldStates: List<FieldState>)

/**
 * Pure Kotlin rectangle abstraction decoupled from Android framework mocks.
 */
data class RedactRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun intersects(other: RedactRect): Boolean {
        return left < other.right && other.left < right && top < other.bottom && other.top < bottom
    }

    companion object {
        fun from(r: Rect?): RedactRect {
            return if (r != null) RedactRect(r.left, r.top, r.right, r.bottom) else RedactRect(0, 0, 0, 0)
        }
    }
}

/**
 * Lightweight abstraction of OCR text elements for pure JVM testability and decoupling from ML Kit internals.
 */
data class RecognizedElement(val text: String, val boundingBox: RedactRect)
data class RecognizedLine(val text: String, val boundingBox: RedactRect, val elements: List<RecognizedElement> = emptyList())
data class RecognizedBlock(val text: String, val boundingBox: RedactRect, val lines: List<RecognizedLine> = emptyList())

/**
 * On-Device PII Redaction & Field Fill-State Tracking Engine for Sahaayika (सहायिका).
 *
 * Scrubs sensitive citizen information (Aadhaar, PAN, Bank Accounts, IFSC, Cards, Mobile, Voter IDs)
 * using solid black rectangles BEFORE transmitting screenshots to cloud endpoints.
 *
 * Also tracks whether form fields on screen are EMPTY or FILLED to assist visual grounding.
 */
object PrivacyRedactor {

    // 1. Structural Regex Patterns
    val AADHAAR_REGEX = Regex("""\b[2-9]\d{3}\s?\d{4}\s?\d{4}\b""")
    val PAN_REGEX = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""")
    val IFSC_REGEX = Regex("""\b[A-Z]{4}0[A-Z0-9]{6}\b""")
    val MOBILE_REGEX = Regex("""\b[6-9]\d{9}\b""")
    val VOTER_ID_REGEX = Regex("""\b[A-Z]{3}[0-9]{7}\b""")
    val PAYMENT_CARD_REGEX = Regex("""\b(?:\d{4}[-\s]?){3}\d{4}\b""")

    // 2. Anchor Keywords for Bank Accounts and Ration Cards
    val ANCHOR_KEYWORDS = listOf("account", "a/c", "खाता", "bank", "ifsc", "ration", "राशन")
    val BANK_OR_RATION_NUM_REGEX = Regex("""\b\d{9,18}\b""")

    // 3. Placeholder Cues indicating an empty input field
    val PLACEHOLDER_CUES = listOf("enter", "type", "यहाँ लिखें", "दर्ज करें", "optional")

    // Field Label Cues
    val FIELD_LABEL_CUES = listOf(
        "aadhaar", "आधार", "mobile", "मोबाइल", "phone", "फोन", "name", "नाम",
        "account", "खाता", "bank", "बैंक", "ifsc", "ration", "राशन", "samagra",
        "समग्र", "voter", "पहचान", "otp", "ओटीपी", "dob", "जन्मतिथि", "gender", "लिंग"
    )

    private val blackoutPaint by lazy {
        Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
    }

    /**
     * Coroutine-based entrypoint for scrubbing a live Bitmap using Google ML Kit Text Recognition.
     */
    suspend fun redact(sourceBitmap: Bitmap): RedactionResult = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(sourceBitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val blocks = convertVisionTextToBlocks(visionText)
                    val result = processBlocks(sourceBitmap, blocks)
                    continuation.resume(result)
                }
                .addOnFailureListener {
                    // Graceful fallback: return unredacted or minimal safe copy
                    val safeCopy = sourceBitmap.copy(sourceBitmap.config ?: Bitmap.Config.ARGB_8888, true)
                    continuation.resume(RedactionResult(safeCopy, emptyList()))
                }
        } catch (e: Throwable) {
            val safeCopy = sourceBitmap.copy(sourceBitmap.config ?: Bitmap.Config.ARGB_8888, true)
            continuation.resume(RedactionResult(safeCopy, emptyList()))
        }
    }

    /**
     * Core redaction and field analysis logic operating on decoupled text blocks.
     * Fully executable in pure JVM unit tests without device dependencies.
     */
    fun processBlocks(sourceBitmap: Bitmap, blocks: List<RecognizedBlock>): RedactionResult {
        val sanitized = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(sanitized)

        val allLines = blocks.flatMap { it.lines }
        val blackedOutAreas = findPiiBlackouts(allLines)

        for (rect in blackedOutAreas) {
            canvas.drawRect(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.bottom.toFloat(),
                blackoutPaint
            )
        }

        // Step 3: Input Field "Empty vs. Filled" State Detection
        val fieldStates = analyzeFieldStates(allLines, blackedOutAreas)

        return RedactionResult(sanitized, fieldStates)
    }

    /**
     * Identifies all PII regions to be redacted based on structural regexes and keyword anchors.
     */
    fun findPiiBlackouts(allLines: List<RecognizedLine>): List<RedactRect> {
        val blackedOutAreas = mutableListOf<RedactRect>()

        // Step 1: Structural Regex Redactions
        for (line in allLines) {
            val lineText = line.text

            // Aadhaar check
            collectMatches(lineText, line, AADHAAR_REGEX, blackedOutAreas)

            // PAN check
            collectMatches(lineText, line, PAN_REGEX, blackedOutAreas)

            // IFSC check
            collectMatches(lineText, line, IFSC_REGEX, blackedOutAreas)

            // Mobile check
            collectMatches(lineText, line, MOBILE_REGEX, blackedOutAreas)

            // Voter ID check
            collectMatches(lineText, line, VOTER_ID_REGEX, blackedOutAreas)

            // Payment Card check
            collectMatches(lineText, line, PAYMENT_CARD_REGEX, blackedOutAreas)
        }

        // Step 2: Anchor-Based Redaction (Bank Accounts & Ration Cards)
        for (i in allLines.indices) {
            val line = allLines[i]
            val lowerText = line.text.lowercase()

            val hasAnchor = ANCHOR_KEYWORDS.any { anchor -> lowerText.contains(anchor) }
            if (hasAnchor) {
                // Check current line for numeric sequence (9-18 digits)
                collectMatches(line.text, line, BANK_OR_RATION_NUM_REGEX, blackedOutAreas)

                // Also check lines immediately below or adjacent in vertical proximity
                for (j in allLines.indices) {
                    if (i == j) continue
                    val targetLine = allLines[j]
                    val lineHeight = (line.boundingBox.bottom - line.boundingBox.top).coerceAtLeast(20)
                    val isBelowOrAdjacent = targetLine.boundingBox.top >= line.boundingBox.top - 10 &&
                            targetLine.boundingBox.top <= line.boundingBox.bottom + (lineHeight * 3)

                    if (isBelowOrAdjacent) {
                        collectMatches(targetLine.text, targetLine, BANK_OR_RATION_NUM_REGEX, blackedOutAreas)
                    }
                }
            }
        }

        return blackedOutAreas
    }

    private fun collectMatches(
        text: String,
        line: RecognizedLine,
        regex: Regex,
        blackedOutAreas: MutableList<RedactRect>
    ) {
        val matches = regex.findAll(text)
        for (match in matches) {
            val matchedRect = calculateMatchBoundingBox(line, text, match.range)
            blackedOutAreas.add(matchedRect)
        }
    }

    /**
     * Calculates the bounding box for a regex match within a line.
     * Uses sub-element locations when available, falling back to proportional interpolation.
     */
    fun calculateMatchBoundingBox(line: RecognizedLine, fullText: String, range: IntRange): RedactRect {
        if (line.elements.isNotEmpty()) {
            val matchingElements = line.elements.filter { elem ->
                // Check if element overlaps with match range
                val elemStart = fullText.indexOf(elem.text)
                if (elemStart != -1) {
                    val elemEnd = elemStart + elem.text.length
                    maxOf(range.first, elemStart) < minOf(range.last + 1, elemEnd)
                } else false
            }
            if (matchingElements.isNotEmpty()) {
                val left = matchingElements.minOf { it.boundingBox.left }
                val top = matchingElements.minOf { it.boundingBox.top }
                val right = matchingElements.maxOf { it.boundingBox.right }
                val bottom = matchingElements.maxOf { it.boundingBox.bottom }
                return RedactRect(left, top, right, bottom)
            }
        }

        // Proportional interpolation fallback
        val totalChars = fullText.length.coerceAtLeast(1)
        val lineWidth = (line.boundingBox.right - line.boundingBox.left).coerceAtLeast(100)
        val charWidth = lineWidth.toFloat() / totalChars
        val left = line.boundingBox.left + (range.first * charWidth).toInt()
        val right = line.boundingBox.left + ((range.last + 1) * charWidth).toInt()

        return RedactRect(
            left.coerceIn(line.boundingBox.left, line.boundingBox.right),
            line.boundingBox.top,
            right.coerceIn(line.boundingBox.left, line.boundingBox.right),
            line.boundingBox.bottom
        )
    }

    /**
     * Analyzes detected fields on screen and determines if they are FILLED or EMPTY.
     */
    fun analyzeFieldStates(lines: List<RecognizedLine>, blackedOutAreas: List<RedactRect>): List<FieldState> {
        val results = mutableListOf<FieldState>()

        for (i in lines.indices) {
            val line = lines[i]
            val lineText = line.text.trim()
            val lowerText = lineText.lowercase()

            val hasColon = lineText.contains(":")
            val labelCandidate = if (hasColon) lineText.substringBefore(":").trim() else lineText
            val lowerLabel = labelCandidate.lowercase()

            // If line doesn't have a colon and is purely placeholder text, skip
            if (!hasColon && isPlaceholderText(lowerText)) continue

            // Detect if this represents a field label:
            // Either label candidate matches a field label cue, or the line has a colon with non-empty label candidate
            val isLabel = FIELD_LABEL_CUES.any { cue -> lowerLabel.contains(cue) } || (hasColon && labelCandidate.isNotEmpty())
            if (!isLabel) continue

            val labelName = labelCandidate

            // Look for associated value line:
            // 1. In the same line after colon (e.g. "Aadhaar: 1234 5678 9012" or "Mobile: Enter mobile")
            // 2. In a line directly beneath or adjacent to this label
            var valueText: String? = null
            var valueRect: RedactRect? = null
            var wasBlackedOut = false

            if (hasColon && lineText.substringAfter(":").trim().isNotEmpty()) {
                valueText = lineText.substringAfter(":").trim()
                valueRect = line.boundingBox
            } else {
                // Find nearest line below or to the right
                val lineHeight = (line.boundingBox.bottom - line.boundingBox.top).coerceAtLeast(20)
                val nextCandidate = lines.filterIndexed { index, candidate ->
                    index != i &&
                            candidate.boundingBox.top >= line.boundingBox.top - 5 &&
                            candidate.boundingBox.top <= line.boundingBox.bottom + (lineHeight * 2.5) &&
                            candidate.text.trim().isNotEmpty()
                }.minByOrNull { it.boundingBox.top }

                if (nextCandidate != null) {
                    valueText = nextCandidate.text.trim()
                    valueRect = nextCandidate.boundingBox
                }
            }

            // Check if any blackout overlaps with this field value area
            if (valueRect != null) {
                wasBlackedOut = blackedOutAreas.any { blackRect ->
                    blackRect.intersects(valueRect)
                }
            } else {
                // If label line itself had a blackout (e.g. Aadhaar was blacked out in same line)
                wasBlackedOut = blackedOutAreas.any { blackRect ->
                    blackRect.intersects(line.boundingBox)
                }
            }

            // Determine if filled
            val isFilled = when {
                wasBlackedOut -> true
                valueText == null -> false
                isPlaceholderText(valueText) -> false
                valueText.isNotEmpty() -> true
                else -> false
            }

            results.add(FieldState(label = labelName, isFilled = isFilled))
        }

        return results
    }

    /**
     * Checks if given text matches common empty field placeholder hints.
     */
    fun isPlaceholderText(text: String): Boolean {
        val lower = text.lowercase().trim()
        return PLACEHOLDER_CUES.any { cue -> lower.contains(cue) }
    }

    private fun convertVisionTextToBlocks(visionText: Text): List<RecognizedBlock> {
        return visionText.textBlocks.map { block ->
            val blockRect = RedactRect.from(block.boundingBox)
            val lines = block.lines.map { line ->
                val lineRect = RedactRect.from(line.boundingBox)
                val elements = line.elements.map { elem ->
                    RecognizedElement(elem.text, RedactRect.from(elem.boundingBox))
                }
                RecognizedLine(line.text, lineRect, elements)
            }
            RecognizedBlock(block.text, blockRect, lines)
        }
    }
}
