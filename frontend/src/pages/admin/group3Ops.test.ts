import { describe, expect, it } from "vitest";
import { ROLES, hasAnyRole } from "@/shared/auth/roles";
import {
  allowedBroadcastTemplates,
  buildBroadcastRequest,
  canResendOutbox,
  isJobRunValid,
  validateBroadcastForm,
  type Group3Capability,
} from "./group3Ops";

const canFor =
  (roles: string[]) =>
  (key: Group3Capability): boolean =>
    hasAnyRole(roles, ROLES[key]);

const form = { templateCode: "SYSTEM_MAINTENANCE", title: "Bảo trì", body: "Nội dung", reason: "Bảo trì định kỳ" };

describe("quyền nhóm vận hành (ROLES)", () => {
  it("chạy job tay chỉ ADMIN_SUPER", () => {
    expect(canFor(["ADMIN_SUPER"])("jobRun")).toBe(true);
    expect(canFor(["DPO"])("jobRun")).toBe(false);
  });
});

describe("allowedBroadcastTemplates", () => {
  it("ADMIN_SUPER được cả hai mẫu", () => {
    expect(allowedBroadcastTemplates(canFor(["ADMIN_SUPER"]))).toEqual([
      "SYSTEM_MAINTENANCE",
      "PRIVACY_INCIDENT_NOTICE",
    ]);
  });
  it("DPO chỉ được mẫu sự cố", () => {
    expect(allowedBroadcastTemplates(canFor(["DPO"]))).toEqual(["PRIVACY_INCIDENT_NOTICE"]);
  });
  it("vai trò khác không được mẫu nào", () => {
    expect(allowedBroadcastTemplates(canFor(["ADMIN_SUPPORT"]))).toEqual([]);
  });
});

describe("buildBroadcastRequest", () => {
  it("dry run không cần xác nhận và gửi confirmed=false", () => {
    expect(buildBroadcastRequest(form, "dry", false)).toMatchObject({ dryRun: true, confirmed: false });
  });
  it("phát thật chưa xác nhận bị chặn", () => {
    expect(buildBroadcastRequest(form, "send", false)).toBeNull();
  });
  it("phát thật đã xác nhận gửi confirmed=true, dryRun=false", () => {
    expect(buildBroadcastRequest(form, "send", true)).toMatchObject({ dryRun: false, confirmed: true });
  });
  it("form sai thì không dựng request", () => {
    expect(buildBroadcastRequest({ ...form, reason: "ngắn" }, "dry", false)).toBeNull();
  });
});

describe("validateBroadcastForm", () => {
  it("báo đủ lỗi", () => {
    expect(validateBroadcastForm({ templateCode: "X", title: " ", body: "", reason: "" })).toEqual([
      "template",
      "title",
      "body",
      "reason",
    ]);
  });
  it("hợp lệ", () => {
    expect(validateBroadcastForm(form)).toEqual([]);
  });
});

describe("canResendOutbox / isJobRunValid", () => {
  it("chỉ ADMIN_SUPER gửi lại bản ghi FAILED", () => {
    expect(canResendOutbox(canFor(["ADMIN_SUPER"]), "FAILED")).toBe(true);
    expect(canResendOutbox(canFor(["ADMIN_SUPER"]), "SENT")).toBe(false);
    expect(canResendOutbox(canFor(["DPO"]), "FAILED")).toBe(false);
  });
  it("chạy job cần tên và lý do đủ dài", () => {
    expect(isJobRunValid("retention", "chạy kiểm tra tay")).toBe(true);
    expect(isJobRunValid(" ", "chạy kiểm tra tay")).toBe(false);
    expect(isJobRunValid("retention", "ngắn")).toBe(false);
  });
});
