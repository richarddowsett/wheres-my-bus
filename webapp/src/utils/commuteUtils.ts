import { BusAlertStatus, Direction } from '../types/bus';

export interface StatusTheme {
  label: string;
  badgeBackground: string;
  badgeText: string;
  cardBorder: string;
  icon: string;
  isCritical: boolean;
}

export function getDefaultDirection(currentHour: number): Direction {
  return currentHour < 12 ? 'BRENTWOOD_TO_ONGAR' : 'ONGAR_TO_BRENTWOOD';
}

export function flipDirection(direction: Direction): Direction {
  return direction === 'BRENTWOOD_TO_ONGAR' ? 'ONGAR_TO_BRENTWOOD' : 'BRENTWOOD_TO_ONGAR';
}

export function getDirectionTitle(direction: Direction): string {
  return direction === 'BRENTWOOD_TO_ONGAR'
    ? 'Morning School Run: Brentwood → Ongar'
    : 'Afternoon Return: Ongar → Brentwood';
}

export function formatCountdownLabel(etaMinutes: number | null, status: BusAlertStatus): string {
  if (status === 'CANCELLED_OR_DISRUPTED') {
    return 'Cancelled / Disrupted';
  }
  if (status === 'MISSING_BUS_WARNING') {
    return 'No GPS Signal';
  }
  if (etaMinutes === null) {
    return 'Scheduled';
  }
  if (etaMinutes <= 0) {
    return 'Due now';
  }
  if (etaMinutes === 1) {
    return '1 min';
  }
  return `${etaMinutes} mins`;
}

export function getStatusTheme(status: BusAlertStatus, delayMinutes = 0): StatusTheme {
  switch (status) {
    case 'CANCELLED_OR_DISRUPTED':
      return {
        label: 'DISRUPTED / CANCELLED',
        badgeBackground: '#FEE2E2',
        badgeText: '#991B1B',
        cardBorder: '#EF4444',
        icon: '🚨',
        isCritical: true,
      };
    case 'MISSING_BUS_WARNING':
      return {
        label: 'NO LIVE BUS SIGNAL',
        badgeBackground: '#FFEDD5',
        badgeText: '#9A3412',
        cardBorder: '#F97316',
        icon: '⚠️',
        isCritical: true,
      };
    case 'DELAYED':
      return {
        label: `DELAYED +${delayMinutes}m`,
        badgeBackground: '#FEF3C7',
        badgeText: '#92400E',
        cardBorder: '#F59E0B',
        icon: '⏰',
        isCritical: false,
      };
    case 'ON_TIME':
    default:
      return {
        label: 'ON TIME',
        badgeBackground: '#DCFCE7',
        badgeText: '#166534',
        cardBorder: '#22C55E',
        icon: '🟢',
        isCritical: false,
      };
  }
}

export function calculateRouteProgressPercent(
  currentSequenceOrder: number | null,
  totalStops: number,
): number {
  if (!currentSequenceOrder || totalStops <= 1) {
    return 0;
  }
  const clamped = Math.max(1, Math.min(totalStops, currentSequenceOrder));
  return Math.round(((clamped - 1) / (totalStops - 1)) * 100);
}
