import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { BusApiClient } from '../../src/api/apiClient';
import { DashboardScreen } from '../../src/screens/DashboardScreen';
import {
  HistoricalStatsResponse,
  LiveBusStatus,
  RouteStop,
} from '../../src/types/bus';

const sampleStops: RouteStop[] = [
  {
    atcoCode: '1500IM2459B',
    commonName: 'Brentwood Railway Station',
    locality: 'Brentwood',
    latitude: 51.6136,
    longitude: 0.2995,
    sequenceOrder: 1,
  },
  {
    atcoCode: '150031044001',
    commonName: 'The Rose and Crown',
    locality: 'Pilgrims Hatch',
    latitude: 51.6418,
    longitude: 0.2895,
    sequenceOrder: 2,
  },
  {
    atcoCode: '150032008001',
    commonName: 'Ongar Four Wantz',
    locality: 'Ongar',
    latitude: 51.7145,
    longitude: 0.2435,
    sequenceOrder: 3,
  },
];

const sampleDisruptedStatus: LiveBusStatus = {
  lineName: '21',
  operatorCode: 'NIBS',
  direction: 'BRENTWOOD_TO_ONGAR',
  boardingStop: sampleStops[0],
  alightingStop: sampleStops[2],
  activeJourneyCode: 'NIBS-21-OUT-2',
  scheduledArrivalTime: '07:40',
  expectedArrivalTime: '07:48',
  etaMinutes: 8,
  delayMinutes: 8,
  status: 'CANCELLED_OR_DISRUPTED',
  statusHeadline: 'Bus 21 to Ongar: DISRUPTED / CANCELLED',
  statusDetail: 'Roadworks at Kelvedon Hatch causing severe disruption',
  nearestStopName: 'The Rose and Crown',
  currentStopSequenceIndex: 2,
  vehicleLatitude: 51.6418,
  vehicleLongitude: 0.2895,
  vehicleRef: 'NIBS-402',
  activeDisruptions: [
    {
      situationNumber: 'SX-21-01',
      summary: 'Temporary road closure on Ongar Road',
      description: 'Buses diverted via Doddinghurst.',
      affectedOperatorRefs: ['NIBS'],
      affectedLineRefs: ['21'],
      affectedStopAtcoCodes: ['150031044001'],
      condition: 'diverted',
    },
  ],
  upcomingDepartures: [
    {
      journeyCode: 'NIBS-21-OUT-2',
      scheduledTime: '07:40',
      expectedTime: '07:48',
      status: 'DELAYED',
    },
  ],
  evaluatedAtIso: '2026-10-30T07:40:00Z',
};

const sampleHistoryStats: HistoricalStatsResponse = {
  windowDays: 7,
  directionFilter: null,
  totalScheduledRuns: 35,
  onTimeCount: 25,
  onTimePercentage: 71.4,
  lateCount: 7,
  latePercentage: 20.0,
  cancelledOrMissingCount: 3,
  cancelledOrMissingPercentage: 8.6,
  averageDelayMinutes: 2.8,
  dailyBreakdown: [
    {
      dateIso: '2026-10-30',
      totalRuns: 5,
      onTimeCount: 3,
      lateCount: 1,
      cancelledOrMissingCount: 1,
      averageDelayMinutes: 3.4,
    },
  ],
  recentTrips: [
    {
      dateIso: '2026-10-30',
      journeyCode: 'NIBS-21-OUT-2',
      direction: 'BRENTWOOD_TO_ONGAR',
      scheduledDeparture: '07:40',
      delayMinutes: 7,
      status: 'DELAYED',
      summaryNote: 'Delayed +7m due to A128 / Kelvedon Hatch traffic',
    },
    {
      dateIso: '2026-10-30',
      journeyCode: 'NIBS-21-IN-4',
      direction: 'ONGAR_TO_BRENTWOOD',
      scheduledDeparture: '15:25',
      delayMinutes: 0,
      status: 'CANCELLED_OR_DISRUPTED',
      summaryNote: 'Cancelled / no NIBS SIRI-VM vehicle logged on trip',
    },
  ],
  lastBatchSyncIso: '2026-10-30T19:00:00Z',
};

describe('DashboardScreen Integration', () => {
  test('renders live countdown, disruption banner, timeline, direction flip, watch toggle, and 7-day history tab', async () => {
    const mockClient = new BusApiClient();
    jest.spyOn(mockClient, 'fetchStops').mockResolvedValue(sampleStops);
    jest.spyOn(mockClient, 'fetchLiveStatus').mockResolvedValue(sampleDisruptedStatus);
    jest.spyOn(mockClient, 'fetchHistoricalStats').mockResolvedValue(sampleHistoryStats);
    const dailyBatchSpy = jest
      .spyOn(mockClient, 'triggerDailyHistoryBatch')
      .mockResolvedValue({
        mode: 'DAILY_INCREMENTAL_BATCH',
        daysProcessed: 1,
        recordsUpserted: 5,
        completedAtIso: '2026-10-30T19:05:00Z',
      });
    const backfillSpy = jest
      .spyOn(mockClient, 'triggerWeekBackfill')
      .mockResolvedValue({
        mode: 'INITIAL_7_DAY_BACKFILL',
        daysProcessed: 7,
        recordsUpserted: 35,
        completedAtIso: '2026-10-30T19:06:00Z',
      });
    const watchSpy = jest.spyOn(mockClient, 'startWatchSession').mockResolvedValue({
      id: 'watch-100',
      deviceId: 'family-web-mobile-device',
      pushToken: 'ExponentPushToken[route21-daughter-device]',
      direction: 'BRENTWOOD_TO_ONGAR',
      boardingAtco: '1500IM2459B',
      alightingAtco: '150032008001',
      expiresAtEpochSeconds: 1800000000,
    });

    render(<DashboardScreen apiClient={mockClient} initialHour={7} />);

    await waitFor(() => {
      expect(screen.getByTestId('live-countdown-card')).toBeInTheDocument();
    });

    expect(screen.getByTestId('direction-title')).toHaveTextContent(
      'Morning School Run: Brentwood → Ongar',
    );
    expect(screen.getByTestId('countdown-value')).toHaveTextContent('Cancelled / Disrupted');
    expect(screen.getByTestId('disruption-banner')).toHaveTextContent(
      'Temporary road closure on Ongar Road',
    );
    expect(screen.getByTestId('live-bus-indicator')).toHaveTextContent('Bus NIBS-402 is here');

    fireEvent.click(screen.getByTestId('watch-bus-button'));
    await waitFor(() => {
      expect(watchSpy).toHaveBeenCalledTimes(1);
      expect(screen.getByTestId('subscription-notice')).toHaveTextContent(
        'Live ETA notifications active for the next 45 minutes.',
      );
    });

    fireEvent.click(screen.getByTestId('flip-direction-button'));
    await waitFor(() => {
      expect(screen.getByTestId('direction-title')).toHaveTextContent(
        'Afternoon Return: Ongar → Brentwood',
      );
    });

    fireEvent.click(screen.getByTestId('tab-historical-data'));
    await waitFor(() => {
      expect(screen.getByTestId('history-tab-view')).toBeInTheDocument();
    });

    expect(screen.getByTestId('history-ontime-pct')).toHaveTextContent('71.4%');
    expect(screen.getByTestId('history-late-pct')).toHaveTextContent('20%');
    expect(screen.getByTestId('history-cancelled-pct')).toHaveTextContent('8.6%');
    expect(screen.getByTestId('daily-history-row-2026-10-30')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('run-daily-batch-button'));
    await waitFor(() => {
      expect(dailyBatchSpy).toHaveBeenCalledTimes(1);
    });

    fireEvent.click(screen.getByTestId('run-week-backfill-button'));
    await waitFor(() => {
      expect(backfillSpy).toHaveBeenCalledTimes(1);
    });
  });
});
