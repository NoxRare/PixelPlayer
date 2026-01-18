package com.theveloper.pixelplay.data.preferences

/**
 * Defines which music sources the app should use for library content.
 */
enum class MusicSourcePreference {
    /**
     * Use only local files from device storage.
     */
    LOCAL_ONLY,
    
    /**
     * Use only Plex server music library.
     */
    PLEX_ONLY,
    
    /**
     * Use both local files and Plex server music library.
     */
    BOTH
}
