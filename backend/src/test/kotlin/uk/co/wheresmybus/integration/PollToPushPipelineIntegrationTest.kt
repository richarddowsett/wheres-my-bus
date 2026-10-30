package uk.co.wheresmybus.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import uk.co.wheresmybus.adapter.outbound.bods.BodsTransitFeedAdapter
import uk.co.wheresmybus.adapter.outbound.notifications.LoggingNotificationAdapter
import uk.co.wheresmybus.adapter.outbound.persistence.InMemoryPersistenceAdapter
import uk.co.wheresmybus.domain.model.BusAlertStatus
import uk.co.wheresmybus.domain.model.DeviceSubscription
import uk.co.wheresmybus.domain.model.Direction
import uk.co.wheresmybus.domain.model.PollSummary
import uk.co.wheresmybus.domain.model.WatchRequest
import uk.co.wheresmybus.domain.service.NotificationPolicy
import uk.co.wheresmybus.module
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PollToPushPipelineIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun buildDynamicBodsEngine(delayStep: AtomicInteger): MockEngine =
        MockEngine { request ->
            val xmlHeaders = headersOf(HttpHeaders.ContentType, "application/xml")
            if (request.url.encodedPath.endsWith("/datafeed/")) {
                val delayMins = delayStep.get()
                val xml =
                    """
                    <Siri>
                        <VehicleActivity>
                            <RecordedAtTime>2026-10-30T07:42:00Z</RecordedAtTime>
                            <MonitoredVehicleJourney>
                                <LineRef>21</LineRef>
                                <OperatorRef>NIBS</OperatorRef>
                                <DirectionRef>outbound</DirectionRef>
                                <OriginAimedDepartureTime>2026-10-30T07:40:00Z</OriginAimedDepartureTime>
                                <VehicleLocation>
                                    <Longitude>0.3028</Longitude>
                                    <Latitude>51.6204</Latitude>
                                </VehicleLocation>
                                <Delay>PT${delayMins}M</Delay>
                                <VehicleRef>NIBS-2110</VehicleRef>
                            </MonitoredVehicleJourney>
                        </VehicleActivity>
                    </Siri>
                    """.trimIndent()
                respond(xml, HttpStatusCode.OK, xmlHeaders)
            } else {
                respond("<Siri/>", HttpStatusCode.OK, xmlHeaders)
            }
        }

    @Test
    fun `simulates morning school run across poll ticks and dispatches silent ETA and delay alerts`() =
        testApplication {
            val currentClock =
                AtomicReference(
                    ZonedDateTime.of(2026, 10, 30, 7, 42, 0, 0, ZoneId.of("Europe/London")),
                )
            val delayStep = AtomicInteger(1)
            val dispatcher = LoggingNotificationAdapter()
            val repo = InMemoryPersistenceAdapter()

            application {
                module(
                    persistencePort = repo,
                    transitFeedPort =
                        BodsTransitFeedAdapter(
                            HttpClient(buildDynamicBodsEngine(delayStep)),
                            apiKey = "live-key",
                        ),
                    notificationDispatchPort = dispatcher,
                    familyPin = "2121",
                    clockProvider = { currentClock.get() },
                )
            }

            client.post("/api/subscriptions") {
                header("X-Family-Pin", "2121")
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        DeviceSubscription.serializer(),
                        DeviceSubscription(
                            deviceId = "daughter-phone",
                            pushToken = "ExpoToken[daughter]",
                            morningBoardingAtco = "150031044001",
                            morningAlightingAtco = "150032008001",
                        ),
                    ),
                )
            }

            client.post("/api/watch") {
                header("X-Family-Pin", "2121")
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        WatchRequest.serializer(),
                        WatchRequest(
                            deviceId = "parent-phone",
                            pushToken = "ExpoToken[parent]",
                            direction = Direction.BRENTWOOD_TO_ONGAR,
                            boardingAtco = "150031044001",
                            alightingAtco = "150032008001",
                        ),
                    ),
                )
            }

            val poll1Res = client.post("/api/internal/poll?window=MORNING")
            assertEquals(HttpStatusCode.OK, poll1Res.status)
            val summary1 = json.decodeFromString<PollSummary>(poll1Res.bodyAsText())
            assertEquals(2, summary1.evaluatedSubscribers)
            assertEquals(2, summary1.silentUpdatesSent)
            assertEquals(2, summary1.highPriorityAlertsSent)

            currentClock.set(currentClock.get().plusMinutes(1))
            val poll2Res = client.post("/api/internal/poll?window=MORNING")
            val summary2 = json.decodeFromString<PollSummary>(poll2Res.bodyAsText())
            assertEquals(2, summary2.silentUpdatesSent)
            assertEquals(0, summary2.highPriorityAlertsSent)

            delayStep.set(7)
            currentClock.set(currentClock.get().plusMinutes(1))
            val poll3Res = client.post("/api/internal/poll?window=MORNING")
            val summary3 = json.decodeFromString<PollSummary>(poll3Res.bodyAsText())
            assertEquals(2, summary3.highPriorityAlertsSent)
            assertEquals(2, summary3.silentUpdatesSent)

            val history = dispatcher.history()
            assertTrue(
                history.any {
                    !it.silent &&
                        it.status == BusAlertStatus.DELAYED &&
                        it.collapseKey == NotificationPolicy.ALERT_COLLAPSE_KEY
                },
            )
            assertTrue(
                history.any {
                    it.silent &&
                        it.collapseKey == NotificationPolicy.LIVE_ETA_COLLAPSE_KEY
                },
            )
        }
}
