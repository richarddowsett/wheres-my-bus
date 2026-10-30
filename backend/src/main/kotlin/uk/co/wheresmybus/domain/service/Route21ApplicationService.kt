package uk.co.wheresmybus.domain.service

import uk.co.wheresmybus.domain.model.DeviceSubscription
import uk.co.wheresmybus.domain.model.Direction
import uk.co.wheresmybus.domain.model.HistoricalStatsResponse
import uk.co.wheresmybus.domain.model.HistoryBatchSummary
import uk.co.wheresmybus.domain.model.LiveBusStatus
import uk.co.wheresmybus.domain.model.PollSummary
import uk.co.wheresmybus.domain.model.RouteStop
import uk.co.wheresmybus.domain.model.TimetableSyncSummary
import uk.co.wheresmybus.domain.model.WatchRequest
import uk.co.wheresmybus.domain.model.WatchSession
import uk.co.wheresmybus.domain.port.inbound.Route21MonitorUseCase
import uk.co.wheresmybus.domain.port.outbound.BusStatePersistencePort
import uk.co.wheresmybus.domain.port.outbound.NotificationDispatchPort
import uk.co.wheresmybus.domain.port.outbound.TransitFeedPort
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Suppress("LongParameterList")
class Route21ApplicationService(
    private val persistencePort: BusStatePersistencePort,
    private val transitFeedPort: TransitFeedPort,
    private val notificationDispatchPort: NotificationDispatchPort,
    private val monitorEngine: Route21MonitorEngine = Route21MonitorEngine(),
    private val historicalAnalyticsService: HistoricalAnalyticsService = HistoricalAnalyticsService(),
    private val notificationPolicy: NotificationPolicy = NotificationPolicy(),
    private val clockProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.of("Europe/London")) },
) : Route21MonitorUseCase {
    override suspend fun getStops(directionParam: String?): List<RouteStop> {
        val timetable = persistencePort.getTimetable()
        val direction = resolveDirection(directionParam, clockProvider())
        return if (direction == Direction.BRENTWOOD_TO_ONGAR) {
            timetable.outboundStops
        } else {
            timetable.inboundStops
        }
    }

    override suspend fun getLiveStatus(
        directionParam: String?,
        boardingAtco: String?,
        alightingAtco: String?,
    ): LiveBusStatus {
        val now = clockProvider()
        val direction = resolveDirection(directionParam, now)
        val timetable = persistencePort.getTimetable()
        val allStopAtcos =
            (timetable.outboundStops.map { it.atcoCode } + timetable.inboundStops.map { it.atcoCode }).toSet()

        val vehicles = transitFeedPort.fetchLiveVehicles()
        val disruptions = transitFeedPort.fetchDisruptions(allStopAtcos)

        return monitorEngine.evaluateStatus(
            timetable = timetable,
            liveVehicles = vehicles,
            disruptions = disruptions,
            direction = direction,
            boardingAtco = boardingAtco,
            alightingAtco = alightingAtco,
            now = now,
        )
    }

    override suspend fun getHistoricalStats(
        days: Int,
        directionParam: String?,
    ): HistoricalStatsResponse {
        val now = clockProvider()
        val directionFilter = parseOptionalDirection(directionParam)
        return historicalAnalyticsService.computeStats(
            persistencePort = persistencePort,
            transitFeedPort = transitFeedPort,
            now = now,
            days = days,
            directionFilter = directionFilter,
        )
    }

    override suspend fun saveSubscription(subscription: DeviceSubscription): DeviceSubscription {
        persistencePort.upsertSubscription(subscription)
        return subscription
    }

    override suspend fun getSubscriptions(): List<DeviceSubscription> = persistencePort.getSubscriptions()

    override suspend fun startWatchSession(request: WatchRequest): WatchSession {
        val now = clockProvider()
        val session =
            WatchSession(
                id = UUID.randomUUID().toString(),
                deviceId = request.deviceId,
                pushToken = request.pushToken,
                direction = request.direction,
                boardingAtco = request.boardingAtco,
                alightingAtco = request.alightingAtco,
                expiresAtEpochSeconds = now.toEpochSecond() + (request.durationMinutes.coerceIn(5, 180) * 60L),
            )
        persistencePort.saveWatchSession(session)
        return session
    }

    override suspend fun syncTimetable(): TimetableSyncSummary {
        val updated = transitFeedPort.fetchRoute21Timetable()
        persistencePort.saveTimetable(updated)
        return TimetableSyncSummary(
            operatorCode = updated.operatorCode,
            lineName = updated.lineName,
            outboundStopCount = updated.outboundStops.size,
            inboundStopCount = updated.inboundStops.size,
            journeyCount = updated.journeys.size,
        )
    }

    override suspend fun backfillHistory(days: Int): HistoryBatchSummary =
        historicalAnalyticsService.backfillPastWeek(
            persistencePort = persistencePort,
            transitFeedPort = transitFeedPort,
            now = clockProvider(),
            days = days,
        )

    override suspend fun runDailyHistoryBatch(): HistoryBatchSummary =
        historicalAnalyticsService.runDailyBatch(
            persistencePort = persistencePort,
            transitFeedPort = transitFeedPort,
            now = clockProvider(),
        )

    override suspend fun executePoll(windowParam: String?): PollSummary {
        val now = clockProvider()
        val normalizedWindow = windowParam?.uppercase()
        val defaultDirection = resolveDirection(normalizedWindow, now)

        val timetable = persistencePort.getTimetable()
        val allStopAtcos =
            (timetable.outboundStops.map { it.atcoCode } + timetable.inboundStops.map { it.atcoCode }).toSet()
        val vehicles = transitFeedPort.fetchLiveVehicles()
        val disruptions = transitFeedPort.fetchDisruptions(allStopAtcos)

        var silentCount = 0
        var alertCount = 0
        var evaluatedCount = 0

        for (sub in persistencePort.getSubscriptions()) {
            val (boarding, alighting) = resolveSubscriberStops(sub, defaultDirection)
            val liveStatus =
                monitorEngine.evaluateStatus(
                    timetable = timetable,
                    liveVehicles = vehicles,
                    disruptions = disruptions,
                    direction = defaultDirection,
                    boardingAtco = boarding,
                    alightingAtco = alighting,
                    now = now,
                )
            val (sentSilent, sentAlerts) =
                dispatchForRecipient(
                    pushToken = sub.pushToken,
                    subscriptionKey = "sub:${sub.deviceId}:${defaultDirection.name}",
                    liveStatus = liveStatus,
                    now = now,
                )
            silentCount += sentSilent
            alertCount += sentAlerts
            evaluatedCount++
        }

        for (watch in persistencePort.getActiveWatchSessions(now.toEpochSecond())) {
            val liveStatus =
                monitorEngine.evaluateStatus(
                    timetable = timetable,
                    liveVehicles = vehicles,
                    disruptions = disruptions,
                    direction = watch.direction,
                    boardingAtco = watch.boardingAtco,
                    alightingAtco = watch.alightingAtco,
                    now = now,
                )
            val (sentSilent, sentAlerts) =
                dispatchForRecipient(
                    pushToken = watch.pushToken,
                    subscriptionKey = "watch:${watch.id}",
                    liveStatus = liveStatus,
                    now = now,
                )
            silentCount += sentSilent
            alertCount += sentAlerts
            evaluatedCount++
        }

        return PollSummary(
            window = normalizedWindow ?: defaultDirection.name,
            evaluatedSubscribers = evaluatedCount,
            silentUpdatesSent = silentCount,
            highPriorityAlertsSent = alertCount,
            evaluatedAtIso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

    private suspend fun dispatchForRecipient(
        pushToken: String,
        subscriptionKey: String,
        liveStatus: LiveBusStatus,
        now: ZonedDateTime,
    ): Pair<Int, Int> {
        historicalAnalyticsService.recordLiveObservation(persistencePort, liveStatus, now)
        val prevState = persistencePort.getNotificationState(subscriptionKey)
        val (payloads, nextState) =
            notificationPolicy.evaluateNotifications(
                pushToken = pushToken,
                subscriptionKey = subscriptionKey,
                liveStatus = liveStatus,
                previousState = prevState,
            )
        var silentCount = 0
        var alertCount = 0
        for (payload in payloads) {
            notificationDispatchPort.send(payload)
            if (payload.silent) silentCount++ else alertCount++
        }
        persistencePort.saveNotificationState(nextState)
        return silentCount to alertCount
    }

    private fun resolveSubscriberStops(
        sub: DeviceSubscription,
        direction: Direction,
    ): Pair<String, String> =
        if (direction == Direction.BRENTWOOD_TO_ONGAR) {
            sub.morningBoardingAtco to sub.morningAlightingAtco
        } else {
            sub.afternoonBoardingAtco to sub.afternoonAlightingAtco
        }

    companion object {
        fun resolveDirection(
            raw: String?,
            now: ZonedDateTime,
        ): Direction =
            parseOptionalDirection(raw)
                ?: if (now.hour < 12) Direction.BRENTWOOD_TO_ONGAR else Direction.ONGAR_TO_BRENTWOOD

        fun parseOptionalDirection(raw: String?): Direction? =
            when (raw?.uppercase()) {
                "BRENTWOOD_TO_ONGAR", "OUTBOUND", "MORNING" -> Direction.BRENTWOOD_TO_ONGAR
                "ONGAR_TO_BRENTWOOD", "INBOUND", "AFTERNOON" -> Direction.ONGAR_TO_BRENTWOOD
                else -> null
            }
    }
}
