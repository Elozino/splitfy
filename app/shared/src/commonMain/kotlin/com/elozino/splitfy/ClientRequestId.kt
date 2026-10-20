package com.elozino.splitfy

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A fresh idempotency key for one user intent (one expense, one payment).
 *
 * The server returns the original record when it sees a key twice, so the caller
 * must keep the key across retries of the same intent and mint a new one for the
 * next. Never derive it from the form's contents: two genuine payments of the same
 * amount to the same person would then collapse into one.
 */
@OptIn(ExperimentalUuidApi::class)
fun newClientRequestId(): String = Uuid.random().toString()
