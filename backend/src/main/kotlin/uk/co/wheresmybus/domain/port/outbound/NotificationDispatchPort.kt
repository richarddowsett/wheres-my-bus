package uk.co.wheresmybus.domain.port.outbound

import uk.co.wheresmybus.domain.model.PushNotificationPayload

interface NotificationDispatchPort {
    suspend fun send(payload: PushNotificationPayload): Boolean
}
