import React, { useMemo } from 'react';
import { BusApiClient } from './src/api/apiClient';
import { DashboardScreen } from './src/screens/DashboardScreen';

function resolveDefaultBaseUrl(): string {
  if (process.env.EXPO_PUBLIC_API_URL !== undefined) {
    return process.env.EXPO_PUBLIC_API_URL;
  }
  if (
    typeof window !== 'undefined' &&
    window.location.hostname !== 'localhost' &&
    window.location.hostname !== '127.0.0.1'
  ) {
    return '';
  }
  return 'http://localhost:8080';
}

export default function App() {
  const apiClient = useMemo(
    () => new BusApiClient({ baseUrl: resolveDefaultBaseUrl() }),
    [],
  );
  return <DashboardScreen apiClient={apiClient} />;
}
