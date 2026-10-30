package uk.co.wheresmybus.unit

import uk.co.wheresmybus.bods.TransXChangeParser
import uk.co.wheresmybus.model.BusAlertStatus
import uk.co.wheresmybus.model.Direction
import uk.co.wheresmybus.model.LiveBusStatus
import uk.co.wheresmybus.notifications.NotificationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPolicyTest {
    private val policy = NotificationPolicy()
    private val stops = TransXChangeParser.defaultOutboundStops()

    private fun buildStatus(
        status: BusAlertStatus,
        etaMinutes: Int?,
        delayMinutes: Int = 0,
    ): LiveBusStatus =
        LiveBusStatus(
            lineName = "21",
            operatorCode = "NIBS",
            direction = Direction.BRENTWOOD_TO_ONGAR,
            boardingStop = stops.first(),
            alightingStop = stops.last(),
            activeJourneyCode = "NIBS-21-OUT-2",
            scheduledArrivalTime = "07:40",
            expectedArrivalTime = "07:40",
            etaMinutes = etaMinutes,
            delayMinutes = delayMinutes,
            status = status,
            statusHeadline = "Bus 21 to Ongar: ${etaMinutes ?: 0} mins away",
            statusDetail = "Due 07:40 at Brentwood Railway Station",
            nearestStopName = "Brentwood High Street",
            currentStopSequenceIndex = 2,
            vehicleLatitude = 51.6204,
            vehicleLongitude = 0.3028,
            vehicleRef = "NIBS-1",
            activeDisruptions = emptyList(),
            upcomingDepartures = emptyList(),
            evaluatedAtIso = "2026-10-30T07:30:00Z",
        )

    @Test
    fun `sends initial high-priority alert and silent live ETA on first evaluation, then only silent updates`() {
        val status10m = buildStatus(BusAlertStatus.ON_TIME, etaMinutes = 10)
        val (firstBatch, state1) =
            policy.evaluateNotifications("token-1", "sub-1", status10m, previousState = null)

        assertEquals(2, firstBatch.size)
        assertTrue(firstBatch.any { !it.silent && it.collapseKey == NotificationPolicy.ALERT_COLLAPSE_KEY })
        assertTrue(firstBatch.any { it.silent && it.collapseKey == NotificationPolicy.LIVE_ETA_COLLAPSE_KEY })

        val status9m = buildStatus(BusAlertStatus.ON_TIME, etaMinutes = 9)
        val (secondBatch, state2) =
            policy.evaluateNotifications("token-1", "sub-1", status9m, previousState = state1)

        assertEquals(1, secondBatch.size)
        assertTrue(secondBatch.first().silent)
        assertEquals(NotificationPolicy.LIVE_ETA_COLLAPSE_KEY, secondBatch.first().collapseKey)

        val (unchangedBatch, _) =
            policy.evaluateNotifications("token-1", "sub-1", status9m, previousState = state2)
        assertTrue(unchangedBatch.isEmpty())
    }

    @Test
    fun `triggers audible high-priority alert on transition to DELAYED and when approaching within 3 minutes`() {
        val initialStatus = buildStatus(BusAlertStatus.ON_TIME, etaMinutes = 12)
        val (_, state1) = policy.evaluateNotifications("token-1", "sub-1", initialStatus, null)

        val delayedStatus = buildStatus(BusAlertStatus.DELAYED, etaMinutes = 18, delayMinutes = 6)
        val (delayedBatch, state2) =
            policy.evaluateNotifications("token-1", "sub-1", delayedStatus, state1)

        assertTrue(delayedBatch.any { !it.silent && it.title.startsWith("⏰ DELAYED:") })

        val approachingStatus = buildStatus(BusAlertStatus.DELAYED, etaMinutes = 2, delayMinutes = 6)
        val (approachingBatch, state3) =
            policy.evaluateNotifications("token-1", "sub-1", approachingStatus, state2)

        assertTrue(approachingBatch.any { !it.silent && it.title.startsWith("🚌 APPROACHING:") })
        assertTrue(state3.alertedApproaching)

        val oneMinStatus = buildStatus(BusAlertStatus.DELAYED, etaMinutes = 1, delayMinutes = 6)
        val (oneMinBatch, _) =
            policy.evaluateNotifications("token-1", "sub-1", oneMinStatus, state3)
        assertFalse(oneMinBatch.any { !it.silent })
    }
}
