package uk.co.wheresmybus.notifications

import uk.co.wheresmybus.model.BusAlertStatus
import uk.co.wheresmybus.model.LiveBusStatus
import uk.co.wheresmybus.model.PushNotificationPayload
import uk.co.wheresmybus.model.TripNotificationState

class NotificationPolicy {
    fun evaluateNotifications(
        pushToken: String,
        subscriptionKey: String,
        liveStatus: LiveBusStatus,
        previousState: TripNotificationState?,
    ): Pair<List<PushNotificationPayload>, TripNotificationState> {
        val payloads = mutableListOf<PushNotificationPayload>()
        val isSameJourney = previousState?.journeyCode == liveStatus.activeJourneyCode
        val statusChanged = !isSameJourney || previousState?.lastStatus != liveStatus.status
        val shouldAlertApproaching =
            isApproachingStop(liveStatus) && (!isSameJourney || previousState?.alertedApproaching != true)

        if (shouldSendHighPriority(liveStatus.status, statusChanged, shouldAlertApproaching, previousState == null)) {
            val alertPrefix = resolveAlertPrefix(liveStatus.status, shouldAlertApproaching)
            payloads.add(
                PushNotificationPayload(
                    targetToken = pushToken,
                    collapseKey = ALERT_COLLAPSE_KEY,
                    title = alertPrefix + liveStatus.statusHeadline,
                    body = liveStatus.statusDetail,
                    silent = false,
                    priority = "high",
                    status = liveStatus.status,
                    etaMinutes = liveStatus.etaMinutes,
                ),
            )
        }

        val etaChanged = !isSameJourney || previousState?.lastEtaMinutes != liveStatus.etaMinutes
        if (etaChanged || statusChanged) {
            payloads.add(
                PushNotificationPayload(
                    targetToken = pushToken,
                    collapseKey = LIVE_ETA_COLLAPSE_KEY,
                    title = liveStatus.statusHeadline,
                    body = liveStatus.statusDetail,
                    silent = true,
                    priority = "normal",
                    status = liveStatus.status,
                    etaMinutes = liveStatus.etaMinutes,
                ),
            )
        }

        val nextState =
            TripNotificationState(
                subscriptionKey = subscriptionKey,
                journeyCode = liveStatus.activeJourneyCode,
                lastStatus = liveStatus.status,
                lastEtaMinutes = liveStatus.etaMinutes,
                alertedApproaching =
                    (isSameJourney && previousState?.alertedApproaching == true) || shouldAlertApproaching,
                updatedAtIso = liveStatus.evaluatedAtIso,
            )

        return payloads to nextState
    }

    private fun isApproachingStop(liveStatus: LiveBusStatus): Boolean {
        val eta = liveStatus.etaMinutes ?: return false
        val isNormalOrDelayed =
            liveStatus.status == BusAlertStatus.ON_TIME || liveStatus.status == BusAlertStatus.DELAYED
        return eta <= APPROACHING_THRESHOLD_MINUTES && isNormalOrDelayed
    }

    private fun shouldSendHighPriority(
        status: BusAlertStatus,
        statusChanged: Boolean,
        shouldAlertApproaching: Boolean,
        isFirstEvaluation: Boolean,
    ): Boolean {
        val isAbnormalStatusTransition = status != BusAlertStatus.ON_TIME && statusChanged
        return isAbnormalStatusTransition || shouldAlertApproaching || isFirstEvaluation
    }

    private fun resolveAlertPrefix(
        status: BusAlertStatus,
        shouldAlertApproaching: Boolean,
    ): String =
        when {
            status == BusAlertStatus.CANCELLED_OR_DISRUPTED -> "🚨 DISRUPTION: "
            status == BusAlertStatus.MISSING_BUS_WARNING -> "⚠️ MISSING BUS: "
            shouldAlertApproaching -> "🚌 APPROACHING: "
            status == BusAlertStatus.DELAYED -> "⏰ DELAYED: "
            else -> "🚌 "
        }

    companion object {
        const val LIVE_ETA_COLLAPSE_KEY = "route21-live-eta"
        const val ALERT_COLLAPSE_KEY = "route21-alert"
        const val APPROACHING_THRESHOLD_MINUTES = 3
    }
}
