package uk.co.wheresmybus.bods

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import uk.co.wheresmybus.model.Direction
import uk.co.wheresmybus.model.RouteStop
import uk.co.wheresmybus.model.RouteTimetable
import uk.co.wheresmybus.model.TimingLink
import uk.co.wheresmybus.model.VehicleJourney
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class TransXChangeParser {
    fun parse(xmlContent: String): RouteTimetable {
        if (xmlContent.isBlank()) {
            return defaultRoute21Timetable()
        }
        val doc = parseXmlDocument(xmlContent) ?: return defaultRoute21Timetable()
        val stopsByAtco = parseAnnotatedStopPoints(doc)
        if (stopsByAtco.isEmpty()) {
            return defaultRoute21Timetable()
        }

        val outboundLinks = mutableListOf<TimingLink>()
        val inboundLinks = mutableListOf<TimingLink>()
        parseJourneyPatternSections(doc, outboundLinks, inboundLinks)

        val outboundStops = buildOrderedStops(outboundLinks, stopsByAtco)
        val inboundStops =
            if (inboundLinks.isNotEmpty()) {
                buildOrderedStops(inboundLinks, stopsByAtco)
            } else {
                outboundStops.reversed().mapIndexed { idx, stop -> stop.copy(sequenceOrder = idx + 1) }
            }

        val journeys = parseVehicleJourneys(doc)
        val lineName = firstElementText(doc, "LineName") ?: "21"
        val operatorCode = firstElementText(doc, "NationalOperatorCode") ?: "NIBS"

        return RouteTimetable(
            operatorCode = operatorCode,
            lineName = lineName,
            outboundStops = outboundStops.ifEmpty { defaultOutboundStops() },
            inboundStops = inboundStops.ifEmpty { defaultInboundStops() },
            outboundTimingLinks = outboundLinks.ifEmpty { defaultOutboundTimingLinks() },
            inboundTimingLinks = inboundLinks.ifEmpty { defaultInboundTimingLinks() },
            journeys = journeys.ifEmpty { defaultJourneys() },
        )
    }

    private fun parseAnnotatedStopPoints(doc: Document): Map<String, RouteStop> {
        val result = linkedMapOf<String, RouteStop>()
        val nodes = doc.getElementsByTagName("AnnotatedStopPointRef")
        for (i in 0 until nodes.length) {
            val stop = (nodes.item(i) as? Element)?.let { parseSingleStopPoint(it, i + 1) }
            if (stop != null) {
                result[stop.atcoCode] = stop
            }
        }
        return result
    }

    private fun parseSingleStopPoint(
        el: Element,
        order: Int,
    ): RouteStop? {
        val atco = childText(el, "StopPointRef") ?: return null
        val name = childText(el, "CommonName") ?: atco
        val locality = childText(el, "LocalityName") ?: "Essex"
        val lat = childText(el, "Latitude")?.toDoubleOrNull() ?: 51.6200
        val lon = childText(el, "Longitude")?.toDoubleOrNull() ?: 0.2900
        return RouteStop(
            atcoCode = atco,
            commonName = name,
            locality = locality,
            latitude = lat,
            longitude = lon,
            sequenceOrder = order,
        )
    }

    private fun parseJourneyPatternSections(
        doc: Document,
        outboundLinks: MutableList<TimingLink>,
        inboundLinks: MutableList<TimingLink>,
    ) {
        val sections = doc.getElementsByTagName("JourneyPatternSection")
        for (s in 0 until sections.length) {
            val section = sections.item(s) as? Element ?: continue
            val sectionId = section.getAttribute("id").lowercase()
            val isInbound = sectionId.contains("in") || sectionId.contains("ret")
            val targetList = if (isInbound) inboundLinks else outboundLinks
            parseSectionLinks(section, targetList)
        }
    }

    private fun parseSectionLinks(
        section: Element,
        targetList: MutableList<TimingLink>,
    ) {
        val linkNodes = section.getElementsByTagName("JourneyPatternTimingLink")
        for (l in 0 until linkNodes.length) {
            val timingLink = (linkNodes.item(l) as? Element)?.let { parseSingleTimingLink(it) }
            if (timingLink != null) {
                targetList.add(timingLink)
            }
        }
    }

    private fun parseSingleTimingLink(linkEl: Element): TimingLink? {
        val fromEl = linkEl.getElementsByTagName("From").item(0) as? Element ?: return null
        val toEl = linkEl.getElementsByTagName("To").item(0) as? Element ?: return null
        val fromAtco = childText(fromEl, "StopPointRef") ?: return null
        val toAtco = childText(toEl, "StopPointRef") ?: return null
        val runTimeIso = childText(linkEl, "RunTime") ?: "PT3M"
        val minutes = parseIsoDurationMinutes(runTimeIso).coerceAtLeast(1)
        return TimingLink(
            fromAtcoCode = fromAtco,
            toAtcoCode = toAtco,
            runTimeMinutes = minutes,
        )
    }

    private fun buildOrderedStops(
        links: List<TimingLink>,
        stopsByAtco: Map<String, RouteStop>,
    ): List<RouteStop> {
        if (links.isEmpty()) {
            return stopsByAtco.values.mapIndexed { idx, stop -> stop.copy(sequenceOrder = idx + 1) }
        }
        val orderedAtcos = mutableListOf<String>()
        links.forEach { link ->
            if (!orderedAtcos.contains(link.fromAtcoCode)) {
                orderedAtcos.add(link.fromAtcoCode)
            }
            if (!orderedAtcos.contains(link.toAtcoCode)) {
                orderedAtcos.add(link.toAtcoCode)
            }
        }
        return orderedAtcos.mapIndexedNotNull { idx, atco ->
            stopsByAtco[atco]?.copy(sequenceOrder = idx + 1)
        }
    }

    private fun parseVehicleJourneys(doc: Document): List<VehicleJourney> {
        val journeys = mutableListOf<VehicleJourney>()
        val nodes = doc.getElementsByTagName("VehicleJourney")
        for (i in 0 until nodes.length) {
            val journey = (nodes.item(i) as? Element)?.let { parseSingleVehicleJourney(it, i + 1) }
            if (journey != null) {
                journeys.add(journey)
            }
        }
        return journeys
    }

    private fun parseSingleVehicleJourney(
        el: Element,
        index: Int,
    ): VehicleJourney? {
        val rawDep = childText(el, "DepartureTime") ?: return null
        val code = childText(el, "VehicleJourneyCode") ?: "VJ-$index"
        val depTime = rawDep.take(5)
        val dirText = (childText(el, "Direction") ?: childText(el, "JourneyPatternRef") ?: "").lowercase()
        val direction =
            if (dirText.contains("in") || dirText.contains("brentwood")) {
                Direction.ONGAR_TO_BRENTWOOD
            } else {
                Direction.BRENTWOOD_TO_ONGAR
            }
        val origin =
            childText(el, "OriginStopRef")
                ?: if (direction == Direction.BRENTWOOD_TO_ONGAR) "1500IM2459B" else "150032008001"
        val destination =
            childText(el, "DestinationStopRef")
                ?: if (direction == Direction.BRENTWOOD_TO_ONGAR) "150032008001" else "1500IM2459B"
        val schoolOnly = el.getElementsByTagName("SchoolDay").length > 0
        return VehicleJourney(
            journeyCode = code,
            lineRef = "21",
            operatorRef = "NIBS",
            direction = direction,
            originAtcoCode = origin,
            destinationAtcoCode = destination,
            departureTimeLocal = depTime,
            daysOfWeek = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
            schoolDaysOnly = schoolOnly,
        )
    }

    companion object {
        fun parseIsoDurationMinutes(iso: String): Int {
            val trimmed = iso.trim()
            if (trimmed.isEmpty()) return 0
            val negative = trimmed.startsWith("-")
            val minutesMatch = Regex("(\\d+)M").find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val hoursMatch = Regex("(\\d+)H").find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val secondsMatch = Regex("(\\d+)S").find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val totalMinutes = hoursMatch * 60 + minutesMatch + if (secondsMatch >= 30) 1 else 0
            return if (negative) -totalMinutes else totalMinutes
        }

        internal fun parseXmlDocument(xml: String): Document? =
            runCatching {
                val factory = DocumentBuilderFactory.newInstance()
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                factory.isNamespaceAware = false
                val builder = factory.newDocumentBuilder()
                builder.parse(InputSource(StringReader(xml.trim())))
            }.getOrNull()

        internal fun childText(
            parent: Element,
            tagName: String,
        ): String? {
            val list: NodeList = parent.getElementsByTagName(tagName)
            if (list.length == 0) return null
            return list.item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        }

        internal fun firstElementText(
            doc: Document,
            tagName: String,
        ): String? {
            val list = doc.getElementsByTagName(tagName)
            if (list.length == 0) return null
            return list.item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        }

        fun defaultOutboundStops(): List<RouteStop> =
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

        fun defaultInboundStops(): List<RouteStop> =
            defaultOutboundStops().reversed().mapIndexed { index, stop ->
                stop.copy(sequenceOrder = index + 1)
            }

        fun defaultOutboundTimingLinks(): List<TimingLink> {
            val stops = defaultOutboundStops()
            val runTimes = listOf(4, 3, 4, 2, 4, 2, 3, 3, 3, 2, 3)
            return stops.zipWithNext().mapIndexed { idx, (from, to) ->
                TimingLink(
                    fromAtcoCode = from.atcoCode,
                    toAtcoCode = to.atcoCode,
                    runTimeMinutes = runTimes.getOrElse(idx) { 3 },
                )
            }
        }

        fun defaultInboundTimingLinks(): List<TimingLink> {
            val stops = defaultInboundStops()
            val runTimes = listOf(3, 2, 3, 3, 3, 2, 4, 2, 4, 3, 4)
            return stops.zipWithNext().mapIndexed { idx, (from, to) ->
                TimingLink(
                    fromAtcoCode = from.atcoCode,
                    toAtcoCode = to.atcoCode,
                    runTimeMinutes = runTimes.getOrElse(idx) { 3 },
                )
            }
        }

        fun defaultJourneys(): List<VehicleJourney> {
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

        fun defaultRoute21Timetable(): RouteTimetable =
            RouteTimetable(
                operatorCode = "NIBS",
                lineName = "21",
                outboundStops = defaultOutboundStops(),
                inboundStops = defaultInboundStops(),
                outboundTimingLinks = defaultOutboundTimingLinks(),
                inboundTimingLinks = defaultInboundTimingLinks(),
                journeys = defaultJourneys(),
            )
    }
}
