import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { BusApiClient } from '../api/apiClient';
import { DisruptionBanner } from '../components/DisruptionBanner';
import { HistoryTabView } from '../components/HistoryTabView';
import { RouteTimeline } from '../components/RouteTimeline';
import { StopPickerModal } from '../components/StopPickerModal';
import {
  DeviceSubscription,
  Direction,
  HistoricalStatsResponse,
  LiveBusStatus,
  RouteStop,
} from '../types/bus';
import {
  flipDirection,
  formatCountdownLabel,
  getDefaultDirection,
  getDirectionTitle,
  getStatusTheme,
} from '../utils/commuteUtils';

export type ActiveTab = 'LIVE' | 'HISTORY';

export interface DashboardScreenProps {
  apiClient: BusApiClient;
  initialHour?: number;
}

export const DashboardScreen: React.FC<DashboardScreenProps> = ({
  apiClient,
  initialHour = new Date().getHours(),
}) => {
  const [activeTab, setActiveTab] = useState<ActiveTab>('LIVE');
  const [direction, setDirection] = useState<Direction>(() =>
    getDefaultDirection(initialHour),
  );
  const [boardingAtco, setBoardingAtco] = useState<string>('1500IM2459B');
  const [alightingAtco, setAlightingAtco] = useState<string>('150032008001');
  const [stops, setStops] = useState<RouteStop[]>([]);
  const [liveStatus, setLiveStatus] = useState<LiveBusStatus | null>(null);
  const [historyStats, setHistoryStats] =
    useState<HistoricalStatsResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [watchingTrip, setWatchingTrip] = useState<boolean>(false);
  const [settingsVisible, setSettingsVisible] = useState<boolean>(false);
  const [subscriptionNotice, setSubscriptionNotice] = useState<string | null>(
    null,
  );

  const loadData = useCallback(
    async (
      targetDirection: Direction,
      targetBoarding: string,
      targetAlighting: string,
    ) => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const [fetchedStops, fetchedStatus] = await Promise.all([
          apiClient.fetchStops(targetDirection),
          apiClient.fetchLiveStatus(
            targetDirection,
            targetBoarding,
            targetAlighting,
          ),
        ]);
        setStops(fetchedStops);
        setLiveStatus(fetchedStatus);
      } catch (err) {
        setErrorMessage(
          err instanceof Error ? err.message : 'Unable to reach Bus 21 server',
        );
      } finally {
        setLoading(false);
      }
    },
    [apiClient],
  );

  const loadHistory = useCallback(async () => {
    setErrorMessage(null);
    try {
      const stats = await apiClient.fetchHistoricalStats(7);
      setHistoryStats(stats);
    } catch (err) {
      setErrorMessage(
        err instanceof Error
          ? err.message
          : 'Unable to load historical data',
      );
    }
  }, [apiClient]);

  useEffect(() => {
    void loadData(direction, boardingAtco, alightingAtco);
  }, [direction, boardingAtco, alightingAtco, loadData]);

  useEffect(() => {
    if (activeTab === 'HISTORY') {
      void loadHistory();
    }
  }, [activeTab, loadHistory]);

  const handleToggleDirection = () => {
    const nextDir = flipDirection(direction);
    const nextBoarding = alightingAtco;
    const nextAlighting = boardingAtco;
    setBoardingAtco(nextBoarding);
    setAlightingAtco(nextAlighting);
    setDirection(nextDir);
  };

  const handleWatchBus = async () => {
    try {
      await apiClient.startWatchSession({
        deviceId: 'family-web-mobile-device',
        pushToken: 'ExponentPushToken[route21-daughter-device]',
        direction,
        boardingAtco,
        alightingAtco,
        durationMinutes: 45,
      });
      setWatchingTrip(true);
      setSubscriptionNotice(
        'Live ETA notifications active for the next 45 minutes.',
      );
    } catch (err) {
      setErrorMessage(
        err instanceof Error ? err.message : 'Failed to start live watch',
      );
    }
  };

  const handleRunDailyBatch = async () => {
    try {
      await apiClient.triggerDailyHistoryBatch();
      await loadHistory();
      setSubscriptionNotice('Daily historical batch completed with latest BODS data.');
    } catch (err) {
      setErrorMessage(
        err instanceof Error ? err.message : 'Daily batch failed',
      );
    }
  };

  const handleRunWeekBackfill = async () => {
    try {
      await apiClient.triggerWeekBackfill(7);
      await loadHistory();
      setSubscriptionNotice('Loaded 7-day historical backfill (Today - 1 Week).');
    } catch (err) {
      setErrorMessage(
        err instanceof Error ? err.message : '7-day backfill failed',
      );
    }
  };

  const handleSavePreferences = async (
    newBoarding: string,
    newAlighting: string,
    newPin: string,
    subscription: DeviceSubscription,
  ) => {
    apiClient.setFamilyPin(newPin);
    await apiClient.saveSubscription(subscription);
    setBoardingAtco(newBoarding);
    setAlightingAtco(newAlighting);
    setSubscriptionNotice(
      'Automatic school-day live notifications saved and enabled!',
    );
  };

  const theme = getStatusTheme(
    liveStatus?.status ?? 'ON_TIME',
    liveStatus?.delayMinutes ?? 0,
  );
  const countdownText = formatCountdownLabel(
    liveStatus?.etaMinutes ?? null,
    liveStatus?.status ?? 'ON_TIME',
  );

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <View style={styles.topBar}>
        <View>
          <Text style={styles.brandTitle}>NIBS Bus 21 Live Monitor</Text>
          <Text testID="direction-title" style={styles.directionSubtitle}>
            {getDirectionTitle(direction)}
          </Text>
        </View>
        <View style={styles.topActions}>
          <Pressable
            testID="flip-direction-button"
            style={styles.secondaryButton}
            onPress={handleToggleDirection}
          >
            <Text style={styles.secondaryButtonText}>⇄ Flip Direction</Text>
          </Pressable>
          <Pressable
            testID="open-settings-button"
            style={styles.secondaryButton}
            onPress={() => setSettingsVisible(true)}
          >
            <Text style={styles.secondaryButtonText}>⚙️ Stops & Alerts</Text>
          </Pressable>
        </View>
      </View>

      <View style={styles.tabBar}>
        <Pressable
          testID="tab-live-monitor"
          style={[
            styles.tabButton,
            activeTab === 'LIVE' && styles.tabButtonActive,
          ]}
          onPress={() => setActiveTab('LIVE')}
        >
          <Text
            style={[
              styles.tabButtonText,
              activeTab === 'LIVE' && styles.tabButtonTextActive,
            ]}
          >
            🚌 Live Bus Monitor
          </Text>
        </Pressable>
        <Pressable
          testID="tab-historical-data"
          style={[
            styles.tabButton,
            activeTab === 'HISTORY' && styles.tabButtonActive,
          ]}
          onPress={() => setActiveTab('HISTORY')}
        >
          <Text
            style={[
              styles.tabButtonText,
              activeTab === 'HISTORY' && styles.tabButtonTextActive,
            ]}
          >
            📊 Historical Data (7-Day)
          </Text>
        </Pressable>
      </View>

      {subscriptionNotice && (
        <View testID="subscription-notice" style={styles.noticeBanner}>
          <Text style={styles.noticeText}>🔔 {subscriptionNotice}</Text>
        </View>
      )}

      {errorMessage && (
        <View testID="error-banner" style={styles.errorBanner}>
          <Text style={styles.errorText}>{errorMessage}</Text>
        </View>
      )}

      {activeTab === 'HISTORY' ? (
        <HistoryTabView
          stats={historyStats}
          onRunDailyBatch={() => void handleRunDailyBatch()}
          onRunWeekBackfill={() => void handleRunWeekBackfill()}
        />
      ) : (
        <>
          {loading && !liveStatus ? (
            <View testID="loading-indicator" style={styles.loadingBox}>
              <ActivityIndicator size="large" color="#2563EB" />
              <Text style={styles.loadingText}>
                Checking BODS live NIBS 21 feed...
              </Text>
            </View>
          ) : null}

          {liveStatus && (
            <>
              <View
                testID="live-countdown-card"
                style={[styles.heroCard, { borderColor: theme.cardBorder }]}
              >
                <View style={styles.badgeContainer}>
                  <View
                    testID="status-badge"
                    style={[
                      styles.statusBadge,
                      { backgroundColor: theme.badgeBackground },
                    ]}
                  >
                    <Text
                      style={[
                        styles.statusBadgeText,
                        { color: theme.badgeText },
                      ]}
                    >
                      {theme.icon} {theme.label}
                    </Text>
                  </View>
                  <Pressable
                    testID="refresh-status-button"
                    onPress={() =>
                      void loadData(direction, boardingAtco, alightingAtco)
                    }
                  >
                    <Text style={styles.refreshLink}>↻ Refresh</Text>
                  </Pressable>
                </View>

                <Text testID="countdown-value" style={styles.countdownLarge}>
                  {countdownText}
                </Text>

                <Text testID="status-headline" style={styles.headlineText}>
                  {liveStatus.statusHeadline}
                </Text>
                <Text testID="status-detail" style={styles.detailText}>
                  {liveStatus.statusDetail}
                </Text>

                <View style={styles.metaGrid}>
                  <View style={styles.metaCell}>
                    <Text style={styles.metaLabel}>Boarding Stop</Text>
                    <Text testID="boarding-stop-name" style={styles.metaValue}>
                      {liveStatus.boardingStop.commonName}
                    </Text>
                  </View>
                  <View style={styles.metaCell}>
                    <Text style={styles.metaLabel}>Scheduled / Expected</Text>
                    <Text
                      testID="scheduled-expected-time"
                      style={styles.metaValue}
                    >
                      {liveStatus.scheduledArrivalTime ?? '--:--'} →{' '}
                      {liveStatus.expectedArrivalTime ?? '--:--'}
                    </Text>
                  </View>
                  <View style={styles.metaCell}>
                    <Text style={styles.metaLabel}>Current Bus Location</Text>
                    <Text testID="nearest-stop-name" style={styles.metaValue}>
                      {liveStatus.nearestStopName ?? 'Awaiting GPS'}
                    </Text>
                  </View>
                </View>

                <Pressable
                  testID="watch-bus-button"
                  style={[
                    styles.watchButton,
                    watchingTrip && styles.watchButtonActive,
                  ]}
                  onPress={handleWatchBus}
                >
                  <Text style={styles.watchButtonText}>
                    {watchingTrip
                      ? '🔔 Watching This Bus (Live ETA Pushes On)'
                      : '🔔 Watch This Bus (Start Live Countdown Notification)'}
                  </Text>
                </Pressable>
              </View>

              <DisruptionBanner
                status={liveStatus.status}
                statusDetail={liveStatus.statusDetail}
                disruptions={liveStatus.activeDisruptions}
              />

              {liveStatus.upcomingDepartures.length > 0 && (
                <View
                  testID="upcoming-departures-card"
                  style={styles.upcomingCard}
                >
                  <Text style={styles.sectionTitle}>
                    Upcoming Route 21 Departures
                  </Text>
                  <View style={styles.upcomingList}>
                    {liveStatus.upcomingDepartures.map((dep) => (
                      <View key={dep.journeyCode} style={styles.upcomingPill}>
                        <Text style={styles.upcomingTime}>
                          Sched {dep.scheduledTime}
                        </Text>
                        <Text style={styles.upcomingExpected}>
                          Exp {dep.expectedTime}
                        </Text>
                      </View>
                    ))}
                  </View>
                </View>
              )}

              <RouteTimeline
                stops={stops}
                boardingAtco={liveStatus.boardingStop.atcoCode}
                alightingAtco={liveStatus.alightingStop.atcoCode}
                currentStopSequenceIndex={liveStatus.currentStopSequenceIndex}
                vehicleRef={liveStatus.vehicleRef}
              />
            </>
          )}
        </>
      )}

      <StopPickerModal
        visible={settingsVisible}
        stops={stops}
        initialBoardingAtco={boardingAtco}
        initialAlightingAtco={alightingAtco}
        initialFamilyPin={apiClient.getFamilyPin()}
        onClose={() => setSettingsVisible(false)}
        onSavePreferences={handleSavePreferences}
      />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#F8FAFC',
  },
  content: {
    padding: 16,
    maxWidth: 720,
    width: '100%',
    alignSelf: 'center',
  },
  topBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 12,
  },
  brandTitle: {
    fontSize: 22,
    fontWeight: '800',
    color: '#0F172A',
  },
  directionSubtitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#475569',
    marginTop: 2,
  },
  topActions: {
    flexDirection: 'row',
    gap: 8,
  },
  secondaryButton: {
    backgroundColor: '#E2E8F0',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
  },
  secondaryButtonText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1E293B',
  },
  tabBar: {
    flexDirection: 'row',
    backgroundColor: '#E2E8F0',
    borderRadius: 10,
    padding: 4,
    marginBottom: 14,
    gap: 6,
  },
  tabButton: {
    flex: 1,
    paddingVertical: 9,
    borderRadius: 8,
    alignItems: 'center',
  },
  tabButtonActive: {
    backgroundColor: '#FFFFFF',
  },
  tabButtonText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#475569',
  },
  tabButtonTextActive: {
    color: '#0F172A',
    fontWeight: '800',
  },
  noticeBanner: {
    backgroundColor: '#EFF6FF',
    borderColor: '#3B82F6',
    borderWidth: 1,
    padding: 10,
    borderRadius: 8,
    marginBottom: 10,
  },
  noticeText: {
    color: '#1E40AF',
    fontSize: 13,
    fontWeight: '600',
  },
  errorBanner: {
    backgroundColor: '#FEF2F2',
    borderColor: '#EF4444',
    borderWidth: 1,
    padding: 10,
    borderRadius: 8,
    marginBottom: 10,
  },
  errorText: {
    color: '#991B1B',
    fontSize: 13,
    fontWeight: '600',
  },
  loadingBox: {
    padding: 32,
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 8,
    fontSize: 14,
    color: '#475569',
  },
  heroCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 20,
    borderWidth: 2,
    marginBottom: 6,
  },
  badgeContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  statusBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
  },
  statusBadgeText: {
    fontSize: 12,
    fontWeight: '800',
  },
  refreshLink: {
    fontSize: 13,
    fontWeight: '600',
    color: '#2563EB',
  },
  countdownLarge: {
    fontSize: 42,
    fontWeight: '900',
    color: '#0F172A',
    marginBottom: 6,
  },
  headlineText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1E293B',
    marginBottom: 4,
  },
  detailText: {
    fontSize: 14,
    color: '#475569',
    marginBottom: 16,
  },
  metaGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    borderTopWidth: 1,
    borderTopColor: '#E2E8F0',
    paddingTop: 12,
    marginBottom: 16,
  },
  metaCell: {
    flex: 1,
    minWidth: 140,
  },
  metaLabel: {
    fontSize: 11,
    fontWeight: '600',
    color: '#64748B',
    textTransform: 'uppercase',
  },
  metaValue: {
    fontSize: 14,
    fontWeight: '700',
    color: '#0F172A',
    marginTop: 2,
  },
  watchButton: {
    backgroundColor: '#0F172A',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 10,
    alignItems: 'center',
  },
  watchButtonActive: {
    backgroundColor: '#16A34A',
  },
  watchButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  upcomingCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 14,
    marginTop: 10,
    borderWidth: 1,
    borderColor: '#E2E8F0',
  },
  sectionTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#0F172A',
    marginBottom: 8,
  },
  upcomingList: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  upcomingPill: {
    backgroundColor: '#F1F5F9',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
  },
  upcomingTime: {
    fontSize: 12,
    color: '#475569',
  },
  upcomingExpected: {
    fontSize: 13,
    fontWeight: '700',
    color: '#0F172A',
  },
});
