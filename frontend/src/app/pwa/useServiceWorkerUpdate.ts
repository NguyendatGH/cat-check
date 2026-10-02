import { useCallback, useEffect, useRef, useState } from "react";
import { registerSW } from "virtual:pwa-register";

/**
 * registerType: "prompt" (vite.config.ts) — KHÔNG tự động reload, hook này báo cho UI biết
 * khi nào có bản cập nhật mới (`needRefresh`) để hiện toast/dialog "Có bản cập nhật, tải lại?".
 */
export function useServiceWorkerUpdate() {
  const [needRefresh, setNeedRefresh] = useState(false);
  const updateSWRef = useRef<((reloadPage?: boolean) => Promise<void>) | null>(null);

  useEffect(() => {
    updateSWRef.current = registerSW({
      immediate: true,
      onNeedRefresh() {
        setNeedRefresh(true);
      },
    });
  }, []);

  const applyUpdate = useCallback(() => {
    void updateSWRef.current?.(true);
  }, []);

  return { needRefresh, applyUpdate };
}
