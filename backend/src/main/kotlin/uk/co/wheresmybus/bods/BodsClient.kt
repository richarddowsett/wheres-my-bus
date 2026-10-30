package uk.co.wheresmybus.bods

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import uk.co.wheresmybus.model.DisruptionSituation
import uk.co.wheresmybus.model.LiveVehicle
import uk.co.wheresmybus.model.RouteTimetable

class BodsClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://data.bus-data.dft.gov.uk/api/v1",
    private val transXChangeParser: TransXChangeParser = TransXChangeParser(),
    private val siriVmParser: SiriVmParser = SiriVmParser(),
    private val siriSxParser: SiriSxParser = SiriSxParser(),
) {
    suspend fun fetchRoute21Timetable(
        noc: String = "NIBS",
        lineRef: String = "21",
    ): RouteTimetable {
        if (apiKey.isBlank() || apiKey == "replace-with-bods-api-key") {
            return TransXChangeParser.defaultRoute21Timetable()
        }
        return runCatching {
            val response =
                httpClient.get("$baseUrl/dataset/") {
                    parameter("noc", noc)
                    parameter("lineRef", lineRef)
                    parameter("api_key", apiKey)
                }
            if (!response.status.isSuccess()) {
                return TransXChangeParser.defaultRoute21Timetable()
            }
            val body = response.bodyAsText()
            if (body.trimStart().startsWith("<")) {
                transXChangeParser.parse(body)
            } else {
                TransXChangeParser.defaultRoute21Timetable()
            }
        }.getOrElse { TransXChangeParser.defaultRoute21Timetable() }
    }

    suspend fun fetchLiveVehicles(
        operatorRef: String = "NIBS",
        lineRef: String = "21",
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

    suspend fun fetchDisruptions(route21Stops: Set<String>): List<DisruptionSituation> {
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
