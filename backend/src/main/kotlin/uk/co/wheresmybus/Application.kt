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
import uk.co.wheresmybus.adapter.inbound.web.configureApiRoutes
import uk.co.wheresmybus.adapter.outbound.bods.BodsTransitFeedAdapter
import uk.co.wheresmybus.adapter.outbound.notifications.LoggingNotificationAdapter
import uk.co.wheresmybus.adapter.outbound.persistence.FirestorePersistenceAdapter
import uk.co.wheresmybus.adapter.outbound.persistence.InMemoryPersistenceAdapter
import uk.co.wheresmybus.domain.port.inbound.Route21MonitorUseCase
import uk.co.wheresmybus.domain.port.outbound.BusStatePersistencePort
import uk.co.wheresmybus.domain.port.outbound.NotificationDispatchPort
import uk.co.wheresmybus.domain.port.outbound.TransitFeedPort
import uk.co.wheresmybus.domain.service.HistoricalAnalyticsService
import uk.co.wheresmybus.domain.service.NotificationPolicy
import uk.co.wheresmybus.domain.service.Route21ApplicationService
import uk.co.wheresmybus.domain.service.Route21MonitorEngine
import java.time.ZoneId
import java.time.ZonedDateTime
import io.ktor.server.cio.CIO as ServerCIO

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(ServerCIO, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun createDefaultPersistencePort(
    storageMode: String? = System.getenv("STORAGE_MODE"),
    projectId: String? = System.getenv("GCP_PROJECT_ID"),
    kService: String? = System.getenv("K_SERVICE"),
    httpClient: HttpClient = HttpClient(CIO),
): BusStatePersistencePort {
    val hasCloudEnv = !projectId.isNullOrBlank() || !kService.isNullOrBlank()
    val useFirestore =
        storageMode.equals("FIRESTORE", ignoreCase = true) ||
            (!storageMode.equals("IN_MEMORY", ignoreCase = true) && hasCloudEnv)
    return if (useFirestore) {
        FirestorePersistenceAdapter(
            httpClient = httpClient,
            projectId = projectId?.takeIf { it.isNotBlank() } ?: "wheres-my-bus-513220",
        )
    } else {
        InMemoryPersistenceAdapter()
    }
}

@Suppress("LongParameterList")
fun Application.module(
    persistencePort: BusStatePersistencePort = createDefaultPersistencePort(),
    transitFeedPort: TransitFeedPort =
        BodsTransitFeedAdapter(
            httpClient = HttpClient(CIO),
            apiKey = System.getenv("BODS_API_KEY") ?: "",
        ),
    engine: Route21MonitorEngine = Route21MonitorEngine(),
    historicalAnalyticsService: HistoricalAnalyticsService = HistoricalAnalyticsService(),
    notificationPolicy: NotificationPolicy = NotificationPolicy(),
    notificationDispatchPort: NotificationDispatchPort = LoggingNotificationAdapter(),
    familyPin: String = System.getenv("FAMILY_PIN") ?: "2121",
    staticAssetsDir: String = System.getenv("STATIC_ASSETS_DIR") ?: "/app/static",
    clockProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.of("Europe/London")) },
    useCase: Route21MonitorUseCase =
        Route21ApplicationService(
            persistencePort = persistencePort,
            transitFeedPort = transitFeedPort,
            notificationDispatchPort = notificationDispatchPort,
            monitorEngine = engine,
            historicalAnalyticsService = historicalAnalyticsService,
            notificationPolicy = notificationPolicy,
            clockProvider = clockProvider,
        ),
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
            useCase = useCase,
            familyPin = familyPin,
            staticAssetsDir = staticAssetsDir,
        )
    }
}
