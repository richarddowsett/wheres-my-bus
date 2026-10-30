package uk.co.wheresmybus.adapter.outbound.persistence

import uk.co.wheresmybus.domain.model.DeviceSubscription
import uk.co.wheresmybus.domain.model.HistoricalTripRecord
import uk.co.wheresmybus.domain.model.RouteTimetable
import uk.co.wheresmybus.domain.model.TripNotificationState
import uk.co.wheresmybus.domain.model.WatchSession
import uk.co.wheresmybus.domain.port.outbound.BusStatePersistencePort
import uk.co.wheresmybus.domain.service.DefaultRoute21Timetable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class InMemoryPersistenceAdapter(
    initialTimetable: RouteTimetable = DefaultRoute21Timetable.create(),
) : BusStatePersistencePort {
    private val timetableRef = AtomicReference(initialTimetable)
    private val subscriptions = ConcurrentHashMap<String, DeviceSubscription>()
    private val watchSessions = ConcurrentHashMap<String, WatchSession>()
    private val notificationStates = ConcurrentHashMap<String, TripNotificationState>()
    private val historicalRecords = ConcurrentHashMap<String, HistoricalTripRecord>()
    private val lastHistorySyncRef = AtomicReference<String?>(null)

    override suspend fun getTimetable(): RouteTimetable = timetableRef.get()

    override suspend fun saveTimetable(timetable: RouteTimetable) {
        timetableRef.set(timetable)
    }

    override suspend fun upsertSubscription(subscription: DeviceSubscription) {
        subscriptions[subscription.deviceId] = subscription
    }

    override suspend fun getSubscriptions(): List<DeviceSubscription> = subscriptions.values.filter { it.enabled }

    override suspend fun saveWatchSession(session: WatchSession) {
        watchSessions[session.id] = session
    }

    override suspend fun getActiveWatchSessions(nowEpochSeconds: Long): List<WatchSession> {
        watchSessions.entries.removeIf { it.value.expiresAtEpochSeconds <= nowEpochSeconds }
        return watchSessions.values.toList()
    }

    override suspend fun getNotificationState(subscriptionKey: String): TripNotificationState? = notificationStates[subscriptionKey]

    override suspend fun saveNotificationState(state: TripNotificationState) {
        notificationStates[state.subscriptionKey] = state
    }

    override suspend fun upsertHistoricalRecords(
        records: List<HistoricalTripRecord>,
        syncIso: String,
    ) {
        for (record in records) {
            val key = "${record.dateIso}:${record.journeyCode}:${record.direction.name}"
            historicalRecords[key] = record
        }
        lastHistorySyncRef.set(syncIso)
    }

    override suspend fun getHistoricalRecords(): List<HistoricalTripRecord> =
        historicalRecords.values.sortedWith(
            compareByDescending<HistoricalTripRecord> { it.dateIso }
                .thenByDescending { it.scheduledDeparture },
        )

    override suspend fun getLastHistorySyncIso(): String? = lastHistorySyncRef.get()
}
