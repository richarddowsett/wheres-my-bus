package uk.co.wheresmybus.domain.service

import uk.co.wheresmybus.domain.model.Direction
import uk.co.wheresmybus.domain.model.RouteStop
import uk.co.wheresmybus.domain.model.RouteTimetable
import uk.co.wheresmybus.domain.model.TimingLink
import uk.co.wheresmybus.domain.model.VehicleJourney

object DefaultRoute21Timetable {
    fun outboundStops(): List<RouteStop> =
        listOf(
            RouteStop("1500IM2459B", "Brentwood Railway Station", "Brentwood", 51.6136, 0.2995, 1),
            RouteStop("150031015002", "Brentwood High Street", "Brentwood", 51.6204, 0.3028, 2),
            RouteStop("150031029001", "The Robin Hood", "Brentwood", 51.6305, 0.2974, 3),
            RouteStop("150031044001", "The Rose and Crown", "Pilgrims Hatch", 51.6418, 0.2895, 4),
            RouteStop("150031048001", "Pilgrims Hall", "Pilgrims Hatch", 51.6485, 0.2830, 5),
            RouteStop("150032018001", "Brizes Corner", "Kelvedon Hatch", 51.6630, 0.2735, 6),
            RouteStop("150032015001", "The Eagle", "Kelvedon Hatch", 51.6695, 0.2690, 7),
            RouteStop("150032012001", "Secret Bunker", "Kelvedon Hatch", 51.6778, 0.2625, 8),
            RouteStop("150032010001", "Langford Bridge Farm", "Langford Bridge", 51.6902, 0.2535, 9),
            RouteStop("150032009002", "The Stag", "Ongar", 51.7010, 0.2462, 10),
            RouteStop("150032008005", "Two Brewers", "Ongar", 51.7078, 0.2442, 11),
            RouteStop("150032008001", "Ongar Four Wantz", "Ongar", 51.7145, 0.2435, 12),
        )

    fun inboundStops(): List<RouteStop> =
        outboundStops().reversed().mapIndexed { index, stop ->
            stop.copy(sequenceOrder = index + 1)
        }

    fun outboundTimingLinks(): List<TimingLink> {
        val stops = outboundStops()
        val runTimes = listOf(4, 3, 4, 2, 4, 2, 3, 3, 3, 2, 3)
        return stops.zipWithNext().mapIndexed { idx, (from, to) ->
            TimingLink(
                fromAtcoCode = from.atcoCode,
                toAtcoCode = to.atcoCode,
                runTimeMinutes = runTimes.getOrElse(idx) { 3 },
            )
        }
    }

    fun inboundTimingLinks(): List<TimingLink> {
        val stops = inboundStops()
        val runTimes = listOf(3, 2, 3, 3, 3, 2, 4, 2, 4, 3, 4)
        return stops.zipWithNext().mapIndexed { idx, (from, to) ->
            TimingLink(
                fromAtcoCode = from.atcoCode,
                toAtcoCode = to.atcoCode,
                runTimeMinutes = runTimes.getOrElse(idx) { 3 },
            )
        }
    }

    fun journeys(): List<VehicleJourney> {
        val weekdays = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")
        val morningDeps = listOf("07:15", "07:40", "08:30", "09:30", "10:30", "12:30", "14:30")
        val afternoonDeps = listOf("07:45", "08:58", "13:58", "15:25", "16:08", "17:08", "18:08")

        val outbound =
            morningDeps.mapIndexed { idx, dep ->
                VehicleJourney(
                    journeyCode = "NIBS-21-OUT-${idx + 1}",
                    lineRef = "21",
                    operatorRef = "NIBS",
                    direction = Direction.BRENTWOOD_TO_ONGAR,
                    originAtcoCode = "1500IM2459B",
                    destinationAtcoCode = "150032008001",
                    departureTimeLocal = dep,
                    daysOfWeek = weekdays,
                    schoolDaysOnly = dep == "07:40",
                )
            }

        val inbound =
            afternoonDeps.mapIndexed { idx, dep ->
                VehicleJourney(
                    journeyCode = "NIBS-21-IN-${idx + 1}",
                    lineRef = "21",
                    operatorRef = "NIBS",
                    direction = Direction.ONGAR_TO_BRENTWOOD,
                    originAtcoCode = "150032008001",
                    destinationAtcoCode = "1500IM2459B",
                    departureTimeLocal = dep,
                    daysOfWeek = weekdays,
                    schoolDaysOnly = dep == "15:25",
                )
            }

        return outbound + inbound
    }

    fun create(): RouteTimetable =
        RouteTimetable(
            operatorCode = "NIBS",
            lineName = "21",
            outboundStops = outboundStops(),
            inboundStops = inboundStops(),
            outboundTimingLinks = outboundTimingLinks(),
            inboundTimingLinks = inboundTimingLinks(),
            journeys = journeys(),
        )
}
