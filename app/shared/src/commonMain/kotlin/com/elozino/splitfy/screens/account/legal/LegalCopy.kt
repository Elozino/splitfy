package com.elozino.splitfy.screens.account.legal

/**
 * The text of the Terms of Service, Privacy Policy and FAQ.
 *
 * DRAFT — PENDING LEGAL REVIEW. This is a sensible starting point written for how
 * splitfy actually works today (record-only settlements, no money held, account
 * deletion anonymises), not legal advice. Before release a lawyer should review it
 * for the markets you launch in, fill in the bracketed items, and then set
 * [LEGAL_REVIEW_PENDING] to false to remove the in-app draft banner.
 *
 * If you also publish these pages on the web (both stores ask for a privacy policy
 * URL), keep the two in sync — this file is the source.
 */
object LegalCopy {

    /** Shows a "draft, pending legal review" banner on the legal screens while true. */
    const val LEGAL_REVIEW_PENDING: Boolean = true

    const val LAST_UPDATED = "[date of legal sign-off]"

    val terms: List<LegalSection> = listOf(
        LegalSection(
            "1. About these terms",
            "These terms govern your use of the splitfy app and service (\"splitfy\", \"we\", \"us\"), " +
                "operated by [legal entity name and registered address]. By creating an account or using " +
                "splitfy you agree to them. If you do not agree, do not use splitfy.",
        ),
        LegalSection(
            "2. What splitfy does",
            "splitfy helps groups of people keep track of shared expenses and work out who owes whom. " +
                "splitfy is a record-keeping tool: it does not hold, transfer or receive money on your " +
                "behalf. When you \"settle up\", you pay the other person outside splitfy (for example by " +
                "bank transfer or cash) and record that payment in the app.",
        ),
        LegalSection(
            "3. Your account",
            "You must be at least [13/16] years old to use splitfy. Give accurate information, keep your " +
                "password private, and tell us at the support address below if you think someone else has " +
                "accessed your account. You are responsible for activity on your account.",
        ),
        LegalSection(
            "4. Groups and the information you add",
            "Expenses, payments, notes and receipts you add to a group are visible to every member of " +
                "that group. Only add people who have agreed to share expenses with you, and only add " +
                "information you have the right to share. You keep ownership of what you add; you give us " +
                "permission to store and display it to your group members so the service works.",
        ),
        LegalSection(
            "5. Balances are not a debt we enforce",
            "Balances shown in splitfy are calculated from what members record. They are not a statement " +
                "of a legal debt, and we do not collect, guarantee or enforce payments between members. " +
                "Check amounts before you pay anyone, and settle disagreements directly with the people " +
                "involved.",
        ),
        LegalSection(
            "6. Acceptable use",
            "Do not use splitfy to break the law, to harass anyone, to send spam, or to interfere with " +
                "the service (for example by probing, scraping or overloading it). We may suspend or close " +
                "accounts that do.",
        ),
        LegalSection(
            "7. Deleting your account",
            "You can delete your account at any time from Account > Security > Delete Account, once " +
                "your balances in every group are settled. Your profile and sign-in details are deleted; " +
                "expenses you were part of stay in their groups, attributed to \"Deleted user\", so other " +
                "members' balances remain correct. See the Privacy Policy for details.",
        ),
        LegalSection(
            "8. Availability and changes",
            "We work to keep splitfy available and accurate but provide it \"as is\", without warranties " +
                "of any kind, to the extent the law allows. We may change or discontinue features. If we " +
                "change these terms in a way that matters, we will tell you in the app before the change " +
                "takes effect.",
        ),
        LegalSection(
            "9. Liability",
            "To the extent the law allows, we are not liable for indirect or consequential losses, or for " +
                "losses caused by amounts recorded incorrectly by group members. Nothing in these terms " +
                "limits liability that cannot be limited by law. [Liability cap to be confirmed by counsel.]",
        ),
        LegalSection(
            "10. Governing law and contact",
            "These terms are governed by the laws of [jurisdiction]. Questions about them: " +
                "${SupportContact.EMAIL}.",
        ),
    )

    val privacy: List<LegalSection> = listOf(
        LegalSection(
            "1. Who we are",
            "splitfy is operated by [legal entity name and registered address], the controller of the " +
                "personal data described here. Contact us about privacy at ${SupportContact.EMAIL}.",
        ),
        LegalSection(
            "2. What we collect",
            "• Account details: your name, email address, password (stored only as a one-way hash), and " +
                "optionally your phone number and profile photo.\n" +
                "• Sign-in with Google or Apple: the account identifier and email address that provider " +
                "shares with us.\n" +
                "• What you add: groups, members, expenses, splits, notes, receipt photos and recorded " +
                "payments.\n" +
                "• Device and diagnostics: a notification token if you allow notifications, and — when " +
                "crash reporting is enabled — crash reports containing the app version, device model, " +
                "operating system and the error. Crash reports never include your email, name or " +
                "screenshots.\n" +
                "• Service logs: request times, response codes and an anonymous request id, kept to run " +
                "and secure the service. We do not log what you search for.",
        ),
        LegalSection(
            "3. How we use it",
            "To provide splitfy (show your groups and balances, sync between your devices, send the " +
                "notifications and emails you need such as verification and password-reset codes), to keep " +
                "it secure, to fix problems, and to meet legal obligations. We do not sell your data and " +
                "we do not use it for advertising.",
        ),
        LegalSection(
            "4. Who can see it",
            "Members of a group see your name, photo and everything recorded in that group. Someone can " +
                "find you to add to a group only by your exact, verified email address. We share data with " +
                "service providers that run parts of splitfy for us, under contracts that limit their use " +
                "of it: our hosting and database provider [name], our email provider (Resend), our " +
                "crash-reporting provider (Sentry, when enabled), and Google and Apple for sign-in and push " +
                "notifications. We may disclose data when the law requires it.",
        ),
        LegalSection(
            "5. Where it is stored",
            "Data is stored in [region] by our hosting provider. Where providers process it in other " +
                "countries, we rely on [appropriate safeguards, e.g. standard contractual clauses].",
        ),
        LegalSection(
            "6. How long we keep it",
            "We keep your account data while your account exists. When you delete your account we delete " +
                "your name, email, phone number, photo, password, linked sign-ins, sessions and " +
                "notifications straight away. Expenses and payments you were part of stay in their groups " +
                "under \"Deleted user\", because removing them would change other people's balances. " +
                "Encrypted backups are kept for up to [N] days and then overwritten. Service logs are " +
                "kept for up to [N] days.",
        ),
        LegalSection(
            "7. Your rights",
            "You can see and correct your profile in the app and delete your account from Account > " +
                "Security. Depending on where you live you may also have rights to a copy of your data, to " +
                "object to or restrict processing, and to complain to your data-protection authority " +
                "[e.g. the Nigeria Data Protection Commission]. Email ${SupportContact.EMAIL} to exercise " +
                "them; we answer within [30] days.",
        ),
        LegalSection(
            "8. Security",
            "Connections to splitfy are encrypted, passwords are hashed, and sign-in sessions can be " +
                "revoked. No system is perfectly secure; if we learn of a breach affecting your data we " +
                "will tell you as the law requires.",
        ),
        LegalSection(
            "9. Children",
            "splitfy is not intended for children under [13/16], and we do not knowingly collect their data.",
        ),
        LegalSection(
            "10. Changes",
            "If we change this policy in a way that matters, we will tell you in the app before the change " +
                "takes effect.",
        ),
    )

    val faqCategories: List<String> = listOf("General", "Groups", "Settling up", "Account")

    val faqs: List<FaqEntry> = listOf(
        FaqEntry(
            "General",
            "What is splitfy?",
            "splitfy keeps track of shared costs — trips, rent, dinners — and works out who owes whom, " +
                "so nobody has to keep a spreadsheet.",
        ),
        FaqEntry(
            "General",
            "Is splitfy free?",
            "Yes. Everything you need to track and settle shared expenses is free.",
        ),
        FaqEntry(
            "General",
            "Does splitfy move money?",
            "No. splitfy records what was spent and what has been paid back, but the payments themselves " +
                "happen outside the app — bank transfer, cash, whatever your group prefers.",
        ),
        FaqEntry(
            "Groups",
            "How do I add people to a group?",
            "Open the group and share its invite link, or add someone by the email address they signed " +
                "up with. Anyone with the link can join until you reset it from the group's settings.",
        ),
        FaqEntry(
            "Groups",
            "How are expenses split?",
            "Equally, by exact amounts, by percentage or by shares. splitfy handles the rounding so the " +
                "parts always add up to the total.",
        ),
        FaqEntry(
            "Groups",
            "Can I leave a group?",
            "Yes, once your balance in that group is zero. Settle up first so nobody is left with a debt " +
                "they cannot collect.",
        ),
        FaqEntry(
            "Settling up",
            "How do I settle up?",
            "Pay the person outside the app, then record the payment in the group. They confirm it, and " +
                "both balances update.",
        ),
        FaqEntry(
            "Settling up",
            "Why do the suggested payments differ from individual expenses?",
            "splitfy simplifies the debts in a group so everyone settles with as few payments as possible. " +
                "The totals are the same.",
        ),
        FaqEntry(
            "Account",
            "I forgot my password.",
            "On the sign-in screen tap \"Forgot password\" and we will email you a code to set a new one.",
        ),
        FaqEntry(
            "Account",
            "How do I delete my account?",
            "Go to Account > Security > Delete Account. You need to be settled up in every group first. " +
                "Your profile and sign-in details are deleted; past expenses stay in their groups as " +
                "\"Deleted user\" so everyone else's balances still add up.",
        ),
        FaqEntry(
            "Account",
            "How do I contact support?",
            "Email ${SupportContact.EMAIL}, or use Account > Help & Support > Contact Support.",
        ),
    )
}

data class LegalSection(val title: String, val body: String)

data class FaqEntry(val category: String, val question: String, val answer: String)
