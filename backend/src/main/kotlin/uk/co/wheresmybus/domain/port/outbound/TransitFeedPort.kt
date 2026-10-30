package uk.co.wheresmybus.domain.port.outbound

import uk.co.wheresmybus.domain.model.DisruptionSituation
import uk.co.wheresmybus.domain.model.LiveVehicle
import uk.co.wheresmybus.domain.model.RouteTimetable

interface TransitFeedPort {
    suspend fun fetchRoute21Timetable(
        noc: String = "NIBS",
        lineRef: String = "21",
    ): RouteTimetable

    suspend fun fetchLiveVehicles(
        operatorRef: String = "NIBS",
        lineRef: String = "21",
    ): List<LiveVehicle>

    suspend fun fetchDisruptions(route21Stops: Set<String>): List<DisruptionSituation>
}
