package com.sahaayika.app

import com.sahaayika.app.privacy.FieldState
import com.sahaayika.app.privacy.PrivacyRedactor
import com.sahaayika.app.privacy.RecognizedElement
import com.sahaayika.app.privacy.RecognizedLine
import com.sahaayika.app.privacy.RedactRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyRedactorTest {

    // Helper to create RecognizedLine
    private fun createLine(text: String, top: Int = 100, bottom: Int = 140, left: Int = 50, right: Int = 500): RecognizedLine {
        val rect = RedactRect(left, top, right, bottom)
        val elements = text.split(" ").mapIndexed { idx, word ->
            val elemLeft = left + (idx * 60)
            RecognizedElement(word, RedactRect(elemLeft, top, elemLeft + 50, bottom))
        }
        return RecognizedLine(text, rect, elements)
    }

    @Test
    fun testAadhaarRedaction_withSpacesAndWithout() {
        val line1 = createLine("Your Aadhaar is 2345 6789 0123 for verification")
        val blackouts1 = PrivacyRedactor.findPiiBlackouts(listOf(line1))
        assertEquals(1, blackouts1.size)

        val line2 = createLine("Aadhaar: 987654321098")
        val blackouts2 = PrivacyRedactor.findPiiBlackouts(listOf(line2))
        assertEquals(1, blackouts2.size)

        // Invalid: Starts with 0 or 1 (not valid Aadhaar)
        val lineInvalid = createLine("Sample number 0123 4567 8901")
        val blackoutsInvalid = PrivacyRedactor.findPiiBlackouts(listOf(lineInvalid))
        assertEquals(0, blackoutsInvalid.size)
    }

    @Test
    fun testPanRedaction() {
        val line = createLine("PAN Card Number: ABCDE1234F registered")
        val blackouts = PrivacyRedactor.findPiiBlackouts(listOf(line))
        assertEquals(1, blackouts.size)
    }

    @Test
    fun testIfscRedaction() {
        val line = createLine("Bank IFSC Code: SBIN0001234")
        val blackouts = PrivacyRedactor.findPiiBlackouts(listOf(line))
        // Both IFSC regex and anchor check will safely flag the IFSC
        assertTrue(blackouts.isNotEmpty())
    }

    @Test
    fun testIndianMobileRedaction() {
        val line = createLine("Contact mobile: 9876543210")
        val blackouts = PrivacyRedactor.findPiiBlackouts(listOf(line))
        assertEquals(1, blackouts.size)

        // Invalid: starts with 4
        val lineInvalid = createLine("Invalid phone: 4876543210")
        val blackoutsInvalid = PrivacyRedactor.findPiiBlackouts(listOf(lineInvalid))
        assertEquals(0, blackoutsInvalid.size)
    }

    @Test
    fun testVoterIdRedaction() {
        val line = createLine("Voter ID EPIC: ABC1234567")
        val blackouts = PrivacyRedactor.findPiiBlackouts(listOf(line))
        assertEquals(1, blackouts.size)
    }

    @Test
    fun testPaymentCardRedaction() {
        val line = createLine("Card: 4111 2222 3333 4444 exp 12/28")
        val blackouts = PrivacyRedactor.findPiiBlackouts(listOf(line))
        assertTrue(blackouts.isNotEmpty())
    }

    @Test
    fun testAnchorBasedRedaction_BankAccountInSameLine() {
        val lineEnglish = createLine("Bank Account No: 12345678901234")
        val blackoutsEnglish = PrivacyRedactor.findPiiBlackouts(listOf(lineEnglish))
        assertEquals(1, blackoutsEnglish.size)

        val lineHindi = createLine("खाता संख्या: 987654321012345")
        val blackoutsHindi = PrivacyRedactor.findPiiBlackouts(listOf(lineHindi))
        assertEquals(1, blackoutsHindi.size)
    }

    @Test
    fun testAnchorBasedRedaction_NumberInAdjacentLineBelow() {
        // Anchor in line 1, 14-digit bank account in line 2 directly below
        val anchorLine = createLine("Bank Account Number:", top = 100, bottom = 130)
        val numberLine = createLine("12345678901234", top = 140, bottom = 170)

        val blackouts = PrivacyRedactor.findPiiBlackouts(listOf(anchorLine, numberLine))
        assertEquals(1, blackouts.size)
        // Blackout must cover the number line
        assertEquals(numberLine.boundingBox.top, blackouts[0].top)
    }

    @Test
    fun testAnchorBasedRedaction_RationCard() {
        val line = createLine("राशन कार्ड संख्या: 1029384756123")
        val blackouts = PrivacyRedactor.findPiiBlackouts(listOf(line))
        assertEquals(1, blackouts.size)
    }

    @Test
    fun testFieldState_EmptyWithPlaceholderCues() {
        // Field 1: "Aadhaar: Enter Aadhaar Number" -> isFilled = false
        val line1 = createLine("Aadhaar: Enter Aadhaar Number")
        val states1 = PrivacyRedactor.analyzeFieldStates(listOf(line1), emptyList())
        assertEquals(1, states1.size)
        assertEquals("Aadhaar", states1[0].label)
        assertFalse(states1[0].isFilled)

        // Field 2: "मोबाइल: यहाँ लिखें" -> isFilled = false
        val line2 = createLine("मोबाइल: यहाँ लिखें")
        val states2 = PrivacyRedactor.analyzeFieldStates(listOf(line2), emptyList())
        assertEquals(1, states2.size)
        assertFalse(states2[0].isFilled)

        // Field 3: "Account: optional" -> isFilled = false
        val line3 = createLine("Account: optional")
        val states3 = PrivacyRedactor.analyzeFieldStates(listOf(line3), emptyList())
        assertEquals(1, states3.size)
        assertFalse(states3[0].isFilled)
    }

    @Test
    fun testFieldState_FilledWithUserData() {
        // User entered a name
        val line = createLine("Name: Sunita Devi")
        val states = PrivacyRedactor.analyzeFieldStates(listOf(line), emptyList())
        assertEquals(1, states.size)
        assertEquals("Name", states[0].label)
        assertTrue(states[0].isFilled)
    }

    @Test
    fun testFieldState_FilledWhenBlackoutApplied() {
        // User entered Aadhaar, which got redacted by blackedOutAreas
        val line = createLine("Aadhaar: 2345 6789 0123", top = 100, bottom = 140, left = 50, right = 400)
        val blackouts = PrivacyRedactor.findPiiBlackouts(listOf(line))
        assertTrue(blackouts.isNotEmpty())

        val states = PrivacyRedactor.analyzeFieldStates(listOf(line), blackouts)
        assertEquals(1, states.size)
        assertEquals("Aadhaar", states[0].label)
        // Since blackout was applied to that area, is_filled must be true!
        assertTrue(states[0].isFilled)
    }

    @Test
    fun testMultipleFieldsStateAnalysis() {
        val labelAadhaar = createLine("Aadhaar:", top = 100, bottom = 130)
        val valueAadhaar = createLine("2345 6789 0123", top = 140, bottom = 170)

        val labelMobile = createLine("Mobile:", top = 200, bottom = 230)
        val valueMobile = createLine("Type mobile number", top = 240, bottom = 270)

        val lines = listOf(labelAadhaar, valueAadhaar, labelMobile, valueMobile)
        val blackouts = PrivacyRedactor.findPiiBlackouts(lines)

        val states = PrivacyRedactor.analyzeFieldStates(lines, blackouts)
        assertEquals(2, states.size)

        val aadhaarState = states.find { it.label == "Aadhaar" }
        val mobileState = states.find { it.label == "Mobile" }

        assertTrue(aadhaarState!!.isFilled)
        assertFalse(mobileState!!.isFilled)
    }
}
