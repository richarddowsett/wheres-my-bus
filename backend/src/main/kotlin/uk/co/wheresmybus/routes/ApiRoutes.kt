package uk.co.wheresmybus.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import uk.co.wheresmybus.bods.BodsClient
import uk.co.wheresmybus.engine.HistoricalAnalyticsService
import uk.co.wheresmybus.engine.Route21MonitorEngine
import uk.co.wheresmybus.model.DeviceSubscription
import uk.co.wheresmybus.model.Direction
import uk.co.wheresmybus.model.PollSummary
import uk.co.wheresmybus.model.WatchRequest
import uk.co.wheresmybus.model.WatchSession
import uk.co.wheresmybus.notifications.NotificationPolicy
import uk.co.wheresmybus.notifications.PushNotificationDispatcher
import uk.co.wheresmybus.repository.BusStateRepository
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val line: String,
    val operator: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

@Serializable
data class TimetableSyncResponse(
    val operatorCode: String,
    val lineName: String,
    val outboundStopCount: Int,
    val inboundStopCount: Int,
    val journeyCount: Int,
)

@Suppress("LongParameterList", "LongMethod")
fun Route.configureApiRoutes(
    repository: BusStateRepository,
    bodsClient: BodsClient,
    engine: Route21MonitorEngine,
    historicalAnalyticsService: HistoricalAnalyticsService = HistoricalAnalyticsService(),
    notificationPolicy: NotificationPolicy,
    pushDispatcher: PushNotificationDispatcher,
    familyPin: String,
    clockProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.of("Europe/London")) },
) {
    route("/api") {
        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    service = "wheres-my-bus-route21",
                    line = "21",
                    operator = "NIBS",
                ),
            )
        }

        get("/stops") {
            val dirParam = call.request.queryParameters["direction"]
            val timetable = repository.getTimetable()
            val direction = parseDirection(dirParam, clockProvider())
            val stops =
                if (direction == Direction.BRENTWOOD_TO_ONGAR) {
                    timetable.outboundStops
                } else {
                    timetable.inboundStops
                }
            call.respond(stops)
        }

        get("/status") {
            val now = clockProvider()
            val direction = parseDirection(call.request.queryParameters["direction"], now)
            val boardingAtco = call.request.queryParameters["boardingStop"]
            val alightingAtco = call.request.queryParameters["alightingStop"]

            val timetable = repository.getTimetable()
            val allStopAtcos =
                (timetable.outboundStops.map { it.atcoCode } + timetable.inboundStops.map { it.atcoCode }).toSet()

            val vehicles = bodsClient.fetchLiveVehicles()
            val disruptions = bodsClient.fetchDisruptions(allStopAtcos)

            val liveStatus =
                engine.evaluateStatus(
                    timetable = timetable,
                    liveVehicles = vehicles,
                    disruptions = disruptions,
                    direction = direction,
                    boardingAtco = boardingAtco,
                    alightingAtco = alightingAtco,
                    now = now,
                )
            call.respond(liveStatus)
        }

        get("/history") {
            val now = clockProvider()
            val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 7
            val directionFilter = parseOptionalDirection(call.request.queryParameters["direction"])
            val stats =
                historicalAnalyticsService.computeStats(
                    repository = repository,
                    bodsClient = bodsClient,
                    now = now,
                    days = days,
                    directionFilter = directionFilter,
                )
            call.respond(stats)
        }

        post("/subscriptions") {
            if (!call.isAuthorizedWithFamilyPin(familyPin)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing X-Family-Pin"))
                return@post
            }
            val sub = call.receive<DeviceSubscription>()
            repository.upsertSubscription(sub)
            call.respond(HttpStatusCode.Created, sub)
        }

        get("/subscriptions") {
            if (!call.isAuthorizedWithFamilyPin(familyPin)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing X-Family-Pin"))
                return@get
            }
            call.respond(repository.getSubscriptions())
        }

        post("/watch") {
            if (!call.isAuthorizedWithFamilyPin(familyPin)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing X-Family-Pin"))
                return@post
            }
            val req = call.receive<WatchRequest>()
            val now = clockProvider()
            val session =
                WatchSession(
                    id = UUID.randomUUID().toString(),
                    deviceId = req.deviceId,
                    pushToken = req.pushToken,
                    direction = req.direction,
                    boardingAtco = req.boardingAtco,
                    alightingAtco = req.alightingAtco,
                    expiresAtEpochSeconds = now.toEpochSecond() + (req.durationMinutes.coerceIn(5, 180) * 60L),
                )
            repository.saveWatchSession(session)
            call.respond(HttpStatusCode.Created, session)
        }

        post("/internal/sync-timetable") {
            val updated = bodsClient.fetchRoute21Timetable()
            repository.saveTimetable(updated)
            call.respond(
                TimetableSyncResponse(
                    operatorCode = updated.operatorCode,
                    lineName = updated.lineName,
                    outboundStopCount = updated.outboundStops.size,
                    inboundStopCount = updated.inboundStops.size,
                    journeyCount = updated.journeys.size,
                ),
            )
        }

        post("/internal/backfill-history") {
            val now = clockProvider()
            val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 7
            val summary =
                historicalAnalyticsService.backfillPastWeek(
                    repository = repository,
                    bodsClient = bodsClient,
                    now = now,
                    days = days,
                )
            call.respond(summary)
        }

        post("/internal/batch-daily-history") {
            val now = clockProvider()
            val summary =
                historicalAnalyticsService.runDailyBatch(
                    repository = repository,
                    bodsClient = bodsClient,
                    now = now,
                )
            call.respond(summary)
        }

        post("/internal/poll") {
            val now = clockProvider()
            val windowParam = call.request.queryParameters["window"]?.uppercase()
            val defaultDirection =
                when (windowParam) {
                    "MORNING" -> Direction.BRENTWOOD_TO_ONGAR
                    "AFTERNOON" -> Direction.ONGAR_TO_BRENTWOOD
                    else -> if (now.hour < 12) Direction.BRENTWOOD_TO_ONGAR else Direction.ONGAR_TO_BRENTWOOD
                }

            val timetable = repository.getTimetable()
            val allStopAtcos =
                (timetable.outboundStops.map { it.atcoCode } + timetable.inboundStops.map { it.atcoCode }).toSet()
            val vehicles = bodsClient.fetchLiveVehicles()
            val disruptions = bodsClient.fetchDisruptions(allStopAtcos)

            var silentCount = 0
            var alertCount = 0
            var evaluatedCount = 0

            val subscriptions = repository.getSubscriptions()
            for (sub in subscriptions) {
                val boarding =
                    if (defaultDirection == Direction.BRENTWOOD_TO_ONGAR) {
                        sub.morningBoardingAtco
                    } else {
                        sub.afternoonBoardingAtco
                    }
                val alighting =
                    if (defaultDirection == Direction.BRENTWOOD_TO_ONGAR) {
                        sub.morningAlightingAtco
                    } else {
                        sub.afternoonAlightingAtco
                    }
                val liveStatus =
                    engine.evaluateStatus(
                        timetable = timetable,
                        liveVehicles = vehicles,
                        disruptions = disruptions,
                        direction = defaultDirection,
                        boardingAtco = boarding,
                        alightingAtco = alighting,
                        now = now,
                    )
                historicalAnalyticsService.recordLiveObservation(repository, liveStatus, now)
                val subKey = "sub:${sub.deviceId}:${defaultDirection.name}"
                val prevState = repository.getNotificationState(subKey)
                val (payloads, nextState) =
                    notificationPolicy.evaluateNotifications(
                        pushToken = sub.pushToken,
                        subscriptionKey = subKey,
                        liveStatus = liveStatus,
                        previousState = prevState,
                    )
                for (payload in payloads) {
                    pushDispatcher.send(payload)
                    if (payload.silent) silentCount++ else alertCount++
                }
                repository.saveNotificationState(nextState)
                evaluatedCount++
            }

            val activeWatchSessions = repository.getActiveWatchSessions(now.toEpochSecond())
            for (watch in activeWatchSessions) {
                val liveStatus =
                    engine.evaluateStatus(
                        timetable = timetable,
                        liveVehicles = vehicles,
                        disruptions = disruptions,
                        direction = watch.direction,
                        boardingAtco = watch.boardingAtco,
                        alightingAtco = watch.alightingAtco,
                        now = now,
                    )
                historicalAnalyticsService.recordLiveObservation(repository, liveStatus, now)
                val watchKey = "watch:${watch.id}"
                val prevState = repository.getNotificationState(watchKey)
                val (payloads, nextState) =
                    notificationPolicy.evaluateNotifications(
                        pushToken = watch.pushToken,
                        subscriptionKey = watchKey,
                        liveStatus = liveStatus,
                        previousState = prevState,
                    )
                for (payload in payloads) {
                    pushDispatcher.send(payload)
                    if (payload.silent) silentCount++ else alertCount++
                }
                repository.saveNotificationState(nextState)
                evaluatedCount++
            }

            call.respond(
                PollSummary(
                    window = windowParam ?: defaultDirection.name,
                    evaluatedSubscribers = evaluatedCount,
                    silentUpdatesSent = silentCount,
                    highPriorityAlertsSent = alertCount,
                    evaluatedAtIso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                ),
            )
        }
    }

    val staticDir = File(System.getenv("STATIC_ASSETS_DIR") ?: "/app/static")
    if (staticDir.exists() && staticDir.isDirectory) {
        staticFiles("/", staticDir) {
            default("index.html")
        }
    }
}

private fun ApplicationCall.isAuthorizedWithFamilyPin(expectedPin: String): Boolean {
    if (expectedPin.isBlank()) return true
    val provided = request.headers["X-Family-Pin"]?.trim()
    return provided == expectedPin
}

private fun parseDirection(
    raw: String?,
    now: ZonedDateTime,
): Direction =
    parseOptionalDirection(raw)
        ?: if (now.hour < 12) Direction.BRENTWOOD_TO_ONGAR else Direction.ONGAR_TO_BRENTWOOD

private fun parseOptionalDirection(raw: String?): Direction? =
    when (raw?.uppercase()) {
        "BRENTWOOD_TO_ONGAR", "OUTBOUND", "MORNING" -> Direction.BRENTWOOD_TO_ONGAR
        "ONGAR_TO_BRENTWOOD", "INBOUND", "AFTERNOON" -> Direction.ONGAR_TO_BRENTWOOD
        else -> null
    }
