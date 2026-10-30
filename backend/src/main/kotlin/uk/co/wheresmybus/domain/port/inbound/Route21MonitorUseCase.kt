package uk.co.wheresmybus.domain.port.inbound

import uk.co.wheresmybus.domain.model.DeviceSubscription
import uk.co.wheresmybus.domain.model.HistoricalStatsResponse
import uk.co.wheresmybus.domain.model.HistoryBatchSummary
import uk.co.wheresmybus.domain.model.LiveBusStatus
import uk.co.wheresmybus.domain.model.PollSummary
import uk.co.wheresmybus.domain.model.RouteStop
import uk.co.wheresmybus.domain.model.TimetableSyncSummary
import uk.co.wheresmybus.domain.model.WatchRequest
import uk.co.wheresmybus.domain.model.WatchSession

interface Route21MonitorUseCase {
    suspend fun getStops(directionParam: String?): List<RouteStop>

    suspend fun getLiveStatus(
        directionParam: String?,
        boardingAtco: String?,
        alightingAtco: String?,
    ): LiveBusStatus

    suspend fun getHistoricalStats(
        days: Int,
        directionParam: String?,
    ): HistoricalStatsResponse

    suspend fun saveSubscription(subscription: DeviceSubscription): DeviceSubscription

    suspend fun getSubscriptions(): List<DeviceSubscription>

    suspend fun startWatchSession(request: WatchRequest): WatchSession

    suspend fun syncTimetable(): TimetableSyncSummary

    suspend fun backfillHistory(days: Int): HistoryBatchSummary

    suspend fun runDailyHistoryBatch(): HistoryBatchSummary

    suspend fun executePoll(windowParam: String?): PollSummary
}
