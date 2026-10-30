import {
  calculateRouteProgressPercent,
  flipDirection,
  formatCountdownLabel,
  getDefaultDirection,
  getDirectionTitle,
  getPathnameForTab,
  getStatusTheme,
  getTabFromPathname,
} from '../../src/utils/commuteUtils';

describe('commuteUtils', () => {
  test('selects BRENTWOOD_TO_ONGAR before noon and ONGAR_TO_BRENTWOOD from noon onwards', () => {
    expect(getDefaultDirection(7)).toBe('BRENTWOOD_TO_ONGAR');
    expect(getDefaultDirection(11)).toBe('BRENTWOOD_TO_ONGAR');
    expect(getDefaultDirection(12)).toBe('ONGAR_TO_BRENTWOOD');
    expect(getDefaultDirection(15)).toBe('ONGAR_TO_BRENTWOOD');
  });

  test('flips direction and formats direction titles', () => {
    expect(flipDirection('BRENTWOOD_TO_ONGAR')).toBe('ONGAR_TO_BRENTWOOD');
    expect(flipDirection('ONGAR_TO_BRENTWOOD')).toBe('BRENTWOOD_TO_ONGAR');
    expect(getDirectionTitle('BRENTWOOD_TO_ONGAR')).toContain('Brentwood → Ongar');
    expect(getDirectionTitle('ONGAR_TO_BRENTWOOD')).toContain('Ongar → Brentwood');
  });

  test('formats countdown labels across all states and minute values', () => {
    expect(formatCountdownLabel(8, 'ON_TIME')).toBe('8 mins');
    expect(formatCountdownLabel(1, 'ON_TIME')).toBe('1 min');
    expect(formatCountdownLabel(0, 'ON_TIME')).toBe('Due now');
    expect(formatCountdownLabel(null, 'ON_TIME')).toBe('Scheduled');
    expect(formatCountdownLabel(5, 'MISSING_BUS_WARNING')).toBe('No GPS Signal');
    expect(formatCountdownLabel(5, 'CANCELLED_OR_DISRUPTED')).toBe('Cancelled / Disrupted');
  });

  test('returns appropriate status theme for each BusAlertStatus', () => {
    expect(getStatusTheme('ON_TIME').label).toBe('ON TIME');
    expect(getStatusTheme('DELAYED', 7).label).toBe('DELAYED +7m');
    expect(getStatusTheme('MISSING_BUS_WARNING').isCritical).toBe(true);
    expect(getStatusTheme('CANCELLED_OR_DISRUPTED').isCritical).toBe(true);
  });

  test('calculates route progress percentage accurately', () => {
    expect(calculateRouteProgressPercent(null, 12)).toBe(0);
    expect(calculateRouteProgressPercent(1, 1)).toBe(0);
    expect(calculateRouteProgressPercent(1, 11)).toBe(0);
    expect(calculateRouteProgressPercent(6, 11)).toBe(50);
    expect(calculateRouteProgressPercent(11, 11)).toBe(100);
  });

  test('maps URL pathnames to active tabs and back', () => {
    expect(getTabFromPathname('/')).toBe('LIVE');
    expect(getTabFromPathname('/live')).toBe('LIVE');
    expect(getTabFromPathname('/live/')).toBe('LIVE');
    expect(getTabFromPathname('/history')).toBe('HISTORY');
    expect(getTabFromPathname('/history/')).toBe('HISTORY');
    expect(getPathnameForTab('LIVE')).toBe('/live');
    expect(getPathnameForTab('HISTORY')).toBe('/history');
  });
});
