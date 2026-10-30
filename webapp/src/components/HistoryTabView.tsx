import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { HistoricalStatsResponse } from '../types/bus';
import { getStatusTheme } from '../utils/commuteUtils';

export interface HistoryTabViewProps {
  stats: HistoricalStatsResponse | null;
  onRunDailyBatch: () => void;
  onRunWeekBackfill: () => void;
}

export const HistoryTabView: React.FC<HistoryTabViewProps> = ({
  stats,
  onRunDailyBatch,
  onRunWeekBackfill,
}) => {
  if (!stats) {
    return (
      <View testID="history-empty-state" style={styles.card}>
        <Text style={styles.sectionTitle}>Loading 7-Day Route 21 History...</Text>
      </View>
    );
  }

  return (
    <View testID="history-tab-view">
      <View style={styles.card}>
        <View style={styles.headerRow}>
          <View>
            <Text style={styles.sectionTitle}>
              Last {stats.windowDays} Days Punctuality & Cancellations
            </Text>
            <Text style={styles.subtitle}>
              {stats.totalScheduledRuns} tracked school runs • Avg delay{' '}
              {stats.averageDelayMinutes}m
            </Text>
          </View>
          <View style={styles.actionRow}>
            <Pressable
              testID="run-daily-batch-button"
              style={styles.batchButton}
              onPress={onRunDailyBatch}
            >
              <Text style={styles.batchButtonText}>↻ Run Daily Batch</Text>
            </Pressable>
            <Pressable
              testID="run-week-backfill-button"
              style={styles.secondaryBatchButton}
              onPress={onRunWeekBackfill}
            >
              <Text style={styles.secondaryBatchButtonText}>
                ⟲ Reload 7-Day Data
              </Text>
            </Pressable>
          </View>
        </View>

        <View style={styles.kpiGrid}>
          <View style={[styles.kpiCard, styles.onTimeKpi]}>
            <Text style={styles.kpiLabel}>On Time</Text>
            <Text testID="history-ontime-pct" style={styles.kpiValueGreen}>
              {stats.onTimePercentage}%
            </Text>
            <Text style={styles.kpiSub}>
              {stats.onTimeCount} of {stats.totalScheduledRuns} runs
            </Text>
          </View>

          <View style={[styles.kpiCard, styles.lateKpi]}>
            <Text style={styles.kpiLabel}>Late (≥5m)</Text>
            <Text testID="history-late-pct" style={styles.kpiValueAmber}>
              {stats.latePercentage}%
            </Text>
            <Text style={styles.kpiSub}>
              {stats.lateCount} of {stats.totalScheduledRuns} runs
            </Text>
          </View>

          <View style={[styles.kpiCard, styles.cancelledKpi]}>
            <Text style={styles.kpiLabel}>Cancelled / Missing</Text>
            <Text testID="history-cancelled-pct" style={styles.kpiValueRed}>
              {stats.cancelledOrMissingPercentage}%
            </Text>
            <Text style={styles.kpiSub}>
              {stats.cancelledOrMissingCount} of {stats.totalScheduledRuns} runs
            </Text>
          </View>
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Daily Breakdown (Today - 1 Week)</Text>
        {stats.dailyBreakdown.map((day) => (
          <View
            key={day.dateIso}
            testID={`daily-history-row-${day.dateIso}`}
            style={styles.dailyRow}
          >
            <Text style={styles.dayDate}>{day.dateIso}</Text>
            <View style={styles.dayPills}>
              <Text style={styles.pillGreen}>🟢 {day.onTimeCount} On Time</Text>
              <Text style={styles.pillAmber}>⏰ {day.lateCount} Late</Text>
              <Text style={styles.pillRed}>
                🚨 {day.cancelledOrMissingCount} Cancelled
              </Text>
            </View>
            <Text style={styles.dayAvg}>Avg +{day.averageDelayMinutes}m</Text>
          </View>
        ))}
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Recent School Commute Runs</Text>
        {stats.recentTrips.map((trip) => {
          const theme = getStatusTheme(trip.status, trip.delayMinutes);
          return (
            <View
              key={`${trip.dateIso}-${trip.journeyCode}`}
              style={styles.tripRow}
            >
              <View style={styles.tripHeader}>
                <Text style={styles.tripTitle}>
                  {trip.dateIso} • {trip.scheduledDeparture} (
                  {trip.direction === 'BRENTWOOD_TO_ONGAR'
                    ? 'Brentwood → Ongar'
                    : 'Ongar → Brentwood'}
                  )
                </Text>
                <Text
                  style={[
                    styles.tripBadge,
                    {
                      backgroundColor: theme.badgeBackground,
                      color: theme.badgeText,
                    },
                  ]}
                >
                  {theme.label}
                </Text>
              </View>
              <Text style={styles.tripNote}>{trip.summaryNote}</Text>
            </View>
          );
        })}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 16,
    marginTop: 12,
    borderWidth: 1,
    borderColor: '#E2E8F0',
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 14,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#0F172A',
  },
  subtitle: {
    fontSize: 13,
    color: '#475569',
    marginTop: 2,
  },
  actionRow: {
    flexDirection: 'row',
    gap: 8,
  },
  batchButton: {
    backgroundColor: '#2563EB',
    paddingHorizontal: 10,
    paddingVertical: 7,
    borderRadius: 8,
  },
  batchButtonText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '700',
  },
  secondaryBatchButton: {
    backgroundColor: '#F1F5F9',
    paddingHorizontal: 10,
    paddingVertical: 7,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#CBD5E1',
  },
  secondaryBatchButtonText: {
    color: '#1E293B',
    fontSize: 12,
    fontWeight: '600',
  },
  kpiGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  kpiCard: {
    flex: 1,
    minWidth: 150,
    padding: 12,
    borderRadius: 10,
    borderWidth: 1,
  },
  onTimeKpi: {
    backgroundColor: '#F0FDF4',
    borderColor: '#BBF7D0',
  },
  lateKpi: {
    backgroundColor: '#FFFBEB',
    borderColor: '#FDE68A',
  },
  cancelledKpi: {
    backgroundColor: '#FEF2F2',
    borderColor: '#FECACA',
  },
  kpiLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: '#334155',
  },
  kpiValueGreen: {
    fontSize: 28,
    fontWeight: '900',
    color: '#166534',
    marginVertical: 2,
  },
  kpiValueAmber: {
    fontSize: 28,
    fontWeight: '900',
    color: '#92400E',
    marginVertical: 2,
  },
  kpiValueRed: {
    fontSize: 28,
    fontWeight: '900',
    color: '#991B1B',
    marginVertical: 2,
  },
  kpiSub: {
    fontSize: 12,
    color: '#475569',
  },
  dailyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#F1F5F9',
    gap: 8,
  },
  dayDate: {
    fontSize: 14,
    fontWeight: '700',
    color: '#0F172A',
    minWidth: 95,
  },
  dayPills: {
    flexDirection: 'row',
    gap: 8,
    flexWrap: 'wrap',
  },
  pillGreen: {
    fontSize: 12,
    fontWeight: '600',
    color: '#166534',
  },
  pillAmber: {
    fontSize: 12,
    fontWeight: '600',
    color: '#92400E',
  },
  pillRed: {
    fontSize: 12,
    fontWeight: '600',
    color: '#991B1B',
  },
  dayAvg: {
    fontSize: 12,
    fontWeight: '700',
    color: '#475569',
  },
  tripRow: {
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#F1F5F9',
  },
  tripHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 6,
  },
  tripTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#0F172A',
  },
  tripBadge: {
    fontSize: 11,
    fontWeight: '800',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 999,
  },
  tripNote: {
    fontSize: 12,
    color: '#475569',
    marginTop: 3,
  },
});
