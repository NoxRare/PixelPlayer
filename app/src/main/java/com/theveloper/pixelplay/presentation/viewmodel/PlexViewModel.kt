package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.plex.PlexAuthManager
import com.theveloper.pixelplay.data.network.plex.PlexAuthState
import com.theveloper.pixelplay.data.network.plex.PlexLibrarySection
import com.theveloper.pixelplay.data.network.plex.PlexServer
import com.theveloper.pixelplay.data.repository.PlexMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository

/**
 * ViewModel for managing Plex authentication and music library state.
 */
@HiltViewModel
class PlexViewModel @Inject constructor(
    private val plexAuthManager: PlexAuthManager,
    private val plexMusicRepository: PlexMusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val TAG = "PlexViewModel"

    // Authentication state
    val authState: StateFlow<PlexAuthState> = plexAuthManager.authState

    // UI state for login screen
    private val _loginUiState = MutableStateFlow(PlexLoginUiState())
    val loginUiState: StateFlow<PlexLoginUiState> = _loginUiState.asStateFlow()

    // Available servers
    val availableServers: StateFlow<List<PlexServer>> = plexMusicRepository.availableServers

    // Selected server
    val selectedServer: StateFlow<PlexServer?> = plexMusicRepository.selectedServer

    // Music sections
    val musicSections: StateFlow<List<PlexLibrarySection>> = plexMusicRepository.musicSections

    // Selected music section
    val selectedMusicSection: StateFlow<PlexLibrarySection?> = plexMusicRepository.selectedMusicSection

    // Music library data
    val plexSongs: StateFlow<List<Song>> = plexMusicRepository.getPlexSongs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val plexAlbums: StateFlow<List<Album>> = plexMusicRepository.getPlexAlbums()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val plexArtists: StateFlow<List<Artist>> = plexMusicRepository.getPlexArtists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Sign in to Plex with email/username and password.
     */
    fun signIn(login: String, password: String) {
        viewModelScope.launch {
            _loginUiState.value = _loginUiState.value.copy(isLoading = true, error = null)
            
            val result = plexMusicRepository.signIn(login, password)
            
            result.fold(
                onSuccess = {
                    _loginUiState.value = _loginUiState.value.copy(isLoading = false)
                    Timber.tag(TAG).d("Sign in successful")
                    // Save username to preferences
                    val authState = plexAuthManager.authState.value
                    if (authState is PlexAuthState.Authenticated) {
                        userPreferencesRepository.setPlexUsername(authState.user.username)
                        userPreferencesRepository.setPlexEnabled(true)
                    }
                    // Automatically discover servers after sign in
                    discoverServers()
                },
                onFailure = { exception ->
                    _loginUiState.value = _loginUiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Authentication failed"
                    )
                    Timber.tag(TAG).e(exception, "Sign in failed")
                }
            )
        }
    }

    /**
     * Sign out from Plex.
     */
    fun signOut() {
        viewModelScope.launch {
            plexMusicRepository.signOut()
            // Clear preferences
            userPreferencesRepository.clearPlexSettings()
            userPreferencesRepository.setPlexEnabled(false)
        }
    }

    /**
     * Discover available Plex servers.
     */
    fun discoverServers() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            val result = plexMusicRepository.discoverServers()
            
            result.fold(
                onSuccess = { servers ->
                    _isLoading.value = false
                    Timber.tag(TAG).d("Discovered ${servers.size} servers")
                },
                onFailure = { exception ->
                    _isLoading.value = false
                    _error.value = exception.message ?: "Failed to discover servers"
                    Timber.tag(TAG).e(exception, "Failed to discover servers")
                }
            )
        }
    }

    /**
     * Select a Plex server.
     */
    fun selectServer(server: PlexServer) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            plexMusicRepository.selectServer(server)
            
            // Save server to preferences
            userPreferencesRepository.setPlexServer(server.id, server.name)
            
            // Fetch music sections for the selected server
            val result = plexMusicRepository.fetchMusicSections()
            
            result.fold(
                onSuccess = { sections ->
                    _isLoading.value = false
                    Timber.tag(TAG).d("Found ${sections.size} music sections")
                    // Auto-select if only one music section
                    if (sections.size == 1) {
                        selectMusicSection(sections.first())
                    }
                },
                onFailure = { exception ->
                    _isLoading.value = false
                    _error.value = exception.message ?: "Failed to fetch music sections"
                    Timber.tag(TAG).e(exception, "Failed to fetch music sections")
                }
            )
        }
    }

    /**
     * Select a music library section.
     */
    fun selectMusicSection(section: PlexLibrarySection) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            plexMusicRepository.selectMusicSection(section)
            
            // Save section to preferences
            userPreferencesRepository.setPlexMusicSection(section.key, section.title)
            
            _isLoading.value = false
            Timber.tag(TAG).d("Selected music section: ${section.title}")
        }
    }

    /**
     * Refresh the Plex music library.
     */
    fun refreshLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            plexMusicRepository.refreshLibrary()
            
            _isLoading.value = false
        }
    }

    /**
     * Clear any error state.
     */
    fun clearError() {
        _error.value = null
        _loginUiState.value = _loginUiState.value.copy(error = null)
    }

    /**
     * Update login form fields.
     */
    fun updateLoginField(field: LoginField, value: String) {
        _loginUiState.value = when (field) {
            LoginField.LOGIN -> _loginUiState.value.copy(login = value)
            LoginField.PASSWORD -> _loginUiState.value.copy(password = value)
        }
    }
}

/**
 * UI state for Plex login screen.
 */
data class PlexLoginUiState(
    val login: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Login form field identifiers.
 */
enum class LoginField {
    LOGIN,
    PASSWORD
}
