import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { BusAlertStatus, DisruptionSituation } from '../types/bus';

export interface DisruptionBannerProps {
  status: BusAlertStatus;
  statusDetail: string;
  disruptions: DisruptionSituation[];
}

export const DisruptionBanner: React.FC<DisruptionBannerProps> = ({
  status,
  statusDetail,
  disruptions,
}) => {
  const showMissingWarning = status === 'MISSING_BUS_WARNING';
  const showCancelled = status === 'CANCELLED_OR_DISRUPTED';

  if (!showMissingWarning && !showCancelled && disruptions.length === 0) {
    return null;
  }

  return (
    <View
      testID="disruption-banner"
      style={[
        styles.container,
        showCancelled ? styles.cancelledBox : styles.warningBox,
      ]}
    >
      <Text style={styles.bannerTitle}>
        {showCancelled
          ? '🚨 Official Route 21 Service Disruption'
          : '⚠️ Live Bus Tracking Alert'}
      </Text>
      <Text style={styles.bannerDetail}>{statusDetail}</Text>
      {disruptions.map((item) => (
        <View key={item.situationNumber} style={styles.situationItem}>
          <Text style={styles.situationSummary}>
            {item.summary} ({item.condition.toUpperCase()})
          </Text>
          <Text style={styles.situationDescription}>{item.description}</Text>
        </View>
      ))}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    borderRadius: 12,
    padding: 14,
    marginVertical: 10,
    borderWidth: 1,
  },
  cancelledBox: {
    backgroundColor: '#FEF2F2',
    borderColor: '#EF4444',
  },
  warningBox: {
    backgroundColor: '#FFF7ED',
    borderColor: '#F97316',
  },
  bannerTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#7F1D1D',
    marginBottom: 4,
  },
  bannerDetail: {
    fontSize: 14,
    color: '#431407',
    marginBottom: 6,
  },
  situationItem: {
    marginTop: 6,
    paddingTop: 6,
    borderTopWidth: 1,
    borderTopColor: '#FECACA',
  },
  situationSummary: {
    fontSize: 14,
    fontWeight: '600',
    color: '#991B1B',
  },
  situationDescription: {
    fontSize: 13,
    color: '#7F1D1D',
    marginTop: 2,
  },
});
