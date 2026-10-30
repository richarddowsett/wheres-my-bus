package uk.co.wheresmybus.domain.port.outbound

import uk.co.wheresmybus.domain.model.DeviceSubscription
import uk.co.wheresmybus.domain.model.HistoricalTripRecord
import uk.co.wheresmybus.domain.model.RouteTimetable
import uk.co.wheresmybus.domain.model.TripNotificationState
import uk.co.wheresmybus.domain.model.WatchSession

interface BusStatePersistencePort {
    suspend fun getTimetable(): RouteTimetable

    suspend fun saveTimetable(timetable: RouteTimetable)

    suspend fun upsertSubscription(subscription: DeviceSubscription)

    suspend fun getSubscriptions(): List<DeviceSubscription>

    suspend fun saveWatchSession(session: WatchSession)

    suspend fun getActiveWatchSessions(nowEpochSeconds: Long): List<WatchSession>

    suspend fun getNotificationState(subscriptionKey: String): TripNotificationState?

    suspend fun saveNotificationState(state: TripNotificationState)

    suspend fun upsertHistoricalRecords(
        records: List<HistoricalTripRecord>,
        syncIso: String,
    )

    suspend fun getHistoricalRecords(): List<HistoricalTripRecord>

    suspend fun getLastHistorySyncIso(): String?
}
