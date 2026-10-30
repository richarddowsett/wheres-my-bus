package uk.co.wheresmybus.unit

import uk.co.wheresmybus.adapter.outbound.bods.TransXChangeParser
import uk.co.wheresmybus.domain.model.BusAlertStatus
import uk.co.wheresmybus.domain.model.Direction
import uk.co.wheresmybus.domain.model.DisruptionSituation
import uk.co.wheresmybus.domain.model.LiveVehicle
import uk.co.wheresmybus.domain.service.Route21MonitorEngine
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Route21MonitorEngineTest {
    private val engine = Route21MonitorEngine()
    private val timetable = TransXChangeParser.defaultRoute21Timetable()
    private val londonZone = ZoneId.of("Europe/London")

    @Test
    fun `classifies ON_TIME and calculates live ETA when vehicle is running on schedule`() {
        val now = ZonedDateTime.of(2026, 10, 30, 7, 44, 0, 0, londonZone)
        val liveVehicle =
            LiveVehicle(
                vehicleRef = "NIBS-2101",
                operatorRef = "NIBS",
                lineRef = "21",
                direction = Direction.BRENTWOOD_TO_ONGAR,
                originAtcoCode = "1500IM2459B",
                destinationAtcoCode = "150032008001",
                aimedOriginDepartureTime = "07:40",
                latitude = 51.6204,
                longitude = 0.3028,
                bearing = 350.0,
                reportedDelayMinutes = 1,
                recordedAtIso = "2026-10-30T07:44:00Z",
            )

        val status =
            engine.evaluateStatus(
                timetable = timetable,
                liveVehicles = listOf(liveVehicle),
                disruptions = emptyList(),
                direction = Direction.BRENTWOOD_TO_ONGAR,
                boardingAtco = "150031044001",
                alightingAtco = "150032008001",
                now = now,
            )

        assertEquals(BusAlertStatus.ON_TIME, status.status)
        assertEquals(1, status.delayMinutes)
        assertEquals("Brentwood High Street", status.nearestStopName)
        assertNotNull(status.etaMinutes)
        assertEquals(8, status.etaMinutes)
        assertTrue(status.statusHeadline.contains("On Time"))
    }

    @Test
    fun `classifies DELAYED when vehicle delay is 5 minutes or more`() {
        val now = ZonedDateTime.of(2026, 10, 30, 7, 48, 0, 0, londonZone)
        val delayedVehicle =
            LiveVehicle(
                vehicleRef = "NIBS-2102",
                operatorRef = "NIBS",
                lineRef = "21",
                direction = Direction.BRENTWOOD_TO_ONGAR,
                originAtcoCode = "1500IM2459B",
                destinationAtcoCode = "150032008001",
                aimedOriginDepartureTime = "07:40",
                latitude = 51.6136,
                longitude = 0.2995,
                bearing = 0.0,
                reportedDelayMinutes = null,
                recordedAtIso = "2026-10-30T07:48:00Z",
            )

        val status =
            engine.evaluateStatus(
                timetable = timetable,
                liveVehicles = listOf(delayedVehicle),
                disruptions = emptyList(),
                direction = Direction.BRENTWOOD_TO_ONGAR,
                boardingAtco = "150031044001",
                alightingAtco = "150032008001",
                now = now,
            )

        assertEquals(BusAlertStatus.DELAYED, status.status)
        assertEquals(8, status.delayMinutes)
        assertTrue(status.statusHeadline.contains("Delayed +8m"))
    }

    @Test
    fun `classifies MISSING_BUS_WARNING when past origin departure by 5+ mins with no live GPS vehicle`() {
        val now = ZonedDateTime.of(2026, 10, 30, 7, 46, 0, 0, londonZone)

        val status =
            engine.evaluateStatus(
                timetable = timetable,
                liveVehicles = emptyList(),
                disruptions = emptyList(),
                direction = Direction.BRENTWOOD_TO_ONGAR,
                boardingAtco = "150031044001",
                alightingAtco = "150032008001",
                now = now,
            )

        assertEquals(BusAlertStatus.MISSING_BUS_WARNING, status.status)
        assertTrue(status.statusHeadline.contains("No Live Bus Signal"))
    }

    @Test
    fun `classifies CANCELLED_OR_DISRUPTED when active SIRI-SX cancellation exists`() {
        val now = ZonedDateTime.of(2026, 10, 30, 15, 20, 0, 0, londonZone)
        val cancellation =
            DisruptionSituation(
                situationNumber = "SX-CANCEL-1",
                summary = "15:25 Ongar to Brentwood service cancelled",
                description = "Cancelled due to driver shortage",
                validFromIso = "2026-10-30T14:00:00Z",
                validToIso = "2026-10-30T17:00:00Z",
                affectedOperatorRefs = listOf("NIBS"),
                affectedLineRefs = listOf("21"),
                affectedStopAtcoCodes = emptyList(),
                condition = "cancelled",
            )

        val status =
            engine.evaluateStatus(
                timetable = timetable,
                liveVehicles = emptyList(),
                disruptions = listOf(cancellation),
                direction = Direction.ONGAR_TO_BRENTWOOD,
                boardingAtco = "150032008001",
                alightingAtco = "1500IM2459B",
                now = now,
            )

        assertEquals(BusAlertStatus.CANCELLED_OR_DISRUPTED, status.status)
        assertTrue(status.statusHeadline.contains("DISRUPTED / CANCELLED"))
    }
}
