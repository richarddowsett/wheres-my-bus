package uk.co.wheresmybus.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import uk.co.wheresmybus.bods.BodsClient
import uk.co.wheresmybus.model.BusAlertStatus
import uk.co.wheresmybus.model.DeviceSubscription
import uk.co.wheresmybus.model.Direction
import uk.co.wheresmybus.model.HistoricalStatsResponse
import uk.co.wheresmybus.model.HistoryBatchSummary
import uk.co.wheresmybus.model.LiveBusStatus
import uk.co.wheresmybus.model.RouteStop
import uk.co.wheresmybus.model.WatchRequest
import uk.co.wheresmybus.module
import uk.co.wheresmybus.notifications.LoggingPushNotificationDispatcher
import uk.co.wheresmybus.repository.InMemoryBusStateRepository
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiRoutesIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val fixedMorningClock =
        ZonedDateTime.of(2026, 10, 30, 7, 45, 0, 0, ZoneId.of("Europe/London"))

    private fun createMockBodsClient(): BodsClient {
        val mockEngine =
            MockEngine { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/datafeed/") ->
                        respond(
                            content =
                                """
                                <Siri>
                                    <VehicleActivity>
                                        <RecordedAtTime>2026-10-30T07:45:00Z</RecordedAtTime>
                                        <MonitoredVehicleJourney>
                                            <LineRef>21</LineRef>
                                            <OperatorRef>NIBS</OperatorRef>
                                            <DirectionRef>outbound</DirectionRef>
                                            <OriginAimedDepartureTime>2026-10-30T07:40:00Z</OriginAimedDepartureTime>
                                            <VehicleLocation>
                                                <Longitude>0.3028</Longitude>
                                                <Latitude>51.6204</Latitude>
                                            </VehicleLocation>
                                            <Delay>PT1M</Delay>
                                            <VehicleRef>NIBS-2109</VehicleRef>
                                        </MonitoredVehicleJourney>
                                    </VehicleActivity>
                                </Siri>
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/xml"),
                        )

                    else ->
                        respond(
                            content = "<Siri/>",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/xml"),
                        )
                }
            }
        return BodsClient(HttpClient(mockEngine), apiKey = "test-key")
    }

    @Test
    fun `GET health stops and status endpoints return expected payloads`() =
        testApplication {
            application {
                module(
                    repository = InMemoryBusStateRepository(),
                    bodsClient = createMockBodsClient(),
                    pushDispatcher = LoggingPushNotificationDispatcher(),
                    familyPin = "2121",
                    clockProvider = { fixedMorningClock },
                )
            }

            val healthRes = client.get("/api/health")
            assertEquals(HttpStatusCode.OK, healthRes.status)
            assertTrue(healthRes.bodyAsText().contains("wheres-my-bus-route21"))

            val stopsRes = client.get("/api/stops?direction=BRENTWOOD_TO_ONGAR")
            assertEquals(HttpStatusCode.OK, stopsRes.status)
            val stops = json.decodeFromString<List<RouteStop>>(stopsRes.bodyAsText())
            assertTrue(stops.size >= 10)
            assertEquals("Brentwood Railway Station", stops.first().commonName)
            assertEquals("Ongar Four Wantz", stops.last().commonName)

            val statusRes =
                client.get(
                    "/api/status?direction=BRENTWOOD_TO_ONGAR" +
                        "&boardingStop=150031044001&alightingStop=150032008001",
                )
            assertEquals(HttpStatusCode.OK, statusRes.status)
            val status = json.decodeFromString<LiveBusStatus>(statusRes.bodyAsText())
            assertEquals(BusAlertStatus.ON_TIME, status.status)
            assertEquals("NIBS-2109", status.vehicleRef)
            assertEquals("Brentwood High Street", status.nearestStopName)
        }

    @Test
    fun `protected subscription and watch endpoints enforce X-Family-Pin header`() =
        testApplication {
            application {
                module(
                    repository = InMemoryBusStateRepository(),
                    bodsClient = createMockBodsClient(),
                    pushDispatcher = LoggingPushNotificationDispatcher(),
                    familyPin = "2121",
                    clockProvider = { fixedMorningClock },
                )
            }

            val subPayload =
                json.encodeToString(
                    DeviceSubscription.serializer(),
                    DeviceSubscription(
                        deviceId = "daughter-iphone",
                        pushToken = "ExponentPushToken[abc123]",
                    ),
                )

            val unauthSubRes =
                client.post("/api/subscriptions") {
                    contentType(ContentType.Application.Json)
                    setBody(subPayload)
                }
            assertEquals(HttpStatusCode.Unauthorized, unauthSubRes.status)

            val authSubRes =
                client.post("/api/subscriptions") {
                    header("X-Family-Pin", "2121")
                    contentType(ContentType.Application.Json)
                    setBody(subPayload)
                }
            assertEquals(HttpStatusCode.Created, authSubRes.status)

            val listRes =
                client.get("/api/subscriptions") {
                    header("X-Family-Pin", "2121")
                }
            assertEquals(HttpStatusCode.OK, listRes.status)
            val list = json.decodeFromString<List<DeviceSubscription>>(listRes.bodyAsText())
            assertEquals(1, list.size)
            assertEquals("daughter-iphone", list.first().deviceId)

            val watchPayload =
                json.encodeToString(
                    WatchRequest.serializer(),
                    WatchRequest(
                        deviceId = "daughter-iphone",
                        pushToken = "ExponentPushToken[abc123]",
                        direction = Direction.BRENTWOOD_TO_ONGAR,
                        boardingAtco = "1500IM2459B",
                        alightingAtco = "150032008001",
                    ),
                )
            val watchRes =
                client.post("/api/watch") {
                    header("X-Family-Pin", "2121")
                    contentType(ContentType.Application.Json)
                    setBody(watchPayload)
                }
            assertEquals(HttpStatusCode.Created, watchRes.status)

            val syncRes = client.post("/api/internal/sync-timetable")
            assertEquals(HttpStatusCode.OK, syncRes.status)

            val backfillRes = client.post("/api/internal/backfill-history?days=7")
            assertEquals(HttpStatusCode.OK, backfillRes.status)
            val backfillSummary =
                json.decodeFromString<HistoryBatchSummary>(backfillRes.bodyAsText())
            assertEquals("INITIAL_7_DAY_BACKFILL", backfillSummary.mode)
            assertEquals(7, backfillSummary.daysProcessed)

            val dailyBatchRes = client.post("/api/internal/batch-daily-history")
            assertEquals(HttpStatusCode.OK, dailyBatchRes.status)
            val dailySummary =
                json.decodeFromString<HistoryBatchSummary>(dailyBatchRes.bodyAsText())
            assertEquals("DAILY_INCREMENTAL_BATCH", dailySummary.mode)

            val historyRes = client.get("/api/history?days=7")
            assertEquals(HttpStatusCode.OK, historyRes.status)
            val historyStats =
                json.decodeFromString<HistoricalStatsResponse>(historyRes.bodyAsText())
            assertEquals(7, historyStats.windowDays)
            assertEquals(7, historyStats.dailyBreakdown.size)
            assertTrue(historyStats.totalScheduledRuns >= 30)
        }

    @Test
    fun `GET live and history frontend routes serve SPA index html`() =
        testApplication {
            val tempStaticDir = Files.createTempDirectory("spa-static")
            Files.writeString(tempStaticDir.resolve("index.html"), "<html><body>Route 21 SPA</body></html>")

            application {
                module(
                    repository = InMemoryBusStateRepository(),
                    bodsClient = createMockBodsClient(),
                    pushDispatcher = LoggingPushNotificationDispatcher(),
                    familyPin = "2121",
                    staticAssetsDir = tempStaticDir.toString(),
                    clockProvider = { fixedMorningClock },
                )
            }

            for (path in listOf("/", "/live", "/history")) {
                val res = client.get(path)
                assertEquals(HttpStatusCode.OK, res.status)
                assertTrue(res.bodyAsText().contains("Route 21 SPA"))
            }
        }
}
