package uk.co.wheresmybus.adapter.outbound.bods

import org.w3c.dom.Element
import uk.co.wheresmybus.domain.model.DisruptionSituation

class SiriSxParser {
    fun parse(
        xmlContent: String,
        route21StopAtcoCodes: Set<String> = emptySet(),
    ): List<DisruptionSituation> {
        if (xmlContent.isBlank()) return emptyList()
        val doc = TransXChangeParser.parseXmlDocument(xmlContent) ?: return emptyList()
        val elements = doc.getElementsByTagName("PtSituationElement")
        val situations = mutableListOf<DisruptionSituation>()

        for (i in 0 until elements.length) {
            val el = elements.item(i) as? Element ?: continue
            val situationNumber =
                TransXChangeParser.childText(el, "SituationNumber") ?: "SX-${i + 1}"
            val summary =
                TransXChangeParser.childText(el, "Summary") ?: "Service disruption on Route 21"
            val description =
                TransXChangeParser.childText(el, "Description") ?: summary

            val validityEl = el.getElementsByTagName("ValidityPeriod").item(0) as? Element
            val validFrom = validityEl?.let { TransXChangeParser.childText(it, "StartTime") }
            val validTo = validityEl?.let { TransXChangeParser.childText(it, "EndTime") }

            val operators = collectTexts(el, "OperatorRef")
            val lines = collectTexts(el, "LineRef")
            val stops = collectTexts(el, "StopPointRef")
            val condition =
                TransXChangeParser.childText(el, "Condition")
                    ?: TransXChangeParser.childText(el, "Severity")
                    ?: "disrupted"

            if (affectsRoute21(operators, lines, stops, route21StopAtcoCodes)) {
                situations.add(
                    DisruptionSituation(
                        situationNumber = situationNumber,
                        summary = summary,
                        description = description,
                        validFromIso = validFrom,
                        validToIso = validTo,
                        affectedOperatorRefs = operators,
                        affectedLineRefs = lines,
                        affectedStopAtcoCodes = stops,
                        condition = condition,
                    ),
                )
            }
        }
        return situations
    }

    private fun collectTexts(
        parent: Element,
        tagName: String,
    ): List<String> {
        val nodes = parent.getElementsByTagName(tagName)
        val values = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val text = nodes.item(i)?.textContent?.trim()
            if (!text.isNullOrEmpty()) {
                values.add(text)
            }
        }
        return values.toList()
    }

    private fun affectsRoute21(
        operators: List<String>,
        lines: List<String>,
        stops: List<String>,
        route21StopAtcoCodes: Set<String>,
    ): Boolean {
        val matchesLineAndOperator =
            operators.any { it.equals("NIBS", ignoreCase = true) } &&
                (
                    lines.isEmpty() ||
                        lines.any {
                            it.equals("21", ignoreCase = true) ||
                                it.equals("21ON", ignoreCase = true)
                        }
                )
        val matchesKnownStop =
            route21StopAtcoCodes.isNotEmpty() && stops.any { route21StopAtcoCodes.contains(it) }
        return matchesLineAndOperator || matchesKnownStop
    }
}
