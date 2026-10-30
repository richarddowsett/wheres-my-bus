import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { RouteStop } from '../types/bus';
import { calculateRouteProgressPercent } from '../utils/commuteUtils';

export interface RouteTimelineProps {
  stops: RouteStop[];
  boardingAtco: string;
  alightingAtco: string;
  currentStopSequenceIndex: number | null;
  vehicleRef: string | null;
}

export const RouteTimeline: React.FC<RouteTimelineProps> = ({
  stops,
  boardingAtco,
  alightingAtco,
  currentStopSequenceIndex,
  vehicleRef,
}) => {
  const progressPercent = calculateRouteProgressPercent(
    currentStopSequenceIndex,
    stops.length,
  );

  return (
    <View testID="route-timeline" style={styles.card}>
      <View style={styles.headerRow}>
        <Text style={styles.title}>Route 21 Live Stop Progress</Text>
        <Text style={styles.progressText}>{progressPercent}% along route</Text>
      </View>

      {stops.map((stop) => {
        const isCurrentBusStop = currentStopSequenceIndex === stop.sequenceOrder;
        const isBoarding = stop.atcoCode === boardingAtco;
        const isAlighting = stop.atcoCode === alightingAtco;
        const isPassed =
          currentStopSequenceIndex !== null &&
          stop.sequenceOrder < currentStopSequenceIndex;

        return (
          <View
            key={stop.atcoCode}
            testID={`timeline-stop-${stop.atcoCode}`}
            style={[
              styles.stopRow,
              isCurrentBusStop && styles.activeBusRow,
            ]}
          >
            <View
              style={[
                styles.dot,
                isPassed && styles.passedDot,
                isCurrentBusStop && styles.busDot,
                (isBoarding || isAlighting) && styles.highlightDot,
              ]}
            />
            <View style={styles.stopInfo}>
              <Text
                style={[
                  styles.stopName,
                  isPassed && styles.passedText,
                  (isBoarding || isAlighting || isCurrentBusStop) && styles.boldText,
                ]}
              >
                {stop.commonName} ({stop.locality})
              </Text>
              <View style={styles.badgeRow}>
                {isBoarding && (
                  <Text style={styles.boardingBadge}>Boarding Stop</Text>
                )}
                {isAlighting && (
                  <Text style={styles.alightingBadge}>School / Alighting</Text>
                )}
                {isCurrentBusStop && (
                  <Text testID="live-bus-indicator" style={styles.busBadge}>
                    🚌 Bus {vehicleRef ?? '21'} is here
                  </Text>
                )}
              </View>
            </View>
          </View>
        );
      })}
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginTop: 12,
    borderWidth: 1,
    borderColor: '#E2E8F0',
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  title: {
    fontSize: 16,
    fontWeight: '700',
    color: '#0F172A',
  },
  progressText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#2563EB',
  },
  stopRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingVertical: 8,
    paddingHorizontal: 6,
    borderRadius: 8,
  },
  activeBusRow: {
    backgroundColor: '#EFF6FF',
  },
  dot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#CBD5E1',
    marginTop: 4,
    marginRight: 10,
  },
  passedDot: {
    backgroundColor: '#94A3B8',
  },
  busDot: {
    backgroundColor: '#2563EB',
  },
  highlightDot: {
    backgroundColor: '#16A34A',
  },
  stopInfo: {
    flex: 1,
  },
  stopName: {
    fontSize: 14,
    color: '#334155',
  },
  passedText: {
    color: '#94A3B8',
  },
  boldText: {
    fontWeight: '700',
    color: '#0F172A',
  },
  badgeRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginTop: 4,
  },
  boardingBadge: {
    fontSize: 11,
    fontWeight: '600',
    color: '#166534',
    backgroundColor: '#DCFCE7',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  alightingBadge: {
    fontSize: 11,
    fontWeight: '600',
    color: '#1E40AF',
    backgroundColor: '#DBEAFE',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  busBadge: {
    fontSize: 11,
    fontWeight: '700',
    color: '#FFFFFF',
    backgroundColor: '#2563EB',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
});
