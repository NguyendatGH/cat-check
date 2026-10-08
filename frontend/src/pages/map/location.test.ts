import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { acquireLocation, accuracyBounds, validCoordinates } from "./location";

describe("map location acquisition", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function setup() {
    let success!: PositionCallback;
    let failure!: PositionErrorCallback;
    const clearWatch = vi.fn();
    const geo = {
      watchPosition: vi.fn((onSuccess: PositionCallback, onFailure: PositionErrorCallback) => {
        success = onSuccess;
        failure = onFailure;
        return 42;
      }),
      clearWatch,
      getCurrentPosition: vi.fn(),
    } as unknown as Geolocation;
    const ready = vi.fn();
    const error = vi.fn();
    const cancel = acquireLocation(geo, ready, error);
    const emit = (accuracy: number, latitude = 10.8, timestamp = Date.now()) => {
      success({
        coords: { latitude, longitude: 106.7, accuracy },
        timestamp,
      } as GeolocationPosition);
    };
    return {
      clearWatch,
      ready,
      error,
      cancel,
      emit,
      fail: (code: number) => {
        failure({ code } as GeolocationPositionError);
      },
    };
  }

  it("waits for an improved fix instead of committing a 6km coarse position", () => {
    const s = setup();
    s.emit(6000);
    expect(s.ready).not.toHaveBeenCalled();
    s.emit(25, 10.85);
    expect(s.ready).toHaveBeenCalledWith({ latitude: 10.85, longitude: 106.7, accuracy: 25 });
    expect(s.clearWatch).toHaveBeenCalledWith(42);
    s.emit(10, 11);
    expect(s.ready).toHaveBeenCalledTimes(1);
  });

  it("finishes with the best available estimate at the deadline", () => {
    const s = setup();
    s.emit(6000);
    s.emit(1500);
    s.emit(5000);
    vi.advanceTimersByTime(20_000);
    expect(s.ready).toHaveBeenCalledWith(expect.objectContaining({ accuracy: 1500 }));
  });

  it("ignores stale fixes and invalid coordinates", () => {
    const s = setup();
    s.emit(10, 10, Date.now() - 60_000);
    s.emit(10, 100);
    s.emit(-1);
    vi.advanceTimersByTime(20_000);
    expect(s.ready).not.toHaveBeenCalled();
    expect(s.error).toHaveBeenCalledWith("unavailable");
  });

  it("allows a temporary failure to recover", () => {
    const s = setup();
    s.fail(2);
    s.emit(30);
    expect(s.ready).toHaveBeenCalledOnce();
    expect(s.error).not.toHaveBeenCalled();
  });

  it("does not update state after cancellation or permission denial", () => {
    const s = setup();
    s.cancel();
    s.emit(10);
    s.fail(1);
    vi.advanceTimersByTime(20_000);
    expect(s.ready).not.toHaveBeenCalled();
    expect(s.error).not.toHaveBeenCalled();
    const denied = setup();
    denied.fail(1);
    denied.emit(10);
    expect(denied.error).toHaveBeenCalledWith("denied");
    expect(denied.ready).not.toHaveBeenCalled();
  });

  it("keeps longitude and latitude in map order and excludes missing marker coordinates", () => {
    expect(validCoordinates(undefined, 106)).toBe(false);
    expect(validCoordinates(null, null)).toBe(false);
    const bounds = accuracyBounds({ latitude: 10, longitude: 106, accuracy: 6000 });
    expect(bounds[0][0]).toBeLessThan(106);
    expect(bounds[1][0]).toBeGreaterThan(106);
    expect(bounds[0][1]).toBeLessThan(10);
    expect(bounds[1][1]).toBeGreaterThan(10);
  });
});
