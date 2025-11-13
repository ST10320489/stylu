package com.iie.st10320489.stylu.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.iie.st10320489.stylu.sync.CalendarSyncService
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncIntegrationTest {

    private lateinit var context: Context
    private lateinit var syncService: CalendarSyncService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        syncService = CalendarSyncService(context)
    }

    @Test
    fun syncService_canBeInstantiated() {
        // Then
        assertThat(syncService).isNotNull()
    }

    @Test
    fun syncAll_withoutAuthentication_handlesGracefully() = runBlocking {
        // When
        val result = syncService.syncAll()

        // Then
        // Should not crash, even without authentication
        assertThat(result).isNotNull()
        assertThat(result.message).isNotEmpty()
    }

    @Test
    fun hasPendingSyncs_canBeChecked() = runBlocking {
        // When
        val hasPending = syncService.hasPendingSyncs()

        // Then
        // Should return boolean without crashing
        assertThat(hasPending).isAnyOf(true, false)
    }
}