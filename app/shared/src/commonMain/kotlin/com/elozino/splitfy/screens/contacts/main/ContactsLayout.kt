package com.elozino.splitfy.screens.contacts.main

/** How Contacts lays out a [ContactStep] in the current window. */
internal sealed interface ContactsLayout {
    /** One screen at a time, as on a phone. */
    data class Single(val step: ContactStep) : ContactsLayout

    /**
     * Google's list-detail layout: [listStep] (the list, or a search of it) beside the
     * open contact, or beside a placeholder when none is open.
     */
    data class ListDetail(val listStep: ContactStep) : ContactsLayout
}

/**
 * Picks the layout for [step]. With room for two panes the list and a contact sit side
 * by side; adding a contact is a short form of its own, so it stays one screen.
 */
internal fun contactsLayoutFor(step: ContactStep, twoPanes: Boolean): ContactsLayout = when {
    !twoPanes -> ContactsLayout.Single(step)
    step == ContactStep.SEARCH -> ContactsLayout.ListDetail(ContactStep.SEARCH)
    step == ContactStep.LIST || step == ContactStep.DETAIL -> ContactsLayout.ListDetail(ContactStep.LIST)
    else -> ContactsLayout.Single(step)
}

/**
 * The step to show once the window narrows to one pane. A contact open beside the list
 * or search takes the screen, the way Groups keeps an open group; anything else stays.
 */
internal fun contactStepInOnePane(step: ContactStep, hasSelection: Boolean): ContactStep =
    if (hasSelection && (step == ContactStep.LIST || step == ContactStep.SEARCH)) ContactStep.DETAIL else step

/** The bottom bar stays while the contacts list is on screen, alone or beside a contact. */
internal fun ContactsLayout.showsBottomBar(): Boolean = when (this) {
    is ContactsLayout.ListDetail -> true
    is ContactsLayout.Single -> step == ContactStep.LIST
}
