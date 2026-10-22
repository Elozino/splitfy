package com.elozino.splitfy.screens.account.linked_accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elozino.splitfy.DataResult
import com.elozino.splitfy.api.dto.LinkedAccountsResponse
import com.elozino.splitfy.api.dto.LinkedIdentity
import com.elozino.splitfy.api.dto.OAuthProvider
import com.elozino.splitfy.repositories.AccountRepository
import com.elozino.splitfy.social.SocialSignInResult
import com.elozino.splitfy.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LinkedAccountsState(
    val isLoading: Boolean = true,
    val linked: LinkedAccountsResponse? = null,
    /** Set when the first load failed and there is nothing to show. */
    val loadError: String? = null,
    val errorMessage: String? = null,
    /** The provider being linked or unlinked; its row shows a spinner. */
    val busyProvider: OAuthProvider? = null,
    /** Asks before unlinking; the row's provider. */
    val confirmUnlink: OAuthProvider? = null,
) {
    fun identity(provider: OAuthProvider): LinkedIdentity? = linked?.identities?.firstOrNull { it.provider == provider }

    /**
     * Rows to show: providers both this build and the server support, plus any already
     * linked (an Apple ID linked on iPhone still shows, and can be removed, on Android).
     */
    fun providers(platform: List<OAuthProvider>): List<OAuthProvider> {
        val server = linked?.availableProviders.orEmpty()
        val linkedProviders = linked?.identities.orEmpty().map { it.provider }
        return OAuthProvider.entries.filter { (it in platform && it in server) || it in linkedProviders }
    }

    fun canLink(provider: OAuthProvider, platform: List<OAuthProvider>): Boolean =
        provider in platform && provider in linked?.availableProviders.orEmpty()

    /** Mirrors the server's rule: never remove the last way to sign in. */
    fun canUnlink(provider: OAuthProvider): Boolean {
        val linked = linked ?: return false
        return linked.hasPassword || linked.identities.any { it.provider != provider }
    }
}

/** Linked Accounts: which Google/Apple identities can sign into this account. */
class LinkedAccountsViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LinkedAccountsState())
    val state: StateFlow<LinkedAccountsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            when (val result = accountRepository.linkedAccounts()) {
                is DataResult.Success -> _state.update { it.copy(isLoading = false, linked = result.data) }
                is DataResult.Failure -> _state.update {
                    it.copy(isLoading = false, loadError = result.error.userMessage().takeIf { _ -> it.linked == null })
                }
            }
        }
    }

    /** Called with whatever the platform sign-in sheet returned. */
    fun onSignInResult(provider: OAuthProvider, result: SocialSignInResult) {
        when (result) {
            SocialSignInResult.Cancelled -> _state.update { it.copy(busyProvider = null, errorMessage = null) }
            is SocialSignInResult.Failure -> _state.update { it.copy(busyProvider = null, errorMessage = result.message) }
            is SocialSignInResult.Success -> viewModelScope.launch {
                _state.update { it.copy(busyProvider = provider, errorMessage = null) }
                apply(accountRepository.link(provider, result.idToken, result.nonce))
            }
        }
    }

    /** Marks the row busy while the platform sheet is up. */
    fun onLinkStarted(provider: OAuthProvider) = _state.update { it.copy(busyProvider = provider, errorMessage = null) }

    fun askToUnlink(provider: OAuthProvider) = _state.update { it.copy(confirmUnlink = provider) }

    fun dismissUnlink() = _state.update { it.copy(confirmUnlink = null) }

    fun unlink(provider: OAuthProvider) {
        if (_state.value.busyProvider != null) return
        viewModelScope.launch {
            _state.update { it.copy(confirmUnlink = null, busyProvider = provider, errorMessage = null) }
            apply(accountRepository.unlink(provider))
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    private fun apply(result: DataResult<LinkedAccountsResponse>) = when (result) {
        is DataResult.Success -> _state.update { it.copy(busyProvider = null, linked = result.data) }
        is DataResult.Failure -> _state.update { it.copy(busyProvider = null, errorMessage = result.error.userMessage()) }
    }
}
