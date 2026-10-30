package uk.co.wheresmybus.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.co.wheresmybus.adapter.outbound.persistence.FirestorePersistenceAdapter
import uk.co.wheresmybus.adapter.outbound.persistence.FirestorePersistenceAdapter.FirestoreCommitRequest
import uk.co.wheresmybus.adapter.outbound.persistence.FirestorePersistenceAdapter.FirestoreDocument
import uk.co.wheresmybus.adapter.outbound.persistence.FirestorePersistenceAdapter.FirestoreListResponse
import uk.co.wheresmybus.adapter.outbound.persistence.GcpMetadataAccessTokenProvider
import uk.co.wheresmybus.adapter.outbound.persistence.InMemoryPersistenceAdapter
import uk.co.wheresmybus.createDefaultPersistencePort
import uk.co.wheresmybus.domain.model.BusAlertStatus
import uk.co.wheresmybus.domain.model.DeviceSubscription
import uk.co.wheresmybus.domain.model.Direction
import uk.co.wheresmybus.domain.model.HistoricalTripRecord
import uk.co.wheresmybus.domain.model.TripNotificationState
import uk.co.wheresmybus.domain.model.WatchSession
import uk.co.wheresmybus.domain.port.outbound.BusStatePersistencePort
import uk.co.wheresmybus.domain.service.DefaultRoute21Timetable
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistencePortContractTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private fun createMockFirestoreHttpClient(): HttpClient {
        val collections = ConcurrentHashMap<String, ConcurrentHashMap<String, FirestoreDocument>>()
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

        val engine =
            MockEngine { request ->
                val urlStr = request.url.toString()
                val path = request.url.encodedPath

                when {
                    urlStr.contains("computeMetadata/v1/instance/service-accounts/default/token") -> {
                        assertEquals("Google", request.headers["Metadata-Flavor"])
                        respond("""{"access_token":"mock-gcp-oauth-token"}""", HttpStatusCode.OK, jsonHeaders)
                    }

                    path.endsWith(":commit") && request.method == HttpMethod.Post -> {
                        assertEquals("Bearer mock-gcp-oauth-token", request.headers[HttpHeaders.Authorization])
                        val bodyText = (request.body as TextContent).text
                        val commitReq = json.decodeFromString<FirestoreCommitRequest>(bodyText)
                        for (write in commitReq.writes) {
                            val docName = write.update.name ?: continue
                            val segments = docName.split("/")
                            val collection = segments[segments.size - 2]
                            val docId = segments.last()
                            collections
                                .getOrPut(collection) { ConcurrentHashMap() }[docId] = write.update
                        }
                        respond("""{"writeResults":[]}""", HttpStatusCode.OK, jsonHeaders)
                    }

                    else -> {
                        val afterDocuments = path.substringAfter("/documents/", "")
                        val parts = afterDocuments.split("/").filter { it.isNotBlank() }
                        if (parts.size == 1 && request.method == HttpMethod.Get) {
                            val collection = parts[0]
                            val docs = collections[collection]?.values?.toList() ?: emptyList()
                            val body = json.encodeToString(FirestoreListResponse(documents = docs))
                            respond(body, HttpStatusCode.OK, jsonHeaders)
                        } else if (parts.size == 2) {
                            val collection = parts[0]
                            val docId = parts[1]
                            when (request.method) {
                                HttpMethod.Get -> {
                                    val doc = collections[collection]?.get(docId)
                                    if (doc == null) {
                                        respond("""{"error":"not found"}""", HttpStatusCode.NotFound, jsonHeaders)
                                    } else {
                                        respond(json.encodeToString(doc), HttpStatusCode.OK, jsonHeaders)
                                    }
                                }

                                HttpMethod.Patch -> {
                                    val bodyText = (request.body as TextContent).text
                                    val doc = json.decodeFromString<FirestoreDocument>(bodyText)
                                    collections.getOrPut(collection) { ConcurrentHashMap() }[docId] = doc
                                    respond(json.encodeToString(doc), HttpStatusCode.OK, jsonHeaders)
                                }

                                HttpMethod.Delete -> {
                                    collections[collection]?.remove(docId)
                                    respond("{}", HttpStatusCode.OK, jsonHeaders)
                                }

                                else -> respond("Unsupported", HttpStatusCode.MethodNotAllowed)
                            }
                        } else {
                            respond("Not Found", HttpStatusCode.NotFound)
                        }
                    }
                }
            }

        return HttpClient(engine)
    }

    private suspend fun verifyPersistenceContract(adapter: BusStatePersistencePort) {
        assertEquals("21", adapter.getTimetable().lineName)
        adapter.saveTimetable(DefaultRoute21Timetable.create().copy(lineName = "21ON"))
        assertEquals("21ON", adapter.getTimetable().lineName)

        adapter.upsertSubscription(DeviceSubscription("daughter-ios", "ExpoPushToken[1]", enabled = true))
        adapter.upsertSubscription(DeviceSubscription("disabled-device", "ExpoPushToken[2]", enabled = false))
        val activeSubs = adapter.getSubscriptions()
        assertEquals(1, activeSubs.size)
        assertEquals("daughter-ios", activeSubs.first().deviceId)

        adapter.saveWatchSession(
            WatchSession(
                id = "active-watch",
                deviceId = "daughter-ios",
                pushToken = "ExpoPushToken[1]",
                direction = Direction.BRENTWOOD_TO_ONGAR,
                boardingAtco = "1500IM2459B",
                alightingAtco = "150032008001",
                expiresAtEpochSeconds = 2_000L,
            ),
        )
        adapter.saveWatchSession(
            WatchSession(
                id = "expired-watch",
                deviceId = "daughter-ios",
                pushToken = "ExpoPushToken[1]",
                direction = Direction.ONGAR_TO_BRENTWOOD,
                boardingAtco = "150032008001",
                alightingAtco = "1500IM2459B",
                expiresAtEpochSeconds = 500L,
            ),
        )
        val sessions = adapter.getActiveWatchSessions(nowEpochSeconds = 1_000L)
        assertEquals(1, sessions.size)
        assertEquals("active-watch", sessions.first().id)

        assertNull(adapter.getNotificationState("sub:daughter-ios:BRENTWOOD_TO_ONGAR"))
        adapter.saveNotificationState(
            TripNotificationState(
                subscriptionKey = "sub:daughter-ios:BRENTWOOD_TO_ONGAR",
                journeyCode = "NIBS-21-OUT-2",
                lastStatus = BusAlertStatus.DELAYED,
                lastEtaMinutes = 6,
                alertedApproaching = false,
                updatedAtIso = "2026-10-30T07:45:00Z",
            ),
        )
        val loadedState = adapter.getNotificationState("sub:daughter-ios:BRENTWOOD_TO_ONGAR")
        assertNotNull(loadedState)
        assertEquals(BusAlertStatus.DELAYED, loadedState.lastStatus)

        assertNull(adapter.getLastHistorySyncIso())
        val records =
            listOf(
                HistoricalTripRecord(
                    "2026-10-29",
                    "NIBS-21-OUT-1",
                    Direction.BRENTWOOD_TO_ONGAR,
                    "07:15",
                    0,
                    BusAlertStatus.ON_TIME,
                    "Ran on schedule",
                ),
                HistoricalTripRecord(
                    "2026-10-30",
                    "NIBS-21-IN-4",
                    Direction.ONGAR_TO_BRENTWOOD,
                    "15:25",
                    8,
                    BusAlertStatus.DELAYED,
                    "Delayed +8m",
                ),
            )
        adapter.upsertHistoricalRecords(records, "2026-10-30T19:00:00Z")
        assertEquals("2026-10-30T19:00:00Z", adapter.getLastHistorySyncIso())

        val storedHistory = adapter.getHistoricalRecords()
        assertEquals(2, storedHistory.size)
        assertEquals("2026-10-30", storedHistory.first().dateIso)
    }

    @Test
    fun `InMemoryPersistenceAdapter satisfies full BusStatePersistencePort contract`() =
        runBlocking {
            verifyPersistenceContract(InMemoryPersistenceAdapter())
        }

    @Test
    fun `FirestorePersistenceAdapter satisfies full BusStatePersistencePort contract via REST API`() =
        runBlocking {
            val httpClient = createMockFirestoreHttpClient()
            val tokenProvider =
                GcpMetadataAccessTokenProvider(
                    httpClient = httpClient,
                    staticToken = null,
                )
            val firestoreAdapter =
                FirestorePersistenceAdapter(
                    httpClient = httpClient,
                    projectId = "wheres-my-bus-513220",
                    tokenProvider = tokenProvider,
                )
            verifyPersistenceContract(firestoreAdapter)
        }

    @Test
    fun `createDefaultPersistencePort and Firestore base URL helpers resolve expected adapters`() {
        val inMem =
            createDefaultPersistencePort(
                storageMode = "IN_MEMORY",
                projectId = null,
                kService = null,
                httpClient = createMockFirestoreHttpClient(),
            )
        assertIs<InMemoryPersistenceAdapter>(inMem)

        val firestore =
            createDefaultPersistencePort(
                storageMode = "FIRESTORE",
                projectId = "wheres-my-bus-513220",
                kService = "wheres-my-bus-api",
                httpClient = createMockFirestoreHttpClient(),
            )
        assertIs<FirestorePersistenceAdapter>(firestore)

        assertEquals(
            "http://localhost:8085",
            FirestorePersistenceAdapter.resolveFirestoreBaseUrl("localhost:8085"),
        )
        assertEquals(
            "https://firestore.googleapis.com",
            FirestorePersistenceAdapter.resolveFirestoreBaseUrl(null),
        )
        assertTrue(FirestorePersistenceAdapter.sanitizeDocId("sub:dev:1").contains("sub_dev_1"))
    }
}
