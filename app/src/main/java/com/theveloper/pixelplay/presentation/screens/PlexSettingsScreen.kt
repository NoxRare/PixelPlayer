package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.data.network.plex.PlexAuthState
import com.theveloper.pixelplay.data.network.plex.PlexLibrarySection
import com.theveloper.pixelplay.data.network.plex.PlexServer
import com.theveloper.pixelplay.data.preferences.MusicSourcePreference
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.presentation.viewmodel.PlexViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Screen for managing Plex integration settings.
 * Allows users to sign in to Plex via OAuth, select servers, and choose music libraries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlexSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: PlexViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val oauthUiState by viewModel.oauthUiState.collectAsState()
    val availableServers by viewModel.availableServers.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val musicSections by viewModel.musicSections.collectAsState()
    val selectedMusicSection by viewModel.selectedMusicSection.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    // Handle OAuth URL opening
    LaunchedEffect(oauthUiState.oauthUrl) {
        oauthUiState.oauthUrl?.let { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Timber.tag("PlexSettingsScreen").e(e, "Failed to open OAuth URL")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plex Integration") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = authState) {
                is PlexAuthState.NotAuthenticated,
                is PlexAuthState.Error -> {
                    PlexOAuthContent(
                        isLoading = oauthUiState.isLoading,
                        error = oauthUiState.error ?: (state as? PlexAuthState.Error)?.message,
                        onSignIn = { viewModel.startOAuth() },
                        onClearError = { viewModel.clearError() }
                    )
                }
                is PlexAuthState.Authenticating -> {
                    PlexLoadingContent(message = "Authenticating...")
                }
                is PlexAuthState.Authenticated -> {
                    PlexAuthenticatedContent(
                        username = state.user.username,
                        availableServers = availableServers,
                        selectedServer = selectedServer,
                        musicSections = musicSections,
                        selectedMusicSection = selectedMusicSection,
                        isLoading = isLoading,
                        error = error,
                        onServerSelect = { viewModel.selectServer(it) },
                        onMusicSectionSelect = { viewModel.selectMusicSection(it) },
                        onRefreshServers = { viewModel.discoverServers() },
                        onRefreshLibrary = { viewModel.refreshLibrary() },
                        onSignOut = { viewModel.signOut() },
                        navBarPadding = navBarPadding.calculateBottomPadding()
                    )
                }
            }
        }
    }
}

@Composable
private fun PlexOAuthContent(
    isLoading: Boolean,
    error: String?,
    onSignIn: () -> Unit,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Plex logo/icon
        Icon(
            imageVector = Icons.Rounded.Cloud,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Connect to Plex",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sign in with your Plex account to stream music from your servers",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You'll be redirected to Plex.tv to sign in securely with your account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Button(
            onClick = {
                if (error != null) onClearError()
                onSignIn()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Waiting for authentication...")
            } else {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with Plex")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your credentials are never stored in the app. Authentication is handled securely through Plex.tv.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun PlexLoadingContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlexAuthenticatedContent(
    username: String,
    availableServers: List<PlexServer>,
    selectedServer: PlexServer?,
    musicSections: List<PlexLibrarySection>,
    selectedMusicSection: PlexLibrarySection?,
    isLoading: Boolean,
    error: String?,
    onServerSelect: (PlexServer) -> Unit,
    onMusicSectionSelect: (PlexLibrarySection) -> Unit,
    onRefreshServers: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onSignOut: () -> Unit,
    navBarPadding: androidx.compose.ui.unit.Dp
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = navBarPadding + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Account section
        item {
            PlexSectionCard(
                title = "Account",
                icon = Icons.Rounded.Cloud
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Signed in as",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = username,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    FilledTonalButton(onClick = onSignOut) {
                        Icon(
                            imageVector = Icons.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        // Servers section
        item {
            PlexSectionCard(
                title = "Servers",
                icon = Icons.Rounded.Computer,
                action = {
                    IconButton(onClick = onRefreshServers, enabled = !isLoading) {
                        if (isLoading && selectedServer == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh servers"
                            )
                        }
                    }
                }
            ) {
                if (availableServers.isEmpty()) {
                    Text(
                        text = if (isLoading) "Discovering servers..." else "No servers found. Tap refresh to discover servers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableServers.forEach { server ->
                            PlexServerItem(
                                server = server,
                                isSelected = selectedServer?.id == server.id,
                                onClick = { onServerSelect(server) }
                            )
                        }
                    }
                }
            }
        }

        // Music Library section (only show if server is selected)
        if (selectedServer != null) {
            item {
                PlexSectionCard(
                    title = "Music Library",
                    icon = Icons.Rounded.LibraryMusic,
                    action = {
                        if (selectedMusicSection != null) {
                            IconButton(onClick = onRefreshLibrary, enabled = !isLoading) {
                                if (isLoading && selectedServer != null) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Refresh library"
                                    )
                                }
                            }
                        }
                    }
                ) {
                    if (musicSections.isEmpty()) {
                        Text(
                            text = if (isLoading) "Loading music sections..." else "No music libraries found on this server.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            musicSections.forEach { section ->
                                PlexMusicSectionItem(
                                    section = section,
                                    isSelected = selectedMusicSection?.key == section.key,
                                    onClick = { onMusicSectionSelect(section) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Cache Settings section (only show if music section is selected)
        if (selectedMusicSection != null) {
            item {
                PlexCacheSettings()
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                MusicSourceSelection()
                // Extra bottom padding to ensure visibility above navigation bar
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Error display
        if (error != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PlexSectionCard(
    title: String,
    icon: ImageVector,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                action?.invoke()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PlexServerItem(
    server: PlexServer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (server.isLocal) Icons.Rounded.Computer else Icons.Rounded.Cloud,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Column {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = if (server.isLocal) "Local" else if (server.owned) "Owned" else "Shared",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PlexMusicSectionItem(
    section: PlexLibrarySection,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PlexCacheSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Access UserPreferencesRepository from context
    val userPreferencesRepository = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            PlexCacheSettingsEntryPoint::class.java
        ).userPreferencesRepository()
    }
    
    val cacheEnabled by userPreferencesRepository.plexCacheEnabledFlow.collectAsState(initial = true)
    val cacheSizeMb by userPreferencesRepository.plexCacheSizeMbFlow.collectAsState(initial = 500)
    
    PlexSectionCard(
        title = "Cache Settings",
        icon = Icons.Rounded.Storage
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Cache enabled switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Cache",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Cache music from Plex for offline playback",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = cacheEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            userPreferencesRepository.setPlexCacheEnabled(enabled)
                        }
                    }
                )
            }
            
            // Cache size slider (only show when cache is enabled)
            if (cacheEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Cache Size",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$cacheSizeMb MB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = cacheSizeMb.toFloat(),
                        onValueChange = { value ->
                            scope.launch {
                                userPreferencesRepository.setPlexCacheSizeMb(value.roundToInt())
                            }
                        },
                        valueRange = 100f..5000f,
                        steps = 48,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Recommended: 500-1000 MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Clear cache button
                FilledTonalButton(
                    onClick = {
                        // TODO: Implement cache clearing functionality
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Cache")
                }
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PlexCacheSettingsEntryPoint {
    fun userPreferencesRepository(): UserPreferencesRepository
}

@Composable
private fun MusicSourceSelection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    val userPreferencesRepository = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            PlexCacheSettingsEntryPoint::class.java
        ).userPreferencesRepository()
    }
    
    val musicSourcePreference by userPreferencesRepository.musicSourcePreferenceFlow
        .collectAsState(initial = MusicSourcePreference.LOCAL_ONLY)
    
    PlexSectionCard(
        title = "Music Source",
        icon = Icons.Rounded.LibraryMusic
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Choose which music sources to use in your library",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            MusicSourceOption(
                title = "Local Files Only",
                description = "Use only music stored on this device",
                isSelected = musicSourcePreference == MusicSourcePreference.LOCAL_ONLY,
                onClick = {
                    scope.launch {
                        userPreferencesRepository.setMusicSourcePreference(MusicSourcePreference.LOCAL_ONLY)
                    }
                }
            )
            
            MusicSourceOption(
                title = "Plex Server Only",
                description = "Stream music from Plex server exclusively",
                isSelected = musicSourcePreference == MusicSourcePreference.PLEX_ONLY,
                onClick = {
                    scope.launch {
                        userPreferencesRepository.setMusicSourcePreference(MusicSourcePreference.PLEX_ONLY)
                    }
                }
            )
            
            MusicSourceOption(
                title = "Both Sources",
                description = "Combine local files and Plex server music",
                isSelected = musicSourcePreference == MusicSourcePreference.BOTH,
                onClick = {
                    scope.launch {
                        userPreferencesRepository.setMusicSourcePreference(MusicSourcePreference.BOTH)
                    }
                }
            )
        }
    }
}

@Composable
private fun MusicSourceOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
