export type Direction = 'BRENTWOOD_TO_ONGAR' | 'ONGAR_TO_BRENTWOOD';

export type BusAlertStatus =
  | 'ON_TIME'
  | 'DELAYED'
  | 'MISSING_BUS_WARNING'
  | 'CANCELLED_OR_DISRUPTED';

export interface RouteStop {
  atcoCode: string;
  commonName: string;
  locality: string;
  latitude: number;
  longitude: number;
  sequenceOrder: number;
}

export interface DisruptionSituation {
  situationNumber: string;
  summary: string;
  description: string;
  validFromIso?: string | null;
  validToIso?: string | null;
  affectedOperatorRefs: string[];
  affectedLineRefs: string[];
  affectedStopAtcoCodes: string[];
  condition: string;
}

export interface UpcomingDeparture {
  journeyCode: string;
  scheduledTime: string;
  expectedTime: string;
  status: BusAlertStatus;
}

export interface LiveBusStatus {
  lineName: string;
  operatorCode: string;
  direction: Direction;
  boardingStop: RouteStop;
  alightingStop: RouteStop;
  activeJourneyCode: string | null;
  scheduledArrivalTime: string | null;
  expectedArrivalTime: string | null;
  etaMinutes: number | null;
  delayMinutes: number;
  status: BusAlertStatus;
  statusHeadline: string;
  statusDetail: string;
  nearestStopName: string | null;
  currentStopSequenceIndex: number | null;
  vehicleLatitude: number | null;
  vehicleLongitude: number | null;
  vehicleRef: string | null;
  activeDisruptions: DisruptionSituation[];
  upcomingDepartures: UpcomingDeparture[];
  evaluatedAtIso: string;
}

export interface DeviceSubscription {
  deviceId: string;
  pushToken: string;
  childName: string;
  morningBoardingAtco: string;
  morningAlightingAtco: string;
  afternoonBoardingAtco: string;
  afternoonAlightingAtco: string;
  morningWindowStart: string;
  morningWindowEnd: string;
  afternoonWindowStart: string;
  afternoonWindowEnd: string;
  enabled: boolean;
}

export interface WatchRequest {
  deviceId: string;
  pushToken: string;
  direction: Direction;
  boardingAtco: string;
  alightingAtco: string;
  durationMinutes?: number;
}

export interface WatchSession {
  id: string;
  deviceId: string;
  pushToken: string;
  direction: Direction;
  boardingAtco: string;
  alightingAtco: string;
  expiresAtEpochSeconds: number;
}

export interface HistoricalTripRecord {
  dateIso: string;
  journeyCode: string;
  direction: Direction;
  scheduledDeparture: string;
  delayMinutes: number;
  status: BusAlertStatus;
  summaryNote: string;
}

export interface DailyReliabilityRecord {
  dateIso: string;
  totalRuns: number;
  onTimeCount: number;
  lateCount: number;
  cancelledOrMissingCount: number;
  averageDelayMinutes: number;
}

export interface HistoricalStatsResponse {
  windowDays: number;
  directionFilter: Direction | null;
  totalScheduledRuns: number;
  onTimeCount: number;
  onTimePercentage: number;
  lateCount: number;
  latePercentage: number;
  cancelledOrMissingCount: number;
  cancelledOrMissingPercentage: number;
  averageDelayMinutes: number;
  dailyBreakdown: DailyReliabilityRecord[];
  recentTrips: HistoricalTripRecord[];
  lastBatchSyncIso: string;
}

export interface HistoryBatchSummary {
  mode: string;
  daysProcessed: number;
  recordsUpserted: number;
  completedAtIso: string;
}

