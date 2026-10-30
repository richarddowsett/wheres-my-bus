package uk.co.wheresmybus.adapter.outbound.notifications

import org.slf4j.LoggerFactory
import uk.co.wheresmybus.domain.model.PushNotificationPayload
import uk.co.wheresmybus.domain.port.outbound.NotificationDispatchPort
import java.util.concurrent.CopyOnWriteArrayList

class LoggingNotificationAdapter : NotificationDispatchPort {
    private val logger = LoggerFactory.getLogger(LoggingNotificationAdapter::class.java)
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
