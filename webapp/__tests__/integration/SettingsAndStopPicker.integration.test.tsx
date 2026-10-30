import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { BusApiClient } from '../../src/api/apiClient';
import { DashboardScreen } from '../../src/screens/DashboardScreen';
import { LiveBusStatus, RouteStop } from '../../src/types/bus';

const stops: RouteStop[] = [
  {
    atcoCode: '1500IM2459B',
    commonName: 'Brentwood Railway Station',
    locality: 'Brentwood',
    latitude: 51.6136,
    longitude: 0.2995,
    sequenceOrder: 1,
  },
  {
    atcoCode: '150032009002',
    commonName: 'The Stag',
    locality: 'Ongar',
    latitude: 51.701,
    longitude: 0.2462,
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

const onTimeStatus: LiveBusStatus = {
  lineName: '21',
  operatorCode: 'NIBS',
  direction: 'BRENTWOOD_TO_ONGAR',
  boardingStop: stops[0],
  alightingStop: stops[2],
  activeJourneyCode: 'NIBS-21-OUT-1',
  scheduledArrivalTime: '07:40',
  expectedArrivalTime: '07:40',
  etaMinutes: 5,
  delayMinutes: 0,
  status: 'ON_TIME',
  statusHeadline: 'Bus 21 to Ongar: 5 mins away (On Time)',
  statusDetail: 'Due 07:40 at Brentwood Railway Station',
  nearestStopName: 'Brentwood Railway Station',
  currentStopSequenceIndex: 1,
  vehicleLatitude: 51.6136,
  vehicleLongitude: 0.2995,
  vehicleRef: 'NIBS-100',
  activeDisruptions: [],
  upcomingDepartures: [],
  evaluatedAtIso: '2026-10-30T07:35:00Z',
};

describe('SettingsAndStopPicker Integration', () => {
  test('opens stop picker modal, selects new stops, updates PIN, and saves subscription', async () => {
    const mockClient = new BusApiClient({ familyPin: '2121' });
    jest.spyOn(mockClient, 'fetchStops').mockResolvedValue(stops);
    jest.spyOn(mockClient, 'fetchLiveStatus').mockResolvedValue(onTimeStatus);
    const saveSubSpy = jest.spyOn(mockClient, 'saveSubscription').mockImplementation(async (sub) => sub);

    render(<DashboardScreen apiClient={mockClient} initialHour={8} />);

    await waitFor(() => {
      expect(screen.getByTestId('open-settings-button')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('open-settings-button'));

    await waitFor(() => {
      expect(screen.getByTestId('stop-picker-modal')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByTestId('family-pin-input'), {
      target: { value: '4545' },
    });
    fireEvent.click(screen.getByTestId('select-alighting-150032009002'));
    fireEvent.click(screen.getByTestId('save-settings-button'));

    await waitFor(() => {
      expect(saveSubSpy).toHaveBeenCalledTimes(1);
      expect(mockClient.getFamilyPin()).toBe('4545');
      expect(screen.getByTestId('subscription-notice')).toHaveTextContent(
        'Automatic school-day live notifications saved and enabled!',
      );
    });
  });
});
