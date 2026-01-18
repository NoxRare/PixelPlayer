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

    // UI state for OAuth login screen
    private val _oauthUiState = MutableStateFlow(PlexOAuthUiState())
    val oauthUiState: StateFlow<PlexOAuthUiState> = _oauthUiState.asStateFlow()

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
     * Start OAuth authentication flow.
     * Opens browser for user to authenticate and polls for completion.
     */
    fun startOAuth() {
        viewModelScope.launch {
            _oauthUiState.value = _oauthUiState.value.copy(isLoading = true, error = null)
            
            val pinResult = plexAuthManager.startOAuth()
            
            pinResult.fold(
                onSuccess = { pinResponse ->
                    val oauthUrl = plexAuthManager.buildOAuthUrl(pinResponse.id, pinResponse.code)
                    _oauthUiState.value = _oauthUiState.value.copy(
                        isLoading = false,
                        oauthUrl = oauthUrl,
                        pinId = pinResponse.id,
                        pinCode = pinResponse.code
                    )
                    
                    // Start polling for auth completion
                    pollForAuth(pinResponse.id)
                },
                onFailure = { exception ->
                    _oauthUiState.value = _oauthUiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to start OAuth"
                    )
                    Timber.tag(TAG).e(exception, "Failed to start OAuth")
                }
            )
        }
    }

    /**
     * Poll for OAuth authentication completion.
     */
    private fun pollForAuth(pinId: Int) {
        viewModelScope.launch {
            val result = plexAuthManager.pollOAuthPin(pinId)
            
            result.fold(
                onSuccess = { user ->
                    _oauthUiState.value = _oauthUiState.value.copy(
                        isLoading = false,
                        oauthUrl = null,
                        pinId = null,
                        pinCode = null
                    )
                    Timber.tag(TAG).d("OAuth authentication successful")
                    
                    // Save username to preferences
                    userPreferencesRepository.setPlexUsername(user.username)
                    userPreferencesRepository.setPlexEnabled(true)
                    
                    // Automatically discover servers after sign in
                    discoverServers()
                },
                onFailure = { exception ->
                    _oauthUiState.value = _oauthUiState.value.copy(
                        isLoading = false,
                        oauthUrl = null,
                        pinId = null,
                        pinCode = null,
                        error = exception.message ?: "Authentication failed"
                    )
                    Timber.tag(TAG).e(exception, "OAuth authentication failed")
                }
            )
        }
    }

    /**
     * Cancel ongoing OAuth authentication.
     */
    fun cancelOAuth() {
        _oauthUiState.value = PlexOAuthUiState()
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
            // Reset OAuth UI state
            _oauthUiState.value = PlexOAuthUiState()
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
        _oauthUiState.value = _oauthUiState.value.copy(error = null)
    }
}

/**
 * UI state for Plex OAuth authentication screen.
 */
data class PlexOAuthUiState(
    val oauthUrl: String? = null,
    val pinId: Int? = null,
    val pinCode: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
