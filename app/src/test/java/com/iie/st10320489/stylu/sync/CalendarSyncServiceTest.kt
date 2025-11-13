package com.iie.st10320489.stylu.sync

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.iie.st10320489.stylu.data.local.StyluDatabase
import com.iie.st10320489.stylu.data.local.dao.CalendarDao
import com.iie.st10320489.stylu.data.local.entities.CalendarEntity
import com.iie.st10320489.stylu.data.models.calendar.CalendarEvent
import com.iie.st10320489.stylu.repository.CalendarRepository
import com.iie.st10320489.stylu.repository.TokenManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarSyncServiceTest {

    private lateinit var mockContext: Context
    private lateinit var mockCalendarDao: CalendarDao
    private lateinit var mockTokenManager: TokenManager
    private lateinit var mockCalendarRepository: CalendarRepository
    private lateinit var mockDatabase: StyluDatabase

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockCalendarDao = mockk(relaxed = true)
        mockTokenManager = mockk(relaxed = true)
        mockCalendarRepository = mockk(relaxed = true)
        mockDatabase = mockk(relaxed = true)

        coEvery { mockDatabase.calendarDao() } returns mockCalendarDao
        coEvery { mockTokenManager.getValidAccessToken() } returns Result.success("valid-token")
    }

    private fun createSyncService(): CalendarSyncService {
        return CalendarSyncService(
            context = mockContext,
            calendarRepository = mockCalendarRepository,
            tokenManager = mockTokenManager,
            database = mockDatabase
        )
    }

    @Test
    fun syncAll_withNoPendingSchedules_returnsUpToDate() = runTest {
        val syncService = createSyncService()
        coEvery { mockCalendarDao.getAllScheduledOutfits() } returns emptyList()

        val result = syncService.syncAll()

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("up to date")
        assertThat(result.synced).isEqualTo(0)
        assertThat(result.failed).isEqualTo(0)
    }

    @Test
    fun syncAll_withPendingSchedules_attemptsSync() = runTest {
        val syncService = createSyncService()
        val pendingSchedules = listOf(
            CalendarEntity(-1, "test-user", 101, System.currentTimeMillis(), "Event", null, null, null, null, null)
        )
        coEvery { mockCalendarDao.getAllScheduledOutfits() } returns pendingSchedules

        val mockEvent = CalendarEvent(1, "test-user", 101, Date(), "Event", null)
        coEvery { mockCalendarRepository.scheduleOutfit(any(), any(), any(), any()) } returns Result.success(mockEvent)

        val result = syncService.syncAll()
        assertThat(result).isNotNull()
    }

    @Test
    fun hasPendingSyncs_withPendingSchedules_returnsTrue() = runTest {
        val syncService = createSyncService()
        coEvery { mockCalendarDao.getAllScheduledOutfits() } returns listOf(
            CalendarEntity(1, "user", 101, System.currentTimeMillis(), null, null, null, null, null, null),
            CalendarEntity(-1, "user", 102, System.currentTimeMillis(), null, null, null, null, null, null)
        )
        val hasPending = syncService.hasPendingSyncs()
        assertThat(hasPending).isTrue()
    }

    @Test
    fun hasPendingSyncs_withNoPendingSchedules_returnsFalse() = runTest {
        val syncService = createSyncService()
        coEvery { mockCalendarDao.getAllScheduledOutfits() } returns listOf(
            CalendarEntity(1, "user", 101, System.currentTimeMillis(), null, null, null, null, null, null),
            CalendarEntity(2, "user", 102, System.currentTimeMillis(), null, null, null, null, null, null)
        )
        val hasPending = syncService.hasPendingSyncs()
        assertThat(hasPending).isFalse()
    }

    @Test
    fun syncService_canBeInstantiated() {
        val syncService = createSyncService()
        assertThat(syncService).isNotNull()
    }
}
