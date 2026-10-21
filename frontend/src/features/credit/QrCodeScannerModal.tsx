import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Image as ImageIcon, QrCode, RefreshCw, X } from "lucide-react";
import jsQR from "jsqr";
import { Button } from "@/shared/ui";
import { formatActivationCode, isValidActivationCode } from "./schemas";

interface QrCodeScannerModalProps {
  isOpen: boolean;
  onClose: () => void;
  onScan: (code: string) => void;
}

function extractActivationCode(rawText: string): string | null {
  const trimmed = rawText.trim();
  // Check if it is a CatCheck URL: e.g. https://catcheck.vn/credits/activate?code=CC-...
  try {
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      const url = new URL(trimmed);
      const codeParam = url.searchParams.get("code");
      if (codeParam && isValidActivationCode(codeParam)) {
        return formatActivationCode(codeParam);
      }
    }
  } catch {
    // Ignore URL parse error
  }

  // Look for pattern CC-[A-Z0-9]+-[A-Z0-9]+
  const match = /CC-[A-Z0-9_]+-[A-Z0-9]{10}/i.exec(trimmed);
  if (match && isValidActivationCode(match[0])) {
    return formatActivationCode(match[0]);
  }

  if (isValidActivationCode(trimmed)) {
    return formatActivationCode(trimmed);
  }

  return null;
}

export function QrCodeScannerModal({ isOpen, onClose, onScan }: QrCodeScannerModalProps) {
  const { t } = useTranslation("credit");
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const animFrameRef = useRef<number | null>(null);

  const [hasCamera, setHasCamera] = useState<boolean | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [facingMode, setFacingMode] = useState<"environment" | "user">("environment");
  const [isScanning, setIsScanning] = useState(true);

  const handleDetected = useCallback(
    (scannedText: string) => {
      const code = extractActivationCode(scannedText);
      if (code) {
        if (typeof navigator !== "undefined" && "vibrate" in navigator) {
          try {
            navigator.vibrate(100);
          } catch {
            // Ignore vibrate failures
          }
        }
        setIsScanning(false);
        onScan(code);
        onClose();
      } else {
        setErrorMessage(`Mã QR không đúng định dạng CatCheck: "${scannedText.slice(0, 30)}"`);
      }
    },
    [onScan, onClose],
  );

  const scanFrame = useCallback(() => {
    const video = videoRef.current;
    if (!video || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
      animFrameRef.current = requestAnimationFrame(scanFrame);
      return;
    }

    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    if (!ctx) {
      animFrameRef.current = requestAnimationFrame(scanFrame);
      return;
    }

    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const code = jsQR(imageData.data, imageData.width, imageData.height, {
      inversionAttempts: "dontInvert",
    });

    if (code?.data) {
      handleDetected(code.data);
      return;
    }

    animFrameRef.current = requestAnimationFrame(scanFrame);
  }, [handleDetected]);

  useEffect(() => {
    if (!isOpen) {
      if (animFrameRef.current) {
        cancelAnimationFrame(animFrameRef.current);
        animFrameRef.current = null;
      }
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => {
          track.stop();
        });
        streamRef.current = null;
      }
      return;
    }

    setIsScanning(true);
    setErrorMessage(null);

    let isMounted = true;
    navigator.mediaDevices
      .getUserMedia({
        video: { facingMode: { ideal: facingMode }, width: { ideal: 1280 }, height: { ideal: 720 } },
        audio: false,
      })
      .then((stream) => {
        if (!isMounted) {
          stream.getTracks().forEach((t) => {
            t.stop();
          });
          return;
        }
        streamRef.current = stream;
        setHasCamera(true);
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          void videoRef.current.play().then(() => {
            if (isMounted) {
              animFrameRef.current = requestAnimationFrame(scanFrame);
            }
          });
        }
      })
      .catch((err: unknown) => {
        if (!isMounted) return;
        setHasCamera(false);
        const errName = err instanceof DOMException ? err.name : "";
        if (errName === "NotAllowedError" || errName === "PermissionDeniedError") {
          setErrorMessage("Quyền camera bị từ chối. Hãy cho phép camera hoặc tải ảnh mã QR từ thiết bị.");
        } else {
          setErrorMessage("Không thể kết nối camera. Bạn có thể chọn ảnh mã QR từ thiết bị.");
        }
      });

    return () => {
      isMounted = false;
      if (animFrameRef.current) {
        cancelAnimationFrame(animFrameRef.current);
        animFrameRef.current = null;
      }
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => {
          track.stop();
        });
        streamRef.current = null;
      }
    };
  }, [isOpen, facingMode, scanFrame]);

  const handleFileUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const img = new Image();
    const reader = new FileReader();

    reader.onload = (e) => {
      img.onload = () => {
        const canvas = document.createElement("canvas");
        canvas.width = img.width;
        canvas.height = img.height;
        const ctx = canvas.getContext("2d", { willReadFrequently: true });
        if (!ctx) return;
        ctx.drawImage(img, 0, 0);
        const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
        const decoded = jsQR(imageData.data, imageData.width, imageData.height);
        if (decoded?.data) {
          handleDetected(decoded.data);
        } else {
          setErrorMessage("Không tìm thấy mã QR hợp lệ trong ảnh vừa chọn. Vui lòng thử lại với ảnh rõ hơn.");
        }
      };
      img.src = e.target?.result as string;
    };
    reader.readAsDataURL(file);
    // Reset file input so same file can be re-selected if needed
    event.target.value = "";
  };

  const toggleCamera = () => {
    setFacingMode((prev) => (prev === "environment" ? "user" : "environment"));
  };

  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="qr-scanner-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-4 backdrop-blur-xs animate-fade-in"
    >
      <div className="relative flex w-full max-w-md flex-col overflow-hidden rounded-3xl bg-surface shadow-brand-lg">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border/50 px-5 py-4">
          <div className="flex items-center gap-2.5">
            <span className="flex size-9 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <QrCode className="size-5" aria-hidden="true" />
            </span>
            <div>
              <h2 id="qr-scanner-title" className="text-body font-bold text-text-primary">
                {t("activate.qr.title")}
              </h2>
              <p className="text-[11px] text-text-secondary">{t("activate.qr.subtitle")}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng quét QR"
            className="flex size-8 items-center justify-center rounded-full text-text-tertiary transition-colors hover:bg-background-alt hover:text-text-primary active:scale-95"
          >
            <X className="size-5" aria-hidden="true" />
          </button>
        </div>

        {/* Viewfinder area */}
        <div className="relative aspect-square w-full overflow-hidden bg-black">
          {hasCamera !== false && (
            <video
              ref={videoRef}
              playsInline
              muted
              className="size-full object-cover"
              aria-label="Xem trước camera"
            />
          )}

          {/* Scanner Overlay Frame */}
          {hasCamera !== false && isScanning && (
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <div className="relative size-60 rounded-2xl border-2 border-primary/40">
                {/* 4 Glowing Corner Accents */}
                <span className="absolute -top-1 -left-1 size-5 rounded-tl-lg border-t-4 border-l-4 border-primary" />
                <span className="absolute -top-1 -right-1 size-5 rounded-tr-lg border-t-4 border-r-4 border-primary" />
                <span className="absolute -bottom-1 -left-1 size-5 rounded-bl-lg border-b-4 border-l-4 border-primary" />
                <span className="absolute -bottom-1 -right-1 size-5 rounded-br-lg border-b-4 border-r-4 border-primary" />

                {/* Laser scan animation line */}
                <div
                  className="absolute inset-x-0 h-1 bg-gradient-to-r from-transparent via-primary to-transparent shadow-brand-sm animate-pulse"
                  style={{
                    animation: "qrScanMove 2.2s ease-in-out infinite alternate",
                  }}
                />
              </div>
            </div>
          )}

          {/* Switch camera button */}
          {hasCamera && (
            <button
              type="button"
              onClick={toggleCamera}
              aria-label="Đổi camera"
              className="absolute top-3 right-3 flex size-9 items-center justify-center rounded-full bg-black/60 text-white backdrop-blur-xs transition-colors hover:bg-black/80 active:scale-90"
            >
              <RefreshCw className="size-4" aria-hidden="true" />
            </button>
          )}

          {/* Instructions banner */}
          <div className="absolute inset-x-0 bottom-3 text-center">
            <span className="inline-block rounded-full bg-black/70 px-3.5 py-1 text-[11px] font-medium text-white/90 backdrop-blur-xs">
              {t("activate.qr.guide")}
            </span>
          </div>
        </div>

        {/* Error notification if any */}
        {errorMessage && (
          <div className="border-t border-danger/20 bg-danger-bg px-4 py-2.5 text-caption font-medium text-danger-text">
            {errorMessage}
          </div>
        )}

        {/* Bottom controls */}
        <div className="flex flex-col gap-3 p-4 bg-background-alt/50">
          <div className="flex items-center gap-2">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleFileUpload}
            />
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="flex-1 font-semibold"
              leftIcon={<ImageIcon className="size-4" aria-hidden="true" />}
              onClick={() => {
                fileInputRef.current?.click();
              }}
            >
              {t("activate.qr.chooseImage")}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
