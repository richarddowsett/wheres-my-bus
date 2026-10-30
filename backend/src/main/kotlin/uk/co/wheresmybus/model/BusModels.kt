package uk.co.wheresmybus.model

import kotlinx.serialization.Serializable

@Serializable
enum class Direction {
    BRENTWOOD_TO_ONGAR,
    ONGAR_TO_BRENTWOOD,
}

@Serializable
enum class BusAlertStatus {
    ON_TIME,
    DELAYED,
    MISSING_BUS_WARNING,
    CANCELLED_OR_DISRUPTED,
}

@Serializable
data class RouteStop(
    val atcoCode: String,
    val commonName: String,
    val locality: String,
    val latitude: Double,
    val longitude: Double,
    val sequenceOrder: Int,
)

@Serializable
data class TimingLink(
    val fromAtcoCode: String,
    val toAtcoCode: String,
    val runTimeMinutes: Int,
)

@Serializable
data class VehicleJourney(
    val journeyCode: String,
    val lineRef: String,
    val operatorRef: String,
    val direction: Direction,
    val originAtcoCode: String,
    val destinationAtcoCode: String,
    val departureTimeLocal: String,
    val daysOfWeek: List<String>,
    val schoolDaysOnly: Boolean = false,
)

@Serializable
data class RouteTimetable(
    val operatorCode: String,
    val lineName: String,
    val outboundStops: List<RouteStop>,
    val inboundStops: List<RouteStop>,
    val outboundTimingLinks: List<TimingLink>,
    val inboundTimingLinks: List<TimingLink>,
    val journeys: List<VehicleJourney>,
)

@Serializable
data class LiveVehicle(
    val vehicleRef: String,
    val operatorRef: String,
    val lineRef: String,
    val direction: Direction?,
    val originAtcoCode: String?,
    val destinationAtcoCode: String?,
    val aimedOriginDepartureTime: String?,
    val latitude: Double,
    val longitude: Double,
    val bearing: Double?,
    val reportedDelayMinutes: Int?,
    val recordedAtIso: String,
)

@Serializable
data class DisruptionSituation(
    val situationNumber: String,
    val summary: String,
    val description: String,
    val validFromIso: String?,
    val validToIso: String?,
    val affectedOperatorRefs: List<String>,
    val affectedLineRefs: List<String>,
    val affectedStopAtcoCodes: List<String>,
    val condition: String,
)

@Serializable
data class UpcomingDeparture(
    val journeyCode: String,
    val scheduledTime: String,
    val expectedTime: String,
    val status: BusAlertStatus,
)

@Serializable
data class LiveBusStatus(
    val lineName: String,
    val operatorCode: String,
    val direction: Direction,
    val boardingStop: RouteStop,
    val alightingStop: RouteStop,
    val activeJourneyCode: String?,
    val scheduledArrivalTime: String?,
    val expectedArrivalTime: String?,
    val etaMinutes: Int?,
    val delayMinutes: Int,
    val status: BusAlertStatus,
    val statusHeadline: String,
    val statusDetail: String,
    val nearestStopName: String?,
    val currentStopSequenceIndex: Int?,
    val vehicleLatitude: Double?,
    val vehicleLongitude: Double?,
    val vehicleRef: String?,
    val activeDisruptions: List<DisruptionSituation>,
    val upcomingDepartures: List<UpcomingDeparture>,
    val evaluatedAtIso: String,
)

@Serializable
data class DeviceSubscription(
    val deviceId: String,
    val pushToken: String,
    val childName: String = "Daughter",
    val morningBoardingAtco: String = "1500IM2459B",
    val morningAlightingAtco: String = "150032008001",
    val afternoonBoardingAtco: String = "150032008001",
    val afternoonAlightingAtco: String = "1500IM2459B",
    val morningWindowStart: String = "07:15",
    val morningWindowEnd: String = "08:30",
    val afternoonWindowStart: String = "15:00",
    val afternoonWindowEnd: String = "16:30",
    val enabled: Boolean = true,
)

@Serializable
data class WatchRequest(
    val deviceId: String,
    val pushToken: String,
    val direction: Direction,
    val boardingAtco: String,
    val alightingAtco: String,
    val durationMinutes: Int = 45,
)

@Serializable
data class WatchSession(
    val id: String,
    val deviceId: String,
    val pushToken: String,
    val direction: Direction,
    val boardingAtco: String,
    val alightingAtco: String,
    val expiresAtEpochSeconds: Long,
)

@Serializable
data class PushNotificationPayload(
    val targetToken: String,
    val collapseKey: String,
    val title: String,
    val body: String,
    val silent: Boolean,
    val priority: String,
    val status: BusAlertStatus,
    val etaMinutes: Int?,
)

@Serializable
data class TripNotificationState(
    val subscriptionKey: String,
    val journeyCode: String?,
    val lastStatus: BusAlertStatus,
    val lastEtaMinutes: Int?,
    val alertedApproaching: Boolean = false,
    val updatedAtIso: String,
)

@Serializable
data class PollSummary(
    val window: String,
    val evaluatedSubscribers: Int,
    val silentUpdatesSent: Int,
    val highPriorityAlertsSent: Int,
    val evaluatedAtIso: String,
)

@Serializable
data class HistoricalTripRecord(
    val dateIso: String,
    val journeyCode: String,
    val direction: Direction,
    val scheduledDeparture: String,
    val delayMinutes: Int,
    val status: BusAlertStatus,
    val summaryNote: String,
)

@Serializable
data class DailyReliabilityRecord(
    val dateIso: String,
    val totalRuns: Int,
    val onTimeCount: Int,
    val lateCount: Int,
    val cancelledOrMissingCount: Int,
    val averageDelayMinutes: Double,
)

@Serializable
data class HistoricalStatsResponse(
    val windowDays: Int,
    val directionFilter: Direction?,
    val totalScheduledRuns: Int,
    val onTimeCount: Int,
    val onTimePercentage: Double,
    val lateCount: Int,
    val latePercentage: Double,
    val cancelledOrMissingCount: Int,
    val cancelledOrMissingPercentage: Double,
    val averageDelayMinutes: Double,
    val dailyBreakdown: List<DailyReliabilityRecord>,
    val recentTrips: List<HistoricalTripRecord>,
    val lastBatchSyncIso: String,
)

@Serializable
data class HistoryBatchSummary(
    val mode: String,
    val daysProcessed: Int,
    val recordsUpserted: Int,
    val completedAtIso: String,
)
