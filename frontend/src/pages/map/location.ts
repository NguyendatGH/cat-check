export interface UserLocation {
  latitude: number;
  longitude: number;
  accuracy: number;
}

export function validCoordinates(latitude: unknown, longitude: unknown): boolean {
  return (
    typeof latitude === "number" &&
    Number.isFinite(latitude) &&
    Math.abs(latitude) <= 90 &&
    typeof longitude === "number" &&
    Number.isFinite(longitude) &&
    Math.abs(longitude) <= 180
  );
}

/** One bounded request. Only commit the best fresh fix when acquisition finishes. */
export function acquireLocation(
  geolocation: Geolocation,
  onSuccess: (location: UserLocation) => void,
  onFailure: (status: "denied" | "unavailable") => void,
): () => void {
  let active = true;
  let watchId: number | undefined;
  let best: UserLocation | null = null;
  const startedAt = Date.now();
  const cancel = () => {
    active = false;
    clearTimeout(timer);
    if (watchId !== undefined) geolocation.clearWatch(watchId);
  };
  const finish = () => {
    if (!active) return;
    cancel();
    if (best) onSuccess(best);
    else onFailure("unavailable");
  };
  const timer = setTimeout(finish, 20_000);
  try {
    watchId = geolocation.watchPosition(
      (position) => {
        // Providers may timestamp a fresh fix just before the watch was registered.
        if (!active || !Number.isFinite(position.timestamp) || position.timestamp < startedAt - 5_000) return;
        const { latitude, longitude, accuracy } = position.coords;
        if (!validCoordinates(latitude, longitude) || !Number.isFinite(accuracy) || accuracy <= 0) return;
        if (!best || accuracy <= best.accuracy) best = { latitude, longitude, accuracy };
        if (accuracy <= 100) finish();
      },
      (error) => {
        if (!active) return;
        if (error.code === 1) {
          cancel();
          onFailure("denied");
        }
        // Temporary errors need not terminate the watch; it may still obtain a fix.
      },
      { enableHighAccuracy: true, maximumAge: 0, timeout: 20_000 },
    );
  } catch {
    cancel();
    onFailure("unavailable");
  }
  return cancel;
}

export function accuracyBounds(location: UserLocation): [[number, number], [number, number]] {
  const latDelta = location.accuracy / 111_320;
  const lngDelta = latDelta / Math.max(0.01, Math.cos((location.latitude * Math.PI) / 180));
  return [
    [location.longitude - lngDelta, Math.max(-85, location.latitude - latDelta)],
    [location.longitude + lngDelta, Math.min(85, location.latitude + latDelta)],
  ];
}
