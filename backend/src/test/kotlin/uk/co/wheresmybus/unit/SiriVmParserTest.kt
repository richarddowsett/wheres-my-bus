package uk.co.wheresmybus.unit

import uk.co.wheresmybus.bods.SiriVmParser
import uk.co.wheresmybus.model.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SiriVmParserTest {
    private val parser = SiriVmParser()

    @Test
    fun `extracts NIBS Route 21 vehicle location bearing and delay from SIRI-VM XML`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <Siri>
                <ServiceDelivery>
                    <VehicleMonitoringDelivery>
                        <VehicleActivity>
                            <RecordedAtTime>2026-10-30T07:52:00Z</RecordedAtTime>
                            <MonitoredVehicleJourney>
                                <LineRef>21</LineRef>
                                <DirectionRef>outbound</DirectionRef>
                                <OperatorRef>NIBS</OperatorRef>
                                <OriginRef>1500IM2459B</OriginRef>
                                <DestinationRef>150032008001</DestinationRef>
                                <OriginAimedDepartureTime>2026-10-30T07:40:00Z</OriginAimedDepartureTime>
                                <VehicleLocation>
                                    <Longitude>0.2895</Longitude>
                                    <Latitude>51.6418</Latitude>
                                </VehicleLocation>
                                <Bearing>345.0</Bearing>
                                <Delay>PT7M</Delay>
                                <VehicleRef>NIBS-402</VehicleRef>
                            </MonitoredVehicleJourney>
                        </VehicleActivity>
                        <VehicleActivity>
                            <RecordedAtTime>2026-10-30T07:52:00Z</RecordedAtTime>
                            <MonitoredVehicleJourney>
                                <LineRef>99</LineRef>
                                <OperatorRef>OTHER</OperatorRef>
                                <VehicleLocation>
                                    <Longitude>0.1000</Longitude>
                                    <Latitude>51.5000</Latitude>
                                </VehicleLocation>
                            </MonitoredVehicleJourney>
                        </VehicleActivity>
                    </VehicleMonitoringDelivery>
                </ServiceDelivery>
            </Siri>
            """.trimIndent()

        val vehicles = parser.parse(xml)

        assertEquals(1, vehicles.size)
        val bus = vehicles.first()
        assertEquals("NIBS-402", bus.vehicleRef)
        assertEquals("NIBS", bus.operatorRef)
        assertEquals("21", bus.lineRef)
        assertEquals(Direction.BRENTWOOD_TO_ONGAR, bus.direction)
        assertEquals("07:40", bus.aimedOriginDepartureTime)
        assertEquals(51.6418, bus.latitude)
        assertEquals(0.2895, bus.longitude)
        assertEquals(345.0, bus.bearing)
        assertEquals(7, bus.reportedDelayMinutes)
    }

    @Test
    fun `parses inbound 21ON line reference and handles blank or invalid XML`() {
        val inboundXml =
            """
            <Siri>
                <VehicleActivity>
                    <MonitoredVehicleJourney>
                        <LineRef>21ON</LineRef>
                        <OperatorRef>NIBS</OperatorRef>
                        <DirectionRef>inbound</DirectionRef>
                        <DestinationRef>1500IM2459B</DestinationRef>
                        <VehicleLocation>
                            <Longitude>0.2462</Longitude>
                            <Latitude>51.7010</Latitude>
                        </VehicleLocation>
                    </MonitoredVehicleJourney>
                </VehicleActivity>
            </Siri>
            """.trimIndent()

        val list = parser.parse(inboundXml)
        assertEquals(1, list.size)
        assertEquals(Direction.ONGAR_TO_BRENTWOOD, list.first().direction)

        assertTrue(parser.parse("").isEmpty())
        assertTrue(parser.parse("<broken").isEmpty())
    }
}
