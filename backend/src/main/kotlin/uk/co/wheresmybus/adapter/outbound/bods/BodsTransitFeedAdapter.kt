package uk.co.wheresmybus.adapter.outbound.bods

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import uk.co.wheresmybus.domain.model.DisruptionSituation
import uk.co.wheresmybus.domain.model.LiveVehicle
import uk.co.wheresmybus.domain.model.RouteTimetable
import uk.co.wheresmybus.domain.port.outbound.TransitFeedPort
import uk.co.wheresmybus.domain.service.DefaultRoute21Timetable

class BodsTransitFeedAdapter(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://data.bus-data.dft.gov.uk/api/v1",
    private val transXChangeParser: TransXChangeParser = TransXChangeParser(),
    private val siriVmParser: SiriVmParser = SiriVmParser(),
    private val siriSxParser: SiriSxParser = SiriSxParser(),
) : TransitFeedPort {
    override suspend fun fetchRoute21Timetable(
        noc: String,
        lineRef: String,
    ): RouteTimetable {
        if (apiKey.isBlank() || apiKey == "replace-with-bods-api-key") {
            return DefaultRoute21Timetable.create()
        }
        return runCatching {
            val response =
                httpClient.get("$baseUrl/dataset/") {
                    parameter("noc", noc)
                    parameter("lineRef", lineRef)
                    parameter("api_key", apiKey)
                }
            if (!response.status.isSuccess()) {
                return DefaultRoute21Timetable.create()
            }
            val body = response.bodyAsText()
            if (body.trimStart().startsWith("<")) {
                transXChangeParser.parse(body)
            } else {
                DefaultRoute21Timetable.create()
            }
        }.getOrElse { DefaultRoute21Timetable.create() }
    }

    override suspend fun fetchLiveVehicles(
        operatorRef: String,
        lineRef: String,
    ): List<LiveVehicle> {
        if (apiKey.isBlank() || apiKey == "replace-with-bods-api-key") {
            return emptyList()
        }
        return runCatching {
            val response =
                httpClient.get("$baseUrl/datafeed/") {
                    parameter("operatorRef", operatorRef)
                    parameter("lineRef", lineRef)
                    parameter("api_key", apiKey)
                }
            if (!response.status.isSuccess()) {
                emptyList()
            } else {
                siriVmParser.parse(response.bodyAsText())
            }
        }.getOrElse { emptyList() }
    }

    override suspend fun fetchDisruptions(route21Stops: Set<String>): List<DisruptionSituation> {
        if (apiKey.isBlank() || apiKey == "replace-with-bods-api-key") {
            return emptyList()
        }
        return runCatching {
            val response =
                httpClient.get("$baseUrl/siri-sx/") {
                    parameter("api_key", apiKey)
                }
            if (!response.status.isSuccess()) {
                emptyList()
            } else {
                siriSxParser.parse(response.bodyAsText(), route21Stops)
            }
        }.getOrElse { emptyList() }
    }
}
