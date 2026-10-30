package uk.co.wheresmybus.unit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import uk.co.wheresmybus.bods.BodsClient
import uk.co.wheresmybus.bods.TransXChangeParser
import uk.co.wheresmybus.engine.HistoricalAnalyticsService
import uk.co.wheresmybus.model.BusAlertStatus
import uk.co.wheresmybus.model.Direction
import uk.co.wheresmybus.model.LiveBusStatus
import uk.co.wheresmybus.repository.InMemoryBusStateRepository
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoricalAnalyticsServiceTest {
    private val service = HistoricalAnalyticsService()
    private val london = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(2026, 10, 30, 19, 0, 0, 0, london)

    private fun createBodsClient(siriSxXml: String = "<Siri/>"): BodsClient {
        val mock =
            MockEngine { req ->
                val xmlHeaders = headersOf(HttpHeaders.ContentType, "application/xml")
                if (req.url.encodedPath.endsWith("/siri-sx/")) {
                    respond(siriSxXml, HttpStatusCode.OK, xmlHeaders)
                } else {
                    respond(
                        content =
                            """
                            <Siri>
                                <VehicleActivity>
                                    <MonitoredVehicleJourney>
                                        <LineRef>21</LineRef>
                                        <OperatorRef>NIBS</OperatorRef>
                                        <DirectionRef>outbound</DirectionRef>
                                        <OriginAimedDepartureTime>2026-10-30T07:40:00Z</OriginAimedDepartureTime>
                                        <VehicleLocation>
                                            <Longitude>0.3028</Longitude>
                                            <Latitude>51.6204</Latitude>
                                        </VehicleLocation>
                                        <Delay>PT6M</Delay>
                                        <VehicleRef>NIBS-400</VehicleRef>
                                    </MonitoredVehicleJourney>
                                </VehicleActivity>
                            </Siri>
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = xmlHeaders,
                    )
                }
            }
        return BodsClient(HttpClient(mock), apiKey = "test-key")
    }

    @Test
    fun `backfills past 7 days of historical data and computes reliability statistics`() =
        runBlocking {
            val repo = InMemoryBusStateRepository()
            val bods = createBodsClient()

            val summary = service.backfillPastWeek(repo, bods, now, days = 7)
            assertEquals("INITIAL_7_DAY_BACKFILL", summary.mode)
            assertEquals(7, summary.daysProcessed)
            assertEquals(35, summary.recordsUpserted)

            val stats = service.computeStats(repo, bods, now, days = 7, directionFilter = null)
            assertEquals(7, stats.windowDays)
            assertEquals(35, stats.totalScheduledRuns)
            assertEquals(7, stats.dailyBreakdown.size)
            assertTrue(stats.onTimeCount > 0)
            assertTrue(stats.lateCount > 0)
            assertTrue(stats.cancelledOrMissingCount > 0)
            assertEquals(
                35,
                stats.onTimeCount + stats.lateCount + stats.cancelledOrMissingCount,
            )

            val morningStats =
                service.computeStats(
                    repo,
                    bods,
                    now,
                    days = 7,
                    directionFilter = Direction.BRENTWOOD_TO_ONGAR,
                )
            assertEquals(21, morningStats.totalScheduledRuns)
        }

    @Test
    fun `daily batch incorporates live observations and BODS SIRI-VM delays`() =
        runBlocking {
            val repo = InMemoryBusStateRepository()
            val bods = createBodsClient()
            val stops = TransXChangeParser.defaultOutboundStops()

            val observedStatus =
                LiveBusStatus(
                    lineName = "21",
                    operatorCode = "NIBS",
                    direction = Direction.BRENTWOOD_TO_ONGAR,
                    boardingStop = stops.first(),
                    alightingStop = stops.last(),
                    activeJourneyCode = "NIBS-21-OUT-1",
                    scheduledArrivalTime = "07:15",
                    expectedArrivalTime = "07:15",
                    etaMinutes = null,
                    delayMinutes = 0,
                    status = BusAlertStatus.MISSING_BUS_WARNING,
                    statusHeadline = "No Live Bus Signal",
                    statusDetail = "Possible cancellation",
                    nearestStopName = null,
                    currentStopSequenceIndex = null,
                    vehicleLatitude = null,
                    vehicleLongitude = null,
                    vehicleRef = null,
                    activeDisruptions = emptyList(),
                    upcomingDepartures = emptyList(),
                    evaluatedAtIso = "2026-10-30T07:22:00Z",
                )
            service.recordLiveObservation(repo, observedStatus, now)

            val batchSummary = service.runDailyBatch(repo, bods, now)
            assertEquals("DAILY_INCREMENTAL_BATCH", batchSummary.mode)
            assertEquals(1, batchSummary.daysProcessed)
            assertEquals(5, batchSummary.recordsUpserted)

            val records = repo.getHistoricalRecords()
            val preservedMissing = records.find { it.journeyCode == "NIBS-21-OUT-1" }
            assertEquals(BusAlertStatus.MISSING_BUS_WARNING, preservedMissing?.status)

            val delayedFromSiriVm = records.find { it.journeyCode == "NIBS-21-OUT-2" }
            assertEquals(BusAlertStatus.DELAYED, delayedFromSiriVm?.status)
            assertEquals(6, delayedFromSiriVm?.delayMinutes)
        }
}
