package uk.co.wheresmybus.repository

import uk.co.wheresmybus.bods.TransXChangeParser
import uk.co.wheresmybus.model.DeviceSubscription
import uk.co.wheresmybus.model.HistoricalTripRecord
import uk.co.wheresmybus.model.RouteTimetable
import uk.co.wheresmybus.model.TripNotificationState
import uk.co.wheresmybus.model.WatchSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

interface BusStateRepository {
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

class InMemoryBusStateRepository(
    initialTimetable: RouteTimetable = TransXChangeParser.defaultRoute21Timetable(),
) : BusStateRepository {
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

    override suspend fun getNotificationState(subscriptionKey: String): TripNotificationState? {
        return notificationStates[subscriptionKey]
    }

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
