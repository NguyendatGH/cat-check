import { useCallback, useEffect, useRef, useState } from "react";

export type LiveCameraStatus = "idle" | "requesting" | "live" | "denied" | "unavailable";

const CONSTRAINTS: MediaStreamConstraints = {
  audio: false,
  video: {
    facingMode: { ideal: "environment" },
    width: { ideal: 1920 },
    height: { ideal: 1080 },
  },
};

function isSupported() {
  // mediaDevices is undefined outside secure contexts (plain http on a LAN IP).
  return typeof navigator !== "undefined" && "mediaDevices" in navigator && "getUserMedia" in navigator.mediaDevices;
}

/**
 * Live rear-camera preview. Asks for camera permission while `enabled`, stops the stream
 * otherwise, and grabs a full-resolution JPEG frame on `capture()`.
 */
export function useLiveCamera(enabled: boolean) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [status, setStatus] = useState<LiveCameraStatus>("idle");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    if (!isSupported()) {
      setStatus("unavailable");
      return;
    }
    let cancelled = false;
    const video = videoRef.current;
    setStatus("requesting");
    navigator.mediaDevices.getUserMedia(CONSTRAINTS).then(
      (stream) => {
        if (cancelled) {
          stream.getTracks().forEach((track) => {
            track.stop();
          });
          return;
        }
        streamRef.current = stream;
        if (video) {
          video.srcObject = stream;
          void video.play().catch(() => undefined);
        }
        setStatus("live");
      },
      (error: unknown) => {
        if (cancelled) return;
        const name = error instanceof DOMException ? error.name : "";
        setStatus(name === "NotAllowedError" || name === "SecurityError" ? "denied" : "unavailable");
      },
    );
    return () => {
      cancelled = true;
      streamRef.current?.getTracks().forEach((track) => {
        track.stop();
      });
      streamRef.current = null;
      if (video) video.srcObject = null;
      setStatus("idle");
    };
  }, [enabled, attempt]);

  const retry = useCallback(() => {
    setAttempt((n) => n + 1);
  }, []);

  const capture = useCallback(async (): Promise<File | null> => {
    const video = videoRef.current;
    if (!video || status !== "live" || video.videoWidth === 0) return null;
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const context = canvas.getContext("2d");
    if (!context) return null;
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob(resolve, "image/jpeg", 0.92);
    });
    if (!blob) return null;
    return new File([blob], `scan-${String(Date.now())}.jpg`, { type: "image/jpeg" });
  }, [status]);

  return { videoRef, status, capture, retry };
}
