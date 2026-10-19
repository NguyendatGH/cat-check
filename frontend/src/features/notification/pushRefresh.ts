import { useEffect } from "react";
import { useUpsertPushSubscription } from "./hooks";
import {
  currentDeviceLabel,
  currentPlatform,
  pushAvailability,
  readLocalSubscriptionId,
  registerThisDevice,
  writeLocalSubscriptionId,
} from "./pushClient";

export function usePushSubscriptionRefresh(signedIn: boolean): void {
  const { mutateAsync } = useUpsertPushSubscription();

  useEffect(() => {
    if (!signedIn) return;
    const localId = readLocalSubscriptionId();
    if (localId === null) return;
    if (!pushAvailability().available || Notification.permission !== "granted") return;

    void (async () => {
      try {
        const fid = await registerThisDevice();
        const refreshed = await mutateAsync({
          fid,
          platform: currentPlatform(),
          deviceLabel: currentDeviceLabel(),
        });
        if (refreshed.id !== localId) writeLocalSubscriptionId(refreshed.id);
      } catch {
        // Làm mới nền: lỗi thì giữ đăng ký cũ, lần tải sau sẽ thử lại.
      }
    })();
  }, [signedIn, mutateAsync]);
}
