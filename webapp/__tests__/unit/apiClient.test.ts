import { BusApiClient } from '../../src/api/apiClient';
import { DeviceSubscription } from '../../src/types/bus';

describe('BusApiClient', () => {
  test('fetches stops and live status with query parameters', async () => {
    const mockFetch = jest.fn().mockImplementation(async (url: string) => {
      if (url.includes('/api/stops')) {
        return {
          ok: true,
          status: 200,
          json: async () => [{ atcoCode: '1500IM2459B', commonName: 'Brentwood Station' }],
        };
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({ status: 'ON_TIME', etaMinutes: 6 }),
      };
    });

    const client = new BusApiClient({
      baseUrl: 'https://bus.example.com/',
      familyPin: '2121',
      fetchImpl: mockFetch as unknown as typeof fetch,
    });

    expect(client.getFamilyPin()).toBe('2121');
    client.setFamilyPin(' 9999 ');
    expect(client.getFamilyPin()).toBe('9999');

    const stops = await client.fetchStops('BRENTWOOD_TO_ONGAR');
    expect(stops).toHaveLength(1);
    expect(mockFetch).toHaveBeenCalledWith(
      'https://bus.example.com/api/stops?direction=BRENTWOOD_TO_ONGAR',
      expect.objectContaining({
        method: 'GET',
        headers: { 'X-Family-Pin': '9999' },
      }),
    );

    const status = await client.fetchLiveStatus(
      'BRENTWOOD_TO_ONGAR',
      '1500IM2459B',
      '150032008001',
    );
    expect(status.etaMinutes).toBe(6);

    const history = await client.fetchHistoricalStats(7, 'BRENTWOOD_TO_ONGAR');
    expect(history).toBeDefined();
    await expect(client.triggerDailyHistoryBatch()).resolves.toBeDefined();
    await expect(client.triggerWeekBackfill(7)).resolves.toBeDefined();
  });

  test('saves subscription and starts watch session, handling 401 and 500 errors', async () => {
    let nextStatus = 201;
    const mockFetch = jest.fn().mockImplementation(async () => ({
      ok: nextStatus >= 200 && nextStatus < 300,
      status: nextStatus,
      json: async () => ({ id: 'watch-1', deviceId: 'dev-1' }),
    }));

    const client = new BusApiClient({
      baseUrl: 'https://bus.example.com',
      familyPin: '2121',
      fetchImpl: mockFetch as unknown as typeof fetch,
    });

    const sub: DeviceSubscription = {
      deviceId: 'dev-1',
      pushToken: 'token-1',
      childName: 'Daughter',
      morningBoardingAtco: '1500IM2459B',
      morningAlightingAtco: '150032008001',
      afternoonBoardingAtco: '150032008001',
      afternoonAlightingAtco: '1500IM2459B',
      morningWindowStart: '07:15',
      morningWindowEnd: '08:30',
      afternoonWindowStart: '15:00',
      afternoonWindowEnd: '16:30',
      enabled: true,
    };

    await expect(client.saveSubscription(sub)).resolves.toBeDefined();
    await expect(
      client.startWatchSession({
        deviceId: 'dev-1',
        pushToken: 'token-1',
        direction: 'BRENTWOOD_TO_ONGAR',
        boardingAtco: '1500IM2459B',
        alightingAtco: '150032008001',
      }),
    ).resolves.toBeDefined();

    nextStatus = 401;
    await expect(client.saveSubscription(sub)).rejects.toThrow('Unauthorized: Invalid Family PIN');
    await expect(
      client.startWatchSession({
        deviceId: 'dev-1',
        pushToken: 'token-1',
        direction: 'BRENTWOOD_TO_ONGAR',
        boardingAtco: '1500IM2459B',
        alightingAtco: '150032008001',
      }),
    ).rejects.toThrow('Unauthorized: Invalid Family PIN');

    nextStatus = 500;
    await expect(client.fetchStops('BRENTWOOD_TO_ONGAR')).rejects.toThrow('Failed to fetch stops (500)');
    await expect(client.fetchLiveStatus('BRENTWOOD_TO_ONGAR')).rejects.toThrow(
      'Failed to fetch live bus status (500)',
    );
    await expect(client.saveSubscription(sub)).rejects.toThrow('Failed to save subscription (500)');
    await expect(
      client.startWatchSession({
        deviceId: 'dev-1',
        pushToken: 'token-1',
        direction: 'BRENTWOOD_TO_ONGAR',
        boardingAtco: '1500IM2459B',
        alightingAtco: '150032008001',
      }),
    ).rejects.toThrow('Failed to start watch session (500)');
    await expect(client.fetchHistoricalStats(180)).rejects.toThrow(
      'Failed to fetch historical stats (500)',
    );
    await expect(client.triggerDailyHistoryBatch()).rejects.toThrow(
      'Failed to run daily history batch (500)',
    );
    await expect(client.triggerWeekBackfill(180)).rejects.toThrow(
      'Failed to run historical backfill (500)',
    );
  });
});
