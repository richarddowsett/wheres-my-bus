package uk.co.wheresmybus

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import uk.co.wheresmybus.bods.BodsClient
import uk.co.wheresmybus.engine.HistoricalAnalyticsService
import uk.co.wheresmybus.engine.Route21MonitorEngine
import uk.co.wheresmybus.notifications.LoggingPushNotificationDispatcher
import uk.co.wheresmybus.notifications.NotificationPolicy
import uk.co.wheresmybus.notifications.PushNotificationDispatcher
import uk.co.wheresmybus.repository.BusStateRepository
import uk.co.wheresmybus.repository.InMemoryBusStateRepository
import uk.co.wheresmybus.routes.configureApiRoutes
import java.time.ZoneId
import java.time.ZonedDateTime
import io.ktor.server.cio.CIO as ServerCIO

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(ServerCIO, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

@Suppress("LongParameterList")
fun Application.module(
    repository: BusStateRepository = InMemoryBusStateRepository(),
    bodsClient: BodsClient =
        BodsClient(
            httpClient = HttpClient(CIO),
            apiKey = System.getenv("BODS_API_KEY") ?: "",
        ),
    engine: Route21MonitorEngine = Route21MonitorEngine(),
    historicalAnalyticsService: HistoricalAnalyticsService = HistoricalAnalyticsService(),
    notificationPolicy: NotificationPolicy = NotificationPolicy(),
    pushDispatcher: PushNotificationDispatcher = LoggingPushNotificationDispatcher(),
    familyPin: String = System.getenv("FAMILY_PIN") ?: "2121",
    staticAssetsDir: String = System.getenv("STATIC_ASSETS_DIR") ?: "/app/static",
    clockProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.of("Europe/London")) },
) {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Family-Pin")
    }

    routing {
        configureApiRoutes(
            repository = repository,
            bodsClient = bodsClient,
            engine = engine,
            historicalAnalyticsService = historicalAnalyticsService,
            notificationPolicy = notificationPolicy,
            pushDispatcher = pushDispatcher,
            familyPin = familyPin,
            staticAssetsDir = staticAssetsDir,
            clockProvider = clockProvider,
        )
    }
}
