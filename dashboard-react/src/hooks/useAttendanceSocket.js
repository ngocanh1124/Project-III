import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export function useAttendanceSocket(onMessage) {
  const clientRef = useRef(null);

  useEffect(() => {
    const rawBase = process.env.REACT_APP_API_URL || 'http://localhost:8080';
    const WS_BASE = rawBase.startsWith('http') ? new URL(rawBase).origin : rawBase;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${WS_BASE}/ws-attendance`),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/attendance', (msg) => {
          try {
            const log = JSON.parse(msg.body);
            onMessage(log);
          } catch (_) {}
        });
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, []); 
}

export function useAlertSocket(onAlert) {
  const clientRef = useRef(null);

  useEffect(() => {
    const rawBase = process.env.REACT_APP_API_URL || 'http://localhost:8080';
    const WS_BASE = rawBase.startsWith('http') ? new URL(rawBase).origin : rawBase;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${WS_BASE}/ws-attendance`),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/alert', (msg) => {
          try {
            const log = JSON.parse(msg.body);
            onAlert(log);
          } catch (_) {}
        });
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [onAlert]); 
}

export function useDeviceLogSocket(onLog, filterDeviceCode = null) {
  const clientRef = useRef(null);

  useEffect(() => {
    const rawBase = process.env.REACT_APP_API_URL || 'http://localhost:8080';
    const WS_BASE = rawBase.startsWith('http') ? new URL(rawBase).origin : rawBase;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${WS_BASE}/ws-attendance`),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/device-log', (msg) => {
          try {
            const entry = JSON.parse(msg.body);
            if (!filterDeviceCode || entry.deviceCode === filterDeviceCode) {
              onLog(entry);
            }
          } catch (_) {}
        });
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [onLog, filterDeviceCode]); 
}

export function usePendingAccessSocket(onPending) {
  const clientRef = useRef(null);

  useEffect(() => {
    const rawBase = process.env.REACT_APP_API_URL || 'http://localhost:8080';
    const WS_BASE = rawBase.startsWith('http') ? new URL(rawBase).origin : rawBase;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${WS_BASE}/ws-attendance`),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/pending-access', (msg) => {
          try {
            const log = JSON.parse(msg.body);
            onPending(log);
          } catch (_) {}
        });
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [onPending]); 
}
