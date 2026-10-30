import React, { useState } from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { DeviceSubscription, RouteStop } from '../types/bus';

export interface StopPickerModalProps {
  visible: boolean;
  stops: RouteStop[];
  initialBoardingAtco: string;
  initialAlightingAtco: string;
  initialFamilyPin: string;
  onClose: () => void;
  onSavePreferences: (
    boardingAtco: string,
    alightingAtco: string,
    familyPin: string,
    subscription: DeviceSubscription,
  ) => Promise<void>;
}

export const StopPickerModal: React.FC<StopPickerModalProps> = ({
  visible,
  stops,
  initialBoardingAtco,
  initialAlightingAtco,
  initialFamilyPin,
  onClose,
  onSavePreferences,
}) => {
  const [boardingAtco, setBoardingAtco] = useState(initialBoardingAtco);
  const [alightingAtco, setAlightingAtco] = useState(initialAlightingAtco);
  const [familyPin, setFamilyPin] = useState(initialFamilyPin);
  const [morningWindow, setMorningWindow] = useState('07:15 - 08:30');
  const [afternoonWindow, setAfternoonWindow] = useState('15:00 - 16:30');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (!visible) {
    return null;
  }

  const handleSave = async () => {
    setErrorMessage(null);
    const [mStart = '07:15', mEnd = '08:30'] = morningWindow
      .split('-')
      .map((s) => s.trim());
    const [aStart = '15:00', aEnd = '16:30'] = afternoonWindow
      .split('-')
      .map((s) => s.trim());

    const subscription: DeviceSubscription = {
      deviceId: 'family-web-mobile-device',
      pushToken: 'ExponentPushToken[route21-daughter-device]',
      childName: 'Daughter',
      morningBoardingAtco: boardingAtco,
      morningAlightingAtco: alightingAtco,
      afternoonBoardingAtco: alightingAtco,
      afternoonAlightingAtco: boardingAtco,
      morningWindowStart: mStart,
      morningWindowEnd: mEnd,
      afternoonWindowStart: aStart,
      afternoonWindowEnd: aEnd,
      enabled: true,
    };

    try {
      await onSavePreferences(boardingAtco, alightingAtco, familyPin, subscription);
      onClose();
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Could not save settings');
    }
  };

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View testID="stop-picker-modal" style={styles.backdrop}>
        <View style={styles.sheet}>
          <Text style={styles.title}>Commute & Notification Settings</Text>

          <ScrollView style={styles.scrollArea}>
            <Text style={styles.sectionLabel}>Family PIN (Required for Push Setup)</Text>
            <TextInput
              testID="family-pin-input"
              style={styles.input}
              value={familyPin}
              onChangeText={setFamilyPin}
              placeholder="Enter Family PIN (e.g. 2121)"
              secureTextEntry
            />

            <Text style={styles.sectionLabel}>Morning Commute Alert Window</Text>
            <TextInput
              testID="morning-window-input"
              style={styles.input}
              value={morningWindow}
              onChangeText={setMorningWindow}
            />

            <Text style={styles.sectionLabel}>Afternoon Return Alert Window</Text>
            <TextInput
              testID="afternoon-window-input"
              style={styles.input}
              value={afternoonWindow}
              onChangeText={setAfternoonWindow}
            />

            <Text style={styles.sectionLabel}>Select Boarding Stop (Route 21)</Text>
            <View style={styles.stopList}>
              {stops.map((stop) => {
                const selected = stop.atcoCode === boardingAtco;
                return (
                  <Pressable
                    key={`board-${stop.atcoCode}`}
                    testID={`select-boarding-${stop.atcoCode}`}
                    style={[styles.stopOption, selected && styles.selectedOption]}
                    onPress={() => setBoardingAtco(stop.atcoCode)}
                  >
                    <Text style={selected ? styles.selectedText : styles.optionText}>
                      {stop.commonName} ({stop.locality})
                    </Text>
                  </Pressable>
                );
              })}
            </View>

            <Text style={styles.sectionLabel}>Select School / Alighting Stop</Text>
            <View style={styles.stopList}>
              {stops.map((stop) => {
                const selected = stop.atcoCode === alightingAtco;
                return (
                  <Pressable
                    key={`alight-${stop.atcoCode}`}
                    testID={`select-alighting-${stop.atcoCode}`}
                    style={[styles.stopOption, selected && styles.selectedOption]}
                    onPress={() => setAlightingAtco(stop.atcoCode)}
                  >
                    <Text style={selected ? styles.selectedText : styles.optionText}>
                      {stop.commonName} ({stop.locality})
                    </Text>
                  </Pressable>
                );
              })}
            </View>

            {errorMessage && (
              <Text testID="settings-error-message" style={styles.errorText}>
                {errorMessage}
              </Text>
            )}
          </ScrollView>

          <View style={styles.footerButtons}>
            <Pressable
              testID="cancel-settings-button"
              style={styles.cancelButton}
              onPress={onClose}
            >
              <Text style={styles.cancelButtonText}>Cancel</Text>
            </Pressable>
            <Pressable
              testID="save-settings-button"
              style={styles.saveButton}
              onPress={handleSave}
            >
              <Text style={styles.saveButtonText}>Save & Enable Live Alerts</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(15, 23, 42, 0.6)',
    justifyContent: 'center',
    padding: 16,
  },
  sheet: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 20,
    maxHeight: '90%',
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
    color: '#0F172A',
    marginBottom: 12,
  },
  scrollArea: {
    marginBottom: 14,
  },
  sectionLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: '#334155',
    marginTop: 10,
    marginBottom: 6,
  },
  input: {
    borderWidth: 1,
    borderColor: '#CBD5E1',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 14,
    color: '#0F172A',
  },
  stopList: {
    gap: 6,
  },
  stopOption: {
    paddingVertical: 8,
    paddingHorizontal: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#E2E8F0',
  },
  selectedOption: {
    backgroundColor: '#DBEAFE',
    borderColor: '#2563EB',
  },
  optionText: {
    fontSize: 13,
    color: '#334155',
  },
  selectedText: {
    fontSize: 13,
    fontWeight: '700',
    color: '#1E40AF',
  },
  errorText: {
    color: '#DC2626',
    fontSize: 13,
    fontWeight: '600',
    marginTop: 8,
  },
  footerButtons: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 10,
  },
  cancelButton: {
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
    backgroundColor: '#F1F5F9',
  },
  cancelButtonText: {
    color: '#475569',
    fontWeight: '600',
  },
  saveButton: {
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
    backgroundColor: '#2563EB',
  },
  saveButtonText: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
});
