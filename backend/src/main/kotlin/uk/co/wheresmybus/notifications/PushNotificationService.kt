package uk.co.wheresmybus.notifications

import org.slf4j.LoggerFactory
import uk.co.wheresmybus.model.PushNotificationPayload
import java.util.concurrent.CopyOnWriteArrayList

interface PushNotificationDispatcher {
    suspend fun send(payload: PushNotificationPayload): Boolean
}

class LoggingPushNotificationDispatcher : PushNotificationDispatcher {
    private val logger = LoggerFactory.getLogger(LoggingPushNotificationDispatcher::class.java)
    private val dispatchedHistory = CopyOnWriteArrayList<PushNotificationPayload>()

    override suspend fun send(payload: PushNotificationPayload): Boolean {
        logger.info(
            "Dispatching push notification [collapseKey={}, silent={}, priority={}, token={}]: {} - {}",
            payload.collapseKey,
            payload.silent,
            payload.priority,
            payload.targetToken,
            payload.title,
            payload.body,
        )
        dispatchedHistory.add(payload)
        return true
    }

    fun history(): List<PushNotificationPayload> = dispatchedHistory.toList()

    fun clear() {
        dispatchedHistory.clear()
    }
}
