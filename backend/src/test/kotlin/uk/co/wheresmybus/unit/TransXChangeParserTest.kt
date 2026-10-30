package uk.co.wheresmybus.unit

import uk.co.wheresmybus.bods.TransXChangeParser
import uk.co.wheresmybus.model.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransXChangeParserTest {
    private val parser = TransXChangeParser()

    @Test
    fun `parses valid NIBS Route 21 TransXChange XML into stops timing links and journeys`() {
        val sampleXml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
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
                        <StopPointRef>150031015002</StopPointRef>
                        <CommonName>Brentwood High Street</CommonName>
                        <LocalityName>Brentwood</LocalityName>
                        <Latitude>51.6204</Latitude>
                        <Longitude>0.3028</Longitude>
                    </AnnotatedStopPointRef>
                    <AnnotatedStopPointRef>
                        <StopPointRef>150032008001</StopPointRef>
                        <CommonName>Ongar Four Wantz</CommonName>
                        <LocalityName>Ongar</LocalityName>
                        <Latitude>51.7145</Latitude>
                        <Longitude>0.2435</Longitude>
                    </AnnotatedStopPointRef>
                </StopPoints>
                <JourneyPatternSections>
                    <JourneyPatternSection id="JPS-OUT-1">
                        <JourneyPatternTimingLink id="JPTL-1">
                            <From><StopPointRef>1500IM2459B</StopPointRef></From>
                            <To><StopPointRef>150031015002</StopPointRef></To>
                            <RunTime>PT4M</RunTime>
                        </JourneyPatternTimingLink>
                        <JourneyPatternTimingLink id="JPTL-2">
                            <From><StopPointRef>150031015002</StopPointRef></From>
                            <To><StopPointRef>150032008001</StopPointRef></To>
                            <RunTime>PT26M</RunTime>
                        </JourneyPatternTimingLink>
                    </JourneyPatternSection>
                    <JourneyPatternSection id="JPS-IN-1">
                        <JourneyPatternTimingLink id="JPTL-3">
                            <From><StopPointRef>150032008001</StopPointRef></From>
                            <To><StopPointRef>1500IM2459B</StopPointRef></To>
                            <RunTime>PT30M</RunTime>
                        </JourneyPatternTimingLink>
                    </JourneyPatternSection>
                </JourneyPatternSections>
                <Operators>
                    <Operator id="O1">
                        <NationalOperatorCode>NIBS</NationalOperatorCode>
                    </Operator>
                </Operators>
                <Services>
                    <Service>
                        <Lines><Line><LineName>21</LineName></Line></Lines>
                    </Service>
                </Services>
                <VehicleJourneys>
                    <VehicleJourney>
                        <VehicleJourneyCode>VJ-MORN-1</VehicleJourneyCode>
                        <JourneyPatternRef>JP-OUT</JourneyPatternRef>
                        <DepartureTime>07:40:00</DepartureTime>
                        <SchoolDay/>
                    </VehicleJourney>
                    <VehicleJourney>
                        <VehicleJourneyCode>VJ-AFT-1</VehicleJourneyCode>
                        <Direction>inbound</Direction>
                        <DepartureTime>15:25:00</DepartureTime>
                    </VehicleJourney>
                </VehicleJourneys>
            </TransXChange>
            """.trimIndent()

        val timetable = parser.parse(sampleXml)

        assertEquals("NIBS", timetable.operatorCode)
        assertEquals("21", timetable.lineName)
        assertEquals(3, timetable.outboundStops.size)
        assertEquals("1500IM2459B", timetable.outboundStops.first().atcoCode)
        assertEquals("150032008001", timetable.outboundStops.last().atcoCode)
        assertEquals(2, timetable.outboundTimingLinks.size)
        assertEquals(4, timetable.outboundTimingLinks.first().runTimeMinutes)
        assertEquals(2, timetable.journeys.size)
        assertEquals(Direction.BRENTWOOD_TO_ONGAR, timetable.journeys[0].direction)
        assertTrue(timetable.journeys[0].schoolDaysOnly)
        assertEquals(Direction.ONGAR_TO_BRENTWOOD, timetable.journeys[1].direction)
    }

    @Test
    fun `falls back to default Route 21 timetable when XML is blank or malformed`() {
        val blankResult = parser.parse("")
        assertEquals("NIBS", blankResult.operatorCode)
        assertEquals("21", blankResult.lineName)
        assertTrue(blankResult.outboundStops.size >= 10)

        val malformedResult = parser.parse("<not-valid-xml")
        assertEquals("NIBS", malformedResult.operatorCode)
        assertTrue(malformedResult.journeys.isNotEmpty())
    }

    @Test
    fun `parses ISO-8601 duration strings accurately`() {
        assertEquals(6, TransXChangeParser.parseIsoDurationMinutes("PT6M"))
        assertEquals(-2, TransXChangeParser.parseIsoDurationMinutes("-PT2M"))
        assertEquals(65, TransXChangeParser.parseIsoDurationMinutes("PT1H5M"))
        assertEquals(5, TransXChangeParser.parseIsoDurationMinutes("PT4M45S"))
        assertEquals(0, TransXChangeParser.parseIsoDurationMinutes(""))
    }
}
