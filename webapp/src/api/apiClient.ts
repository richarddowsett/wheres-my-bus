import {
  DeviceSubscription,
  Direction,
  HistoricalStatsResponse,
  HistoryBatchSummary,
  LiveBusStatus,
  RouteStop,
  WatchRequest,
  WatchSession,
} from '../types/bus';

export interface ApiClientConfig {
  baseUrl?: string;
  familyPin?: string;
  fetchImpl?: typeof fetch;
}

export class BusApiClient {
  private readonly baseUrl: string;
  private familyPin: string;
  private readonly fetchFn: typeof fetch;

  constructor(config: ApiClientConfig = {}) {
    this.baseUrl = (config.baseUrl ?? '').replace(/\/$/, '');
    this.familyPin = config.familyPin ?? '2121';
    this.fetchFn = config.fetchImpl ?? ((...args) => fetch(...args));
  }

  setFamilyPin(pin: string): void {
    this.familyPin = pin.trim();
  }

  getFamilyPin(): string {
    return this.familyPin;
  }

  async fetchStops(direction: Direction): Promise<RouteStop[]> {
    const url = `${this.baseUrl}/api/stops?direction=${encodeURIComponent(direction)}`;
    const response = await this.fetchFn(url, {
      method: 'GET',
      headers: this.buildHeaders(false),
    });
    if (!response.ok) {
      throw new Error(`Failed to fetch stops (${response.status})`);
    }
    return (await response.json()) as RouteStop[];
  }

  async fetchLiveStatus(
    direction: Direction,
    boardingStop?: string,
    alightingStop?: string,
  ): Promise<LiveBusStatus> {
    const params = new URLSearchParams({ direction });
    if (boardingStop) {
      params.set('boardingStop', boardingStop);
    }
    if (alightingStop) {
      params.set('alightingStop', alightingStop);
    }
    const url = `${this.baseUrl}/api/status?${params.toString()}`;
    const response = await this.fetchFn(url, {
      method: 'GET',
      headers: this.buildHeaders(false),
    });
    if (!response.ok) {
      throw new Error(`Failed to fetch live bus status (${response.status})`);
    }
    return (await response.json()) as LiveBusStatus;
  }

  async saveSubscription(subscription: DeviceSubscription): Promise<DeviceSubscription> {
    const url = `${this.baseUrl}/api/subscriptions`;
    const response = await this.fetchFn(url, {
      method: 'POST',
      headers: this.buildHeaders(true),
      body: JSON.stringify(subscription),
    });
    if (response.status === 401) {
      throw new Error('Unauthorized: Invalid Family PIN');
    }
    if (!response.ok) {
      throw new Error(`Failed to save subscription (${response.status})`);
    }
    return (await response.json()) as DeviceSubscription;
  }

  async startWatchSession(request: WatchRequest): Promise<WatchSession> {
    const url = `${this.baseUrl}/api/watch`;
    const response = await this.fetchFn(url, {
      method: 'POST',
      headers: this.buildHeaders(true),
      body: JSON.stringify(request),
    });
    if (response.status === 401) {
      throw new Error('Unauthorized: Invalid Family PIN');
    }
    if (!response.ok) {
      throw new Error(`Failed to start watch session (${response.status})`);
    }
    return (await response.json()) as WatchSession;
  }

  async fetchHistoricalStats(
    days = 180,
    direction?: Direction,
  ): Promise<HistoricalStatsResponse> {
    const params = new URLSearchParams({ days: String(days) });
    if (direction) {
      params.set('direction', direction);
    }
    const url = `${this.baseUrl}/api/history?${params.toString()}`;
    const response = await this.fetchFn(url, {
      method: 'GET',
      headers: this.buildHeaders(false),
    });
    if (!response.ok) {
      throw new Error(`Failed to fetch historical stats (${response.status})`);
    }
    return (await response.json()) as HistoricalStatsResponse;
  }

  async triggerDailyHistoryBatch(): Promise<HistoryBatchSummary> {
    const url = `${this.baseUrl}/api/internal/batch-daily-history`;
    const response = await this.fetchFn(url, {
      method: 'POST',
      headers: this.buildHeaders(false),
    });
    if (!response.ok) {
      throw new Error(`Failed to run daily history batch (${response.status})`);
    }
    return (await response.json()) as HistoryBatchSummary;
  }

  async triggerWeekBackfill(days = 180): Promise<HistoryBatchSummary> {
    const url = `${this.baseUrl}/api/internal/backfill-history?days=${days}`;
    const response = await this.fetchFn(url, {
      method: 'POST',
      headers: this.buildHeaders(false),
    });
    if (!response.ok) {
      throw new Error(`Failed to run historical backfill (${response.status})`);
    }
    return (await response.json()) as HistoryBatchSummary;
  }

  private buildHeaders(includeJsonContentType: boolean): Record<string, string> {
    const headers: Record<string, string> = {
      'X-Family-Pin': this.familyPin,
    };
    if (includeJsonContentType) {
      headers['Content-Type'] = 'application/json';
    }
    return headers;
  }
}
