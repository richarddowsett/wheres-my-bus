package uk.co.wheresmybus.adapter.outbound.bods

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import uk.co.wheresmybus.domain.model.Direction
import uk.co.wheresmybus.domain.model.RouteStop
import uk.co.wheresmybus.domain.model.RouteTimetable
import uk.co.wheresmybus.domain.model.TimingLink
import uk.co.wheresmybus.domain.model.VehicleJourney
import uk.co.wheresmybus.domain.service.DefaultRoute21Timetable
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
            outboundTimingLinks = outboundLinks.ifEmpty { DefaultRoute21Timetable.outboundTimingLinks() },
            inboundTimingLinks = inboundLinks.ifEmpty { DefaultRoute21Timetable.inboundTimingLinks() },
            journeys = journeys.ifEmpty { DefaultRoute21Timetable.journeys() },
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

        fun defaultOutboundStops(): List<RouteStop> = DefaultRoute21Timetable.outboundStops()

        fun defaultInboundStops(): List<RouteStop> = DefaultRoute21Timetable.inboundStops()

        fun defaultRoute21Timetable(): RouteTimetable = DefaultRoute21Timetable.create()
    }
}
