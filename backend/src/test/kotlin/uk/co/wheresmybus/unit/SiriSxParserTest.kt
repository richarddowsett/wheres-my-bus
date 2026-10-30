package uk.co.wheresmybus.unit

import uk.co.wheresmybus.bods.SiriSxParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SiriSxParserTest {
    private val parser = SiriSxParser()

    @Test
    fun `filters and parses SIRI-SX situations affecting NIBS Route 21 or its stops`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <Siri>
                <ServiceDelivery>
                    <SituationExchangeDelivery>
                        <Situations>
                            <PtSituationElement>
                                <SituationNumber>SX-21-001</SituationNumber>
                                <ValidityPeriod>
                                    <StartTime>2026-10-30T06:00:00Z</StartTime>
                                    <EndTime>2026-10-30T10:00:00Z</EndTime>
                                </ValidityPeriod>
                                <Summary>07:40 Brentwood to Ongar cancelled</Summary>
                                <Description>Due to a vehicle breakdown, the 07:40 service 21 is cancelled.</Description>
                                <Affects>
                                    <Operators>
                                        <AffectedOperator><OperatorRef>NIBS</OperatorRef></AffectedOperator>
                                    </Operators>
                                    <Networks>
                                        <AffectedNetwork>
                                            <AffectedLine><LineRef>21</LineRef></AffectedLine>
                                        </AffectedNetwork>
                                    </Networks>
                                </Affects>
                                <Consequences>
                                    <Consequence>
                                        <Condition>cancelled</Condition>
                                    </Consequence>
                                </Consequences>
                            </PtSituationElement>
                            <PtSituationElement>
                                <SituationNumber>SX-STOP-002</SituationNumber>
                                <Summary>Roadworks at Kelvedon Hatch</Summary>
                                <Description>Temporary traffic lights near Secret Bunker causing delays.</Description>
                                <Affects>
                                    <StopPoints>
                                        <AffectedStopPoint>
                                            <StopPointRef>150032012001</StopPointRef>
                                        </AffectedStopPoint>
                                    </StopPoints>
                                </Affects>
                                <Consequences>
                                    <Consequence>
                                        <Condition>delayed</Condition>
                                    </Consequence>
                                </Consequences>
                            </PtSituationElement>
                            <PtSituationElement>
                                <SituationNumber>SX-OTHER-999</SituationNumber>
                                <Summary>Unrelated bus route in Manchester</Summary>
                                <Affects>
                                    <Operators>
                                        <AffectedOperator><OperatorRef>STGM</OperatorRef></AffectedOperator>
                                    </Operators>
                                    <Networks>
                                        <AffectedNetwork>
                                            <AffectedLine><LineRef>192</LineRef></AffectedLine>
                                        </AffectedNetwork>
                                    </Networks>
                                </Affects>
                            </PtSituationElement>
                        </Situations>
                    </SituationExchangeDelivery>
                </ServiceDelivery>
            </Siri>
            """.trimIndent()

        val route21Stops = setOf("1500IM2459B", "150032012001", "150032008001")
        val situations = parser.parse(xml, route21Stops)

        assertEquals(2, situations.size)
        assertEquals("SX-21-001", situations[0].situationNumber)
        assertEquals("cancelled", situations[0].condition)
        assertEquals("SX-STOP-002", situations[1].situationNumber)
        assertEquals("delayed", situations[1].condition)
    }

    @Test
    fun `returns empty list when SIRI-SX XML is blank or invalid`() {
        assertTrue(parser.parse("").isEmpty())
        assertTrue(parser.parse("<invalid").isEmpty())
    }
}
