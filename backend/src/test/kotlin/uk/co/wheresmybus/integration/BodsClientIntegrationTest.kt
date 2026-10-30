package uk.co.wheresmybus.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import uk.co.wheresmybus.bods.BodsClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodsClientIntegrationTest {
    private val sampleDatasetXml =
        """
        <TransXChange>
            <StopPoints>
                <AnnotatedStopPointRef>
                    <StopPointRef>1500IM2459B</StopPointRef>
                    <CommonName>Brentwood Railway Station</CommonName>
                    <LocalityName>Brentwood</LocalityName>
                    <Latitude>51.6136</Latitude>
                    <Longitude>0.2995</Longitude>
                </AnnotatedStopPointRef>
                <AnnotatedStopPointRef>
                    <StopPointRef>150032008001</StopPointRef>
                    <CommonName>Ongar Four Wantz</CommonName>
                    <LocalityName>Ongar</LocalityName>
                    <Latitude>51.7145</Latitude>
                    <Longitude>0.2435</Longitude>
                </AnnotatedStopPointRef>
            </StopPoints>
        </TransXChange>
        """.trimIndent()

    private val sampleSiriVmXml =
        """
        <Siri>
            <VehicleActivity>
                <RecordedAtTime>2026-10-30T07:45:00Z</RecordedAtTime>
                <MonitoredVehicleJourney>
                    <LineRef>21</LineRef>
                    <OperatorRef>NIBS</OperatorRef>
                    <DirectionRef>outbound</DirectionRef>
                    <OriginRef>1500IM2459B</OriginRef>
                    <DestinationRef>150032008001</DestinationRef>
                    <VehicleLocation>
                        <Longitude>0.2995</Longitude>
                        <Latitude>51.6136</Latitude>
                    </VehicleLocation>
                    <Delay>PT2M</Delay>
                    <VehicleRef>NIBS-101</VehicleRef>
                </MonitoredVehicleJourney>
            </VehicleActivity>
        </Siri>
        """.trimIndent()

    private val sampleSiriSxXml =
        """
        <Siri>
            <PtSituationElement>
                <SituationNumber>SX-100</SituationNumber>
                <Summary>Diversion on Ongar Road</Summary>
                <Description>Roadworks near Kelvedon Hatch</Description>
                <OperatorRef>NIBS</OperatorRef>
                <LineRef>21</LineRef>
                <Condition>diverted</Condition>
            </PtSituationElement>
        </Siri>
        """.trimIndent()

    @Test
    fun `fetches timetable live vehicles and disruptions from BODS endpoints using MockEngine`() =
        runBlocking {
            val xmlHeaders = headersOf(HttpHeaders.ContentType, "application/xml")
            val mockEngine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/dataset/") -> {
                            assertEquals("NIBS", request.url.parameters["noc"])
                            assertEquals("21", request.url.parameters["lineRef"])
                            assertEquals("test-bods-key", request.url.parameters["api_key"])
                            respond(sampleDatasetXml, HttpStatusCode.OK, xmlHeaders)
                        }

                        path.endsWith("/datafeed/") -> {
                            assertEquals("NIBS", request.url.parameters["operatorRef"])
                            assertEquals("21", request.url.parameters["lineRef"])
                            respond(sampleSiriVmXml, HttpStatusCode.OK, xmlHeaders)
                        }

                        path.endsWith("/siri-sx/") ->
                            respond(sampleSiriSxXml, HttpStatusCode.OK, xmlHeaders)

                        else -> respond("Not Found", HttpStatusCode.NotFound)
                    }
                }

            val client =
                BodsClient(
                    httpClient = HttpClient(mockEngine),
                    apiKey = "test-bods-key",
                    baseUrl = "https://data.bus-data.dft.gov.uk/api/v1",
                )

            val timetable = client.fetchRoute21Timetable()
            assertEquals("NIBS", timetable.operatorCode)
            assertEquals(2, timetable.outboundStops.size)

            val vehicles = client.fetchLiveVehicles()
            assertEquals(1, vehicles.size)
            assertEquals("NIBS-101", vehicles.first().vehicleRef)

            val disruptions = client.fetchDisruptions(setOf("1500IM2459B"))
            assertEquals(1, disruptions.size)
            assertEquals("SX-100", disruptions.first().situationNumber)
        }

    @Test
    fun `gracefully falls back when BODS returns 500 or API key is unconfigured`() =
        runBlocking {
            val failingEngine =
                MockEngine {
                    respond("Upstream Error", HttpStatusCode.InternalServerError)
                }

            val failingClient =
                BodsClient(
                    httpClient = HttpClient(failingEngine),
                    apiKey = "test-bods-key",
                )

            val fallbackTimetable = failingClient.fetchRoute21Timetable()
            assertEquals("NIBS", fallbackTimetable.operatorCode)
            assertTrue(fallbackTimetable.outboundStops.isNotEmpty())
            assertTrue(failingClient.fetchLiveVehicles().isEmpty())
            assertTrue(failingClient.fetchDisruptions(emptySet()).isEmpty())

            val blankKeyClient =
                BodsClient(
                    httpClient = HttpClient(failingEngine),
                    apiKey = "",
                )
            assertEquals("21", blankKeyClient.fetchRoute21Timetable().lineName)
            assertTrue(blankKeyClient.fetchLiveVehicles().isEmpty())
            assertTrue(blankKeyClient.fetchDisruptions(emptySet()).isEmpty())
        }
}
