package uk.co.wheresmybus.domain.service

import uk.co.wheresmybus.domain.model.BusAlertStatus
import uk.co.wheresmybus.domain.model.Direction
import uk.co.wheresmybus.domain.model.DisruptionSituation
import uk.co.wheresmybus.domain.model.LiveBusStatus
import uk.co.wheresmybus.domain.model.LiveVehicle
import uk.co.wheresmybus.domain.model.RouteStop
import uk.co.wheresmybus.domain.model.RouteTimetable
import uk.co.wheresmybus.domain.model.TimingLink
import uk.co.wheresmybus.domain.model.UpcomingDeparture
import uk.co.wheresmybus.domain.model.VehicleJourney
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class Route21MonitorEngine {
    @Suppress("LongMethod")
    fun evaluateStatus(
        timetable: RouteTimetable,
        liveVehicles: List<LiveVehicle>,
        disruptions: List<DisruptionSituation>,
        direction: Direction,
        boardingAtco: String?,
        alightingAtco: String?,
        now: ZonedDateTime,
    ): LiveBusStatus {
        val stops =
            if (direction == Direction.BRENTWOOD_TO_ONGAR) timetable.outboundStops else timetable.inboundStops
        val links =
            if (direction == Direction.BRENTWOOD_TO_ONGAR) {
                timetable.outboundTimingLinks
            } else {
                timetable.inboundTimingLinks
            }

        val boardingStop = stops.find { it.atcoCode == boardingAtco } ?: stops.first()
        val alightingStop = stops.find { it.atcoCode == alightingAtco } ?: stops.last()
        val offsetsByAtco = computeCumulativeOffsetsMinutes(stops, links)
        val boardingOffset = offsetsByAtco[boardingStop.atcoCode] ?: 0
        val nowMinutes = now.hour * 60 + now.minute

        val directionalJourneys =
            timetable.journeys
                .filter { it.direction == direction }
                .sortedBy { parseHHmmToMinutes(it.departureTimeLocal) }

        val activeJourney = selectActiveJourney(directionalJourneys, boardingOffset, nowMinutes)
        val matchedVehicle = matchLiveVehicle(liveVehicles, direction, activeJourney)
        val nearestStop = matchedVehicle?.let { findNearestStop(it.latitude, it.longitude, stops) }

        val originDepMinutes =
            activeJourney?.let { parseHHmmToMinutes(it.departureTimeLocal) } ?: nowMinutes
        val scheduledBoardingMinutes = originDepMinutes + boardingOffset
        val delayMinutes =
            calculateDelayMinutes(matchedVehicle, nearestStop, offsetsByAtco, originDepMinutes, nowMinutes)
        val expectedBoardingMinutes = scheduledBoardingMinutes + delayMinutes
        val etaMinutes = activeJourney?.let { (expectedBoardingMinutes - nowMinutes).coerceAtLeast(0) }

        val status =
            classifyStatus(
                disruptions = disruptions,
                matchedVehicle = matchedVehicle,
                activeJourney = activeJourney,
                originDepMinutes = originDepMinutes,
                scheduledBoardingMinutes = scheduledBoardingMinutes,
                nowMinutes = nowMinutes,
                delayMinutes = delayMinutes,
            )

        val (headline, detail) =
            buildHumanReadableMessages(
                status = status,
                direction = direction,
                boardingStop = boardingStop,
                etaMinutes = etaMinutes,
                delayMinutes = delayMinutes,
                nearestStop = nearestStop,
                disruptions = disruptions,
                scheduledBoardingMinutes = scheduledBoardingMinutes,
                expectedBoardingMinutes = expectedBoardingMinutes,
            )

        return LiveBusStatus(
            lineName = timetable.lineName,
            operatorCode = timetable.operatorCode,
            direction = direction,
            boardingStop = boardingStop,
            alightingStop = alightingStop,
            activeJourneyCode = activeJourney?.journeyCode,
            scheduledArrivalTime = activeJourney?.let { formatMinutesToHHmm(scheduledBoardingMinutes) },
            expectedArrivalTime = activeJourney?.let { formatMinutesToHHmm(expectedBoardingMinutes) },
            etaMinutes = etaMinutes,
            delayMinutes = delayMinutes,
            status = status,
            statusHeadline = headline,
            statusDetail = detail,
            nearestStopName = nearestStop?.commonName,
            currentStopSequenceIndex = nearestStop?.sequenceOrder,
            vehicleLatitude = matchedVehicle?.latitude,
            vehicleLongitude = matchedVehicle?.longitude,
            vehicleRef = matchedVehicle?.vehicleRef,
            activeDisruptions = disruptions,
            upcomingDepartures =
                buildUpcomingDepartures(
                    directionalJourneys,
                    boardingOffset,
                    nowMinutes,
                    activeJourney?.journeyCode,
                    delayMinutes,
                    status,
                ),
            evaluatedAtIso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

    internal fun computeCumulativeOffsetsMinutes(
        stops: List<RouteStop>,
        links: List<TimingLink>,
    ): Map<String, Int> {
        val offsets = linkedMapOf<String, Int>()
        if (stops.isEmpty()) return offsets
        offsets[stops.first().atcoCode] = 0
        var runningTotal = 0
        for (i in 0 until stops.size - 1) {
            val fromAtco = stops[i].atcoCode
            val toAtco = stops[i + 1].atcoCode
            val linkMinutes =
                links.find { it.fromAtcoCode == fromAtco && it.toAtcoCode == toAtco }?.runTimeMinutes ?: 3
            runningTotal += linkMinutes
            offsets[toAtco] = runningTotal
        }
        return offsets
    }

    private fun selectActiveJourney(
        journeys: List<VehicleJourney>,
        boardingOffset: Int,
        nowMinutes: Int,
    ): VehicleJourney? {
        if (journeys.isEmpty()) return null
        return journeys.firstOrNull { journey ->
            val scheduledAtBoarding = parseHHmmToMinutes(journey.departureTimeLocal) + boardingOffset
            scheduledAtBoarding >= nowMinutes - 15
        } ?: journeys.first()
    }

    private fun matchLiveVehicle(
        vehicles: List<LiveVehicle>,
        direction: Direction,
        activeJourney: VehicleJourney?,
    ): LiveVehicle? {
        val directional =
            vehicles.filter { it.direction == null || it.direction == direction }
        if (directional.isEmpty()) return null
        if (activeJourney != null) {
            directional.find { it.aimedOriginDepartureTime == activeJourney.departureTimeLocal }
                ?.let { return it }
        }
        return directional.first()
    }

    internal fun findNearestStop(
        lat: Double,
        lon: Double,
        stops: List<RouteStop>,
    ): RouteStop? =
        stops.minByOrNull { stop ->
            haversineDistanceKm(lat, lon, stop.latitude, stop.longitude)
        }

    private fun calculateDelayMinutes(
        matchedVehicle: LiveVehicle?,
        nearestStop: RouteStop?,
        offsetsByAtco: Map<String, Int>,
        originDepMinutes: Int,
        nowMinutes: Int,
    ): Int {
        if (matchedVehicle == null) return 0
        matchedVehicle.reportedDelayMinutes?.let { return it }
        if (nearestStop != null) {
            val stopOffset = offsetsByAtco[nearestStop.atcoCode] ?: 0
            val scheduledAtNearest = originDepMinutes + stopOffset
            return (nowMinutes - scheduledAtNearest).coerceAtLeast(0)
        }
        return 0
    }

    private fun classifyStatus(
        disruptions: List<DisruptionSituation>,
        matchedVehicle: LiveVehicle?,
        activeJourney: VehicleJourney?,
        originDepMinutes: Int,
        scheduledBoardingMinutes: Int,
        nowMinutes: Int,
        delayMinutes: Int,
    ): BusAlertStatus {
        val hasSevereDisruption =
            disruptions.any { situation ->
                val cond = situation.condition.lowercase()
                cond.contains("cancel") ||
                    cond.contains("noservice") ||
                    cond.contains("divert") ||
                    cond.contains("suspend") ||
                    cond.contains("disrupt")
            }
        if (hasSevereDisruption) {
            return BusAlertStatus.CANCELLED_OR_DISRUPTED
        }

        val shouldHaveDepartedOrigin =
            activeJourney != null &&
                nowMinutes >= originDepMinutes + MISSING_BUS_THRESHOLD_MINUTES &&
                nowMinutes <= scheduledBoardingMinutes + 15
        if (matchedVehicle == null && shouldHaveDepartedOrigin) {
            return BusAlertStatus.MISSING_BUS_WARNING
        }

        if (delayMinutes >= DELAY_ALERT_THRESHOLD_MINUTES || disruptions.isNotEmpty()) {
            return BusAlertStatus.DELAYED
        }

        return BusAlertStatus.ON_TIME
    }

    @Suppress("LongParameterList")
    private fun buildHumanReadableMessages(
        status: BusAlertStatus,
        direction: Direction,
        boardingStop: RouteStop,
        etaMinutes: Int?,
        delayMinutes: Int,
        nearestStop: RouteStop?,
        disruptions: List<DisruptionSituation>,
        scheduledBoardingMinutes: Int,
        expectedBoardingMinutes: Int,
    ): Pair<String, String> {
        val destLabel = if (direction == Direction.BRENTWOOD_TO_ONGAR) "Ongar" else "Brentwood"
        val etaPart = etaMinutes?.let { "$it mins away" } ?: "Scheduled"
        val locationPart = nearestStop?.let { "Near ${it.commonName}" } ?: "Awaiting GPS tracking"
        val schedStr = formatMinutesToHHmm(scheduledBoardingMinutes)
        val expStr = formatMinutesToHHmm(expectedBoardingMinutes)

        return when (status) {
            BusAlertStatus.CANCELLED_OR_DISRUPTED -> {
                val summary = disruptions.firstOrNull()?.summary ?: "Service disruption reported on Route 21"
                "Bus 21 to $destLabel: DISRUPTED / CANCELLED" to
                    "$summary (Scheduled $schedStr at ${boardingStop.commonName})"
            }

            BusAlertStatus.MISSING_BUS_WARNING -> {
                "Bus 21 to $destLabel: No Live Bus Signal" to
                    "Scheduled departure has passed ($schedStr at ${boardingStop.commonName}) " +
                    "with no NIBS GPS vehicle active on Route 21. Possible cancellation."
            }

            BusAlertStatus.DELAYED -> {
                "Bus 21 to $destLabel: $etaPart (Delayed +${delayMinutes}m)" to
                    "Expected $expStr (scheduled $schedStr) at ${boardingStop.commonName} • $locationPart"
            }

            BusAlertStatus.ON_TIME -> {
                "Bus 21 to $destLabel: $etaPart (On Time)" to
                    "Due $expStr at ${boardingStop.commonName} • $locationPart"
            }
        }
    }

    private fun buildUpcomingDepartures(
        directionalJourneys: List<VehicleJourney>,
        boardingOffset: Int,
        nowMinutes: Int,
        activeJourneyCode: String?,
        activeDelayMinutes: Int,
        activeStatus: BusAlertStatus,
    ): List<UpcomingDeparture> =
        directionalJourneys
            .filter { parseHHmmToMinutes(it.departureTimeLocal) + boardingOffset >= nowMinutes - 10 }
            .take(4)
            .map { journey ->
                val schedMins = parseHHmmToMinutes(journey.departureTimeLocal) + boardingOffset
                val isCurrent = journey.journeyCode == activeJourneyCode
                val expMins = if (isCurrent) schedMins + activeDelayMinutes else schedMins
                UpcomingDeparture(
                    journeyCode = journey.journeyCode,
                    scheduledTime = formatMinutesToHHmm(schedMins),
                    expectedTime = formatMinutesToHHmm(expMins),
                    status = if (isCurrent) activeStatus else BusAlertStatus.ON_TIME,
                )
            }

    companion object {
        const val DELAY_ALERT_THRESHOLD_MINUTES = 5
        const val MISSING_BUS_THRESHOLD_MINUTES = 5

        fun parseHHmmToMinutes(hhmm: String): Int =
            runCatching {
                val time = LocalTime.parse(hhmm.trim().take(5))
                time.hour * 60 + time.minute
            }.getOrDefault(0)

        fun formatMinutesToHHmm(totalMinutes: Int): String {
            val normalized = ((totalMinutes % 1440) + 1440) % 1440
            val hours = normalized / 60
            val mins = normalized % 60
            return "%02d:%02d".format(hours, mins)
        }

        fun haversineDistanceKm(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val earthRadiusKm = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a =
                sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return (earthRadiusKm * c * 1000.0).roundToInt() / 1000.0
        }
    }
}
