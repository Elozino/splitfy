package com.elozino.splitfy.screens.contacts

import com.elozino.splitfy.screens.contacts.main.ContactStep
import com.elozino.splitfy.screens.contacts.main.ContactsLayout
import com.elozino.splitfy.screens.contacts.main.contactStepInOnePane
import com.elozino.splitfy.screens.contacts.main.contactsLayoutFor
import com.elozino.splitfy.screens.contacts.main.showsBottomBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactsLayoutTest {

    @Test
    fun `one pane shows every step on its own as on a phone`() {
        ContactStep.entries.forEach { step ->
            assertEquals(ContactsLayout.Single(step), contactsLayoutFor(step, twoPanes = false))
        }
    }

    @Test
    fun `two panes put the list beside the contact`() {
        assertEquals(ContactsLayout.ListDetail(ContactStep.LIST), contactsLayoutFor(ContactStep.LIST, twoPanes = true))
        // A contact opened on a phone, then the window widened: the list comes back beside it.
        assertEquals(ContactsLayout.ListDetail(ContactStep.LIST), contactsLayoutFor(ContactStep.DETAIL, twoPanes = true))
    }

    @Test
    fun `two panes keep a search in the list pane`() {
        assertEquals(ContactsLayout.ListDetail(ContactStep.SEARCH), contactsLayoutFor(ContactStep.SEARCH, twoPanes = true))
    }

    @Test
    fun `adding a contact stays one screen even with room for two`() {
        assertEquals(ContactsLayout.Single(ContactStep.ADD_CONTACT), contactsLayoutFor(ContactStep.ADD_CONTACT, twoPanes = true))
        assertEquals(ContactsLayout.Single(ContactStep.SEARCH_RESULT), contactsLayoutFor(ContactStep.SEARCH_RESULT, twoPanes = true))
    }

    @Test
    fun `narrowing with a contact open gives it the screen`() {
        assertEquals(ContactStep.DETAIL, contactStepInOnePane(ContactStep.LIST, hasSelection = true))
        assertEquals(ContactStep.DETAIL, contactStepInOnePane(ContactStep.SEARCH, hasSelection = true))
    }

    @Test
    fun `narrowing with nothing open changes nothing`() {
        ContactStep.entries.forEach { step ->
            assertEquals(step, contactStepInOnePane(step, hasSelection = false))
        }
        assertEquals(ContactStep.ADD_CONTACT, contactStepInOnePane(ContactStep.ADD_CONTACT, hasSelection = true))
    }

    @Test
    fun `the bottom bar stays while the list is on screen`() {
        assertTrue(ContactsLayout.Single(ContactStep.LIST).showsBottomBar())
        assertTrue(ContactsLayout.ListDetail(ContactStep.LIST).showsBottomBar())
        assertTrue(ContactsLayout.ListDetail(ContactStep.SEARCH).showsBottomBar())
        assertFalse(ContactsLayout.Single(ContactStep.DETAIL).showsBottomBar())
        assertFalse(ContactsLayout.Single(ContactStep.SEARCH).showsBottomBar())
    }
}
