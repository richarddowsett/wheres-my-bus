package uk.co.wheresmybus.adapter.outbound.bods

import org.w3c.dom.Element
import uk.co.wheresmybus.domain.model.Direction
import uk.co.wheresmybus.domain.model.LiveVehicle

class SiriVmParser {
    fun parse(xmlContent: String): List<LiveVehicle> {
        if (xmlContent.isBlank()) return emptyList()
        val doc = TransXChangeParser.parseXmlDocument(xmlContent) ?: return emptyList()
        val activities = doc.getElementsByTagName("VehicleActivity")
        val vehicles = mutableListOf<LiveVehicle>()

        for (i in 0 until activities.length) {
            val vehicle = (activities.item(i) as? Element)?.let { parseSingleActivity(it, i + 1) }
            if (vehicle != null) {
                vehicles.add(vehicle)
            }
        }
        return vehicles
    }

    private fun parseSingleActivity(
        activity: Element,
        index: Int,
    ): LiveVehicle? {
        val recordedAt =
            TransXChangeParser.childText(activity, "RecordedAtTime") ?: "1970-01-01T00:00:00Z"
        val mvj =
            activity.getElementsByTagName("MonitoredVehicleJourney").item(0) as? Element
                ?: return null

        val operatorRef = TransXChangeParser.childText(mvj, "OperatorRef") ?: "NIBS"
        val lineRef = TransXChangeParser.childText(mvj, "LineRef") ?: ""
        if (!isRoute21ForNibs(operatorRef, lineRef)) return null

        val locEl = mvj.getElementsByTagName("VehicleLocation").item(0) as? Element ?: return null
        val lat = TransXChangeParser.childText(locEl, "Latitude")?.toDoubleOrNull() ?: return null
        val lon = TransXChangeParser.childText(locEl, "Longitude")?.toDoubleOrNull() ?: return null

        val vehicleRef = TransXChangeParser.childText(mvj, "VehicleRef") ?: "NIBS-VEH-$index"
        val bearing = TransXChangeParser.childText(mvj, "Bearing")?.toDoubleOrNull()
        val delayIso = TransXChangeParser.childText(mvj, "Delay")
        val delayMinutes = delayIso?.let { TransXChangeParser.parseIsoDurationMinutes(it) }

        val originRef = TransXChangeParser.childText(mvj, "OriginRef")
        val destRef = TransXChangeParser.childText(mvj, "DestinationRef")
        val dirRef = TransXChangeParser.childText(mvj, "DirectionRef")
        val aimedOriginDep =
            TransXChangeParser.childText(mvj, "OriginAimedDepartureTime")
                ?.let { extractLocalTimeHHmm(it) }

        return LiveVehicle(
            vehicleRef = vehicleRef,
            operatorRef = operatorRef,
            lineRef = lineRef,
            direction = inferDirection(dirRef, originRef, destRef),
            originAtcoCode = originRef,
            destinationAtcoCode = destRef,
            aimedOriginDepartureTime = aimedOriginDep,
            latitude = lat,
            longitude = lon,
            bearing = bearing,
            reportedDelayMinutes = delayMinutes,
            recordedAtIso = recordedAt,
        )
    }

    private fun isRoute21ForNibs(
        operatorRef: String,
        lineRef: String,
    ): Boolean {
        val normalizedOperator = operatorRef.uppercase()
        val normalizedLine = lineRef.uppercase()
        val operatorMatches = normalizedOperator.contains("NIBS") || normalizedOperator.isEmpty()
        val lineMatches = normalizedLine == "21" || normalizedLine == "21ON"
        return operatorMatches && lineMatches
    }

    private fun inferDirection(
        dirRef: String?,
        originRef: String?,
        destRef: String?,
    ): Direction? {
        val normalizedDir = dirRef?.lowercase() ?: ""
        return when {
            destRef == "150032008001" || originRef == "1500IM2459B" -> Direction.BRENTWOOD_TO_ONGAR
            destRef == "1500IM2459B" || originRef == "150032008001" -> Direction.ONGAR_TO_BRENTWOOD
            normalizedDir.contains("out") -> Direction.BRENTWOOD_TO_ONGAR
            normalizedDir.contains("in") -> Direction.ONGAR_TO_BRENTWOOD
            else -> null
        }
    }

    private fun extractLocalTimeHHmm(isoTimestamp: String): String? {
        val match = Regex("T(\\d{2}:\\d{2})").find(isoTimestamp)
        return match?.groupValues?.get(1) ?: isoTimestamp.takeIf { it.length >= 5 }?.take(5)
    }
}
