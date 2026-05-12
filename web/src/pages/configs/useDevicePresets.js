import { useCallback, useEffect, useMemo, useState } from 'react';

const FALLBACK_DEVICE = {
  id: 'pixel-tablet',
  name: 'Pixel Tablet',
  class: 'tablet',
  widthDp: 1280,
  heightDp: 800,
  density: 2,
};

export default function useDevicePresets(initialId = 'pixel-tablet') {
  const [devices, setDevices] = useState([FALLBACK_DEVICE]);
  const [deviceId, setDeviceId] = useState(initialId);
  const [draft, setDraft] = useState(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    fetch('/api/v1/devices')
      .then(res => res.ok ? res.json() : Promise.reject(new Error(`${res.status}`)))
      .then(data => {
        const next = data.devices?.length ? data.devices : [FALLBACK_DEVICE];
        setDevices(next);
        setDeviceId(id => next.some(d => d.id === id) ? id : next[0].id);
        setLoaded(true);
      })
      .catch(() => {
        setDevices([FALLBACK_DEVICE]);
        setLoaded(true);
      });
  }, []);

  const selectedPreset = useMemo(
    () => devices.find(d => d.id === deviceId) ?? devices[0] ?? FALLBACK_DEVICE,
    [devices, deviceId],
  );

  const device = draft ?? selectedPreset;
  const isDirty = draft !== null;

  const selectDeviceId = useCallback((id) => {
    setDeviceId(id);
    setDraft(null);
  }, []);

  const updateDevice = useCallback((patch) => {
    setDraft(prev => ({ ...(prev ?? selectedPreset), ...patch }));
  }, [selectedPreset]);

  const saveDraft = useCallback(async (name) => {
    if (!draft) return null;
    const body = {
      name,
      class: draft.class,
      widthDp: Math.round(draft.widthDp),
      heightDp: Math.round(draft.heightDp),
      density: Number(draft.density),
    };
    const res = await fetch('/api/v1/devices', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error ?? `${res.status}`);
    setDevices(prev => [...prev, data].sort((a, b) => a.name.localeCompare(b.name)));
    setDeviceId(data.id);
    setDraft(null);
    return data;
  }, [draft]);

  return {
    devices,
    device,
    deviceId,
    setDeviceId: selectDeviceId,
    updateDevice,
    saveDraft,
    isDirty,
    loaded,
  };
}
