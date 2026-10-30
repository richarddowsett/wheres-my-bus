package uk.co.wheresmybus.adapter.outbound.persistence

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.co.wheresmybus.domain.model.DeviceSubscription
import uk.co.wheresmybus.domain.model.HistoricalTripRecord
import uk.co.wheresmybus.domain.model.RouteTimetable
import uk.co.wheresmybus.domain.model.TripNotificationState
import uk.co.wheresmybus.domain.model.WatchSession
import uk.co.wheresmybus.domain.port.outbound.BusStatePersistencePort
import uk.co.wheresmybus.domain.service.DefaultRoute21Timetable

fun interface AccessTokenProvider {
    suspend fun getAccessToken(): String?
}

class GcpMetadataAccessTokenProvider(
    private val httpClient: HttpClient,
    private val staticToken: String? = System.getenv("GOOGLE_ACCESS_TOKEN"),
    private val metadataUrl: String =
        "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token",
) : AccessTokenProvider {
    @Serializable
    private data class MetadataTokenResponse(
        @SerialName("access_token")
        val accessToken: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getAccessToken(): String? {
        if (!staticToken.isNullOrBlank()) {
            return staticToken
        }
        return runCatching {
            val response =
                httpClient.get(metadataUrl) {
                    header("Metadata-Flavor", "Google")
                }
            if (!response.status.isSuccess()) return null
            json.decodeFromString<MetadataTokenResponse>(response.bodyAsText()).accessToken
        }.getOrNull()
    }
}

class FirestorePersistenceAdapter(
    private val httpClient: HttpClient,
    private val projectId: String,
    private val databaseId: String = "(default)",
    private val baseUrl: String = resolveFirestoreBaseUrl(),
    private val tokenProvider: AccessTokenProvider = GcpMetadataAccessTokenProvider(httpClient),
    private val defaultTimetable: RouteTimetable = DefaultRoute21Timetable.create(),
) : BusStatePersistencePort {
    @Serializable
    internal data class FirestoreValue(
        val stringValue: String? = null,
        val integerValue: String? = null,
        val booleanValue: Boolean? = null,
    )

    @Serializable
    internal data class FirestoreDocument(
        val name: String? = null,
        val fields: Map<String, FirestoreValue> = emptyMap(),
    )

    @Serializable
    internal data class FirestoreListResponse(
        val documents: List<FirestoreDocument> = emptyList(),
        val nextPageToken: String? = null,
    )

    @Serializable
    internal data class FirestoreWriteOperation(
        val update: FirestoreDocument,
    )

    @Serializable
    internal data class FirestoreCommitRequest(
        val writes: List<FirestoreWriteOperation>,
    )

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val documentsPath: String
        get() = "projects/$projectId/databases/$databaseId/documents"

    private val documentsEndpoint: String
        get() = "${baseUrl.trimEnd('/')}/v1/$documentsPath"

    override suspend fun getTimetable(): RouteTimetable =
        fetchDocumentPayload<RouteTimetable>(COLLECTION_TIMETABLES, DOC_CURRENT_TIMETABLE)
            ?: defaultTimetable

    override suspend fun saveTimetable(timetable: RouteTimetable) {
        val doc =
            FirestoreDocument(
                fields =
                    mapOf(
                        FIELD_PAYLOAD to FirestoreValue(stringValue = json.encodeToString(timetable)),
                        "lineName" to FirestoreValue(stringValue = timetable.lineName),
                        "operatorCode" to FirestoreValue(stringValue = timetable.operatorCode),
                    ),
            )
        upsertDocument(COLLECTION_TIMETABLES, DOC_CURRENT_TIMETABLE, doc)
    }

    override suspend fun upsertSubscription(subscription: DeviceSubscription) {
        val docId = sanitizeDocId(subscription.deviceId)
        val doc =
            FirestoreDocument(
                fields =
                    mapOf(
                        FIELD_PAYLOAD to FirestoreValue(stringValue = json.encodeToString(subscription)),
                        "deviceId" to FirestoreValue(stringValue = subscription.deviceId),
                        "enabled" to FirestoreValue(booleanValue = subscription.enabled),
                    ),
            )
        upsertDocument(COLLECTION_SUBSCRIPTIONS, docId, doc)
    }

    override suspend fun getSubscriptions(): List<DeviceSubscription> =
        listCollectionPayloads<DeviceSubscription>(COLLECTION_SUBSCRIPTIONS)
            .filter { it.enabled }

    override suspend fun saveWatchSession(session: WatchSession) {
        val docId = sanitizeDocId(session.id)
        val doc =
            FirestoreDocument(
                fields =
                    mapOf(
                        FIELD_PAYLOAD to FirestoreValue(stringValue = json.encodeToString(session)),
                        "expiresAtEpochSeconds" to
                            FirestoreValue(integerValue = session.expiresAtEpochSeconds.toString()),
                    ),
            )
        upsertDocument(COLLECTION_WATCH_SESSIONS, docId, doc)
    }

    override suspend fun getActiveWatchSessions(nowEpochSeconds: Long): List<WatchSession> {
        val allSessions = listCollectionPayloads<WatchSession>(COLLECTION_WATCH_SESSIONS)
        val active = mutableListOf<WatchSession>()
        for (session in allSessions) {
            if (session.expiresAtEpochSeconds > nowEpochSeconds) {
                active.add(session)
            } else {
                deleteDocument(COLLECTION_WATCH_SESSIONS, sanitizeDocId(session.id))
            }
        }
        return active
    }

    override suspend fun getNotificationState(subscriptionKey: String): TripNotificationState? =
        fetchDocumentPayload(COLLECTION_NOTIFICATION_STATES, sanitizeDocId(subscriptionKey))

    override suspend fun saveNotificationState(state: TripNotificationState) {
        val docId = sanitizeDocId(state.subscriptionKey)
        val doc =
            FirestoreDocument(
                fields =
                    mapOf(
                        FIELD_PAYLOAD to FirestoreValue(stringValue = json.encodeToString(state)),
                        "updatedAtIso" to FirestoreValue(stringValue = state.updatedAtIso),
                    ),
            )
        upsertDocument(COLLECTION_NOTIFICATION_STATES, docId, doc)
    }

    override suspend fun upsertHistoricalRecords(
        records: List<HistoricalTripRecord>,
        syncIso: String,
    ) {
        if (records.isNotEmpty()) {
            val writes =
                records.map { record ->
                    val docId = sanitizeDocId("${record.dateIso}_${record.journeyCode}_${record.direction.name}")
                    val fullDocName = "$documentsPath/$COLLECTION_HISTORICAL_RECORDS/$docId"
                    FirestoreWriteOperation(
                        update =
                            FirestoreDocument(
                                name = fullDocName,
                                fields =
                                    mapOf(
                                        FIELD_PAYLOAD to FirestoreValue(stringValue = json.encodeToString(record)),
                                        "dateIso" to FirestoreValue(stringValue = record.dateIso),
                                        "direction" to FirestoreValue(stringValue = record.direction.name),
                                        "status" to FirestoreValue(stringValue = record.status.name),
                                    ),
                            ),
                    )
                }

            for (chunk in writes.chunked(BATCH_COMMIT_CHUNK_SIZE)) {
                val commitReq = FirestoreCommitRequest(writes = chunk)
                httpClient.post("$documentsEndpoint:commit") {
                    applyAuthHeader()
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(commitReq))
                }
            }
        }

        val syncDoc =
            FirestoreDocument(
                fields =
                    mapOf(
                        FIELD_SYNC_ISO to FirestoreValue(stringValue = syncIso),
                    ),
            )
        upsertDocument(COLLECTION_METADATA, DOC_HISTORY_SYNC, syncDoc)
    }

    override suspend fun getHistoricalRecords(): List<HistoricalTripRecord> =
        listCollectionPayloads<HistoricalTripRecord>(COLLECTION_HISTORICAL_RECORDS)
            .sortedWith(
                compareByDescending<HistoricalTripRecord> { it.dateIso }
                    .thenByDescending { it.scheduledDeparture },
            )

    override suspend fun getLastHistorySyncIso(): String? {
        val doc = fetchRawDocument(COLLECTION_METADATA, DOC_HISTORY_SYNC) ?: return null
        return doc.fields[FIELD_SYNC_ISO]?.stringValue
    }

    private suspend inline fun <reified T> fetchDocumentPayload(
        collection: String,
        docId: String,
    ): T? {
        val doc = fetchRawDocument(collection, docId) ?: return null
        val payloadJson = doc.fields[FIELD_PAYLOAD]?.stringValue ?: return null
        return runCatching { json.decodeFromString<T>(payloadJson) }.getOrNull()
    }

    private suspend fun fetchRawDocument(
        collection: String,
        docId: String,
    ): FirestoreDocument? =
        runCatching {
            val response =
                httpClient.get("$documentsEndpoint/$collection/$docId") {
                    applyAuthHeader()
                }
            if (!response.status.isSuccess()) return null
            json.decodeFromString<FirestoreDocument>(response.bodyAsText())
        }.getOrNull()

    private suspend fun upsertDocument(
        collection: String,
        docId: String,
        document: FirestoreDocument,
    ) {
        httpClient.patch("$documentsEndpoint/$collection/$docId") {
            applyAuthHeader()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(document))
        }
    }

    private suspend fun deleteDocument(
        collection: String,
        docId: String,
    ) {
        runCatching {
            httpClient.delete("$documentsEndpoint/$collection/$docId") {
                applyAuthHeader()
            }
        }
    }

    private suspend inline fun <reified T> listCollectionPayloads(collection: String): List<T> {
        val results = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val page = fetchCollectionPage(collection, pageToken) ?: break
            for (doc in page.documents) {
                val payloadJson = doc.fields[FIELD_PAYLOAD]?.stringValue ?: continue
                runCatching { json.decodeFromString<T>(payloadJson) }
                    .getOrNull()
                    ?.let { results.add(it) }
            }
            pageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return results
    }

    private suspend fun fetchCollectionPage(
        collection: String,
        pageToken: String?,
    ): FirestoreListResponse? =
        runCatching {
            val response =
                httpClient.get("$documentsEndpoint/$collection") {
                    applyAuthHeader()
                    parameter("pageSize", PAGE_SIZE)
                    if (!pageToken.isNullOrBlank()) {
                        parameter("pageToken", pageToken)
                    }
                }
            if (!response.status.isSuccess()) return null
            json.decodeFromString<FirestoreListResponse>(response.bodyAsText())
        }.getOrNull()

    private suspend fun HttpRequestBuilder.applyAuthHeader() {
        val token = tokenProvider.getAccessToken()
        if (!token.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    companion object {
        const val COLLECTION_TIMETABLES = "route21_timetables"
        const val COLLECTION_SUBSCRIPTIONS = "route21_subscriptions"
        const val COLLECTION_WATCH_SESSIONS = "route21_watch_sessions"
        const val COLLECTION_NOTIFICATION_STATES = "route21_notification_states"
        const val COLLECTION_HISTORICAL_RECORDS = "route21_historical_records"
        const val COLLECTION_METADATA = "route21_metadata"

        const val DOC_CURRENT_TIMETABLE = "current"
        const val DOC_HISTORY_SYNC = "history_sync"

        private const val FIELD_PAYLOAD = "payloadJson"
        private const val FIELD_SYNC_ISO = "lastHistorySyncIso"
        private const val BATCH_COMMIT_CHUNK_SIZE = 400
        private const val PAGE_SIZE = 1000

        fun sanitizeDocId(raw: String): String = raw.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        fun resolveFirestoreBaseUrl(emulatorHost: String? = System.getenv("FIRESTORE_EMULATOR_HOST")): String =
            if (!emulatorHost.isNullOrBlank()) {
                val normalized =
                    if (emulatorHost.startsWith("http")) emulatorHost else "http://$emulatorHost"
                normalized.trimEnd('/')
            } else {
                "https://firestore.googleapis.com"
            }
    }
}
