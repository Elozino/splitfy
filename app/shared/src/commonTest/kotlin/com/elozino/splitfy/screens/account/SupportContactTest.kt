package com.elozino.splitfy.screens.account

import com.elozino.splitfy.screens.account.legal.LegalCopy
import com.elozino.splitfy.screens.account.legal.SupportContact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportContactTest {

    @Test
    fun mailtoIsAddressedToSupportWithAnEncodedSubject() {
        val link = SupportContact.mailto("splitfy problem report", "Android 34")
        assertTrue(link.startsWith("mailto:${SupportContact.EMAIL}?subject=splitfy%20problem%20report&body="), link)
        assertTrue("Android%2034" in link, link)
        assertTrue(" " !in link, "Spaces must be encoded: $link")
    }

    @Test
    fun everyFaqBelongsToAListedCategory() {
        val categories = LegalCopy.faqCategories.toSet()
        assertEquals(emptyList(), LegalCopy.faqs.filter { it.category !in categories })
        categories.forEach { category -> assertTrue(LegalCopy.faqs.any { it.category == category }, category) }
    }
}
