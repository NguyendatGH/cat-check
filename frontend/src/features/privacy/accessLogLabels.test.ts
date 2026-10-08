import { describe, expect, it } from "vitest";
import vi from "@/shared/i18n/locales/vi/legal.json";
import { accessLogActionKey, accessLogActorKey, accessLogResultKey } from "./accessLogLabels";

const log = vi.privacyCenter.accessLog as unknown as {
  actions: Record<string, string>;
  actors: Record<string, string>;
  results: Record<string, string>;
};

describe("accessLogLabels", () => {
  it("map mã đã biết sang khoá có nhãn tiếng Việt", () => {
    const key = accessLogActionKey("ADMIN_USER_SCANS_VIEW");
    expect(key).toBe("privacyCenter.accessLog.actions.ADMIN_USER_SCANS_VIEW");
    expect(log.actions.ADMIN_USER_SCANS_VIEW).toBe("Xem lịch sử quét của bạn");
  });

  it("mã lạ rơi về nhãn chung, không lộ mã thô", () => {
    const key = accessLogActionKey("ADMIN_SOMETHING_NEW");
    expect(key).toBe("privacyCenter.accessLog.actions.generic");
    expect(log.actions.generic).toBe("Truy cập dữ liệu của bạn");
  });

  it("mọi khoá được trả về đều có chuỗi trong legal.json", () => {
    for (const a of Object.keys(log.actions)) expect(log.actions[a]).toBeTruthy();
    for (const k of ["USER", "ADMIN", "DPO", "SYSTEM", "JOB"]) expect(log.actors[k]).toBeTruthy();
    for (const k of ["SUCCESS", "DENIED", "ERROR"]) expect(log.results[k]).toBeTruthy();
    expect(accessLogActorKey("ADMIN")).toBe("privacyCenter.accessLog.actors.ADMIN");
    expect(accessLogResultKey("DENIED")).toBe("privacyCenter.accessLog.results.DENIED");
  });

  it("tác nhân/kết quả lạ trả null", () => {
    expect(accessLogActorKey("???")).toBeNull();
    expect(accessLogResultKey("???")).toBeNull();
  });
});
