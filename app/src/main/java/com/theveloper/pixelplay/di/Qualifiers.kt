package com.theveloper.pixelplay.di

import javax.inject.Qualifier

/**
 * Qualifier for Deezer Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeezerRetrofit

/**
 * Qualifier for Fast OkHttpClient (Short timeouts).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FastOkHttpClient

/**
 * Qualifier for Plex Auth API Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlexAuthRetrofit

/**
 * Qualifier for Plex Media Server API Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlexServerRetrofit
