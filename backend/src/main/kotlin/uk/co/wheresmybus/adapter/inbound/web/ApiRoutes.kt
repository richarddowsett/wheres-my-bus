package uk.co.wheresmybus.adapter.inbound.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import uk.co.wheresmybus.domain.model.DeviceSubscription
import uk.co.wheresmybus.domain.model.WatchRequest
import uk.co.wheresmybus.domain.port.inbound.Route21MonitorUseCase
import uk.co.wheresmybus.domain.service.HistoricalAnalyticsService
import java.io.File

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

@Suppress("LongMethod")
fun Route.configureApiRoutes(
    useCase: Route21MonitorUseCase,
    familyPin: String,
    staticAssetsDir: String = System.getenv("STATIC_ASSETS_DIR") ?: "/app/static",
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
            call.respond(useCase.getStops(dirParam))
        }

        get("/status") {
            val liveStatus =
                useCase.getLiveStatus(
                    directionParam = call.request.queryParameters["direction"],
                    boardingAtco = call.request.queryParameters["boardingStop"],
                    alightingAtco = call.request.queryParameters["alightingStop"],
                )
            call.respond(liveStatus)
        }

        get("/history") {
            val days =
                call.request.queryParameters["days"]?.toIntOrNull()
                    ?: HistoricalAnalyticsService.DEFAULT_HISTORY_DAYS
            val stats =
                useCase.getHistoricalStats(
                    days = days,
                    directionParam = call.request.queryParameters["direction"],
                )
            call.respond(stats)
        }

        post("/subscriptions") {
            if (!call.isAuthorizedWithFamilyPin(familyPin)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing X-Family-Pin"))
                return@post
            }
            val sub = call.receive<DeviceSubscription>()
            call.respond(HttpStatusCode.Created, useCase.saveSubscription(sub))
        }

        get("/subscriptions") {
            if (!call.isAuthorizedWithFamilyPin(familyPin)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing X-Family-Pin"))
                return@get
            }
            call.respond(useCase.getSubscriptions())
        }

        post("/watch") {
            if (!call.isAuthorizedWithFamilyPin(familyPin)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing X-Family-Pin"))
                return@post
            }
            val req = call.receive<WatchRequest>()
            call.respond(HttpStatusCode.Created, useCase.startWatchSession(req))
        }

        post("/internal/sync-timetable") {
            call.respond(useCase.syncTimetable())
        }

        post("/internal/backfill-history") {
            val days =
                call.request.queryParameters["days"]?.toIntOrNull()
                    ?: HistoricalAnalyticsService.DEFAULT_HISTORY_DAYS
            call.respond(useCase.backfillHistory(days))
        }

        post("/internal/batch-daily-history") {
            call.respond(useCase.runDailyHistoryBatch())
        }

        post("/internal/poll") {
            val windowParam = call.request.queryParameters["window"]
            call.respond(useCase.executePoll(windowParam))
        }
    }

    val staticDir = File(staticAssetsDir)
    if (staticDir.exists() && staticDir.isDirectory) {
        val indexFile = File(staticDir, "index.html")
        if (indexFile.exists()) {
            get("/live") { call.respondFile(indexFile) }
            get("/history") { call.respondFile(indexFile) }
        }
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
