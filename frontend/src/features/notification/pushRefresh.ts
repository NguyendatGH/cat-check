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

/**
 * Làm mới đăng ký push của máy này mỗi lần mở app: gọi lại `PUT /push/subscriptions` để
 * `last_seen_at` được cập nhật. Backend xoá đăng ký sau 180 ngày không được làm mới, và
 * `last_seen_at` CHỈ đổi ở endpoint này — không có bước này thì người dùng bật push một lần
 * sẽ âm thầm mất thông báo sau 6 tháng dù vẫn dùng app hằng ngày.
 *
 * Chỉ chạy khi: đã đăng nhập, máy này TỪNG bật push bằng công tắc (có id đăng ký cục bộ — nếu
 * không, quyền thông báo đã cấp cho việc khác không được tự biến thành đăng ký), và quyền đang
 * là `granted` (không bao giờ hiện hộp thoại xin quyền lúc mở app, p12 §12.3.5). Mọi lỗi đều
 * im lặng: đây là việc nền, người dùng không có gì để làm với nó; trạng thái thật vẫn xem được
 * ở `/settings/notifications`.
 */
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
        // Im lặng có chủ đích — xem Javadoc.
      }
    })();
  }, [signedIn, mutateAsync]);
}
