import { describe, expect, it } from "vitest";
import {
  buildIncidentPatch,
  buildOpenIncidentPayload,
  buildRulePatch,
  draftToParams,
  emptyIncidentForm,
  emptyPolicyDraft,
  fromLocalInputValue,
  incidentToForm,
  isConfirmPhraseMatch,
  isUuid,
  latestConsentByPurpose,
  paramsToDraft,
  parseDataCodes,
  planReassign,
  planRuleToggle,
  policyDraftToPayload,
  reassignBody,
  replaceRule,
  ruleToDraft,
  stableStringify,
  toLocalInputValue,
  validatePolicyDraft,
  type PolicyDraftForm,
} from "./governanceForms";
import type { AdminConsentRecord, AdminMonitoringRule, AdminScanItem, AdminSecurityIncident } from "./governanceTypes";

const REASON = "Điều chỉnh theo yêu cầu vận hành";

function rule(overrides: Partial<AdminMonitoringRule> = {}): AdminMonitoringRule {
  return {
    code: "REPEATED_OUT_OF_RANGE",
    name: "Lặp lại ngoài khoảng",
    enabled: true,
    params: { windowHours: 48, minCount: 2, sameDirection: true, minConfidence: 0.55 },
    severity: "ATTENTION",
    cooldownHours: 72,
    requiresCalibratedChart: true,
    messageKey: "insight.rule.repeated",
    pushEnabled: false,
    sortOrder: 1,
    updatedAt: "2026-10-01T00:00:00Z",
    etag: 'W/"1727740800000-REPEATED_OUT_OF_RANGE"',
    ...overrides,
  };
}

describe("isUuid", () => {
  it("chấp nhận UUID (có khoảng trắng hai đầu), từ chối chuỗi khác", () => {
    expect(isUuid(" 3f2c1a9e-8b7d-4c6e-9f10-1a2b3c4d5e6f ")).toBe(true);
    expect(isUuid("3f2c1a9e-8b7d-4c6e-9f10")).toBe(false);
    expect(isUuid("")).toBe(false);
  });
});

describe("datetime-local", () => {
  it("chuyển qua lại giữ nguyên tới phút, không phụ thuộc múi giờ máy", () => {
    const iso = "2026-10-11T08:45:00.000Z";
    const local = toLocalInputValue(iso);
    expect(local).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(fromLocalInputValue(local)).toBe(iso);
  });

  it("rỗng/sai thì trả rỗng/null", () => {
    expect(toLocalInputValue(null)).toBe("");
    expect(toLocalInputValue("không phải ngày")).toBe("");
    expect(fromLocalInputValue("  ")).toBeNull();
    expect(fromLocalInputValue("abc")).toBeNull();
  });
});

describe("tham số rule", () => {
  it("giữ kiểu gốc của từng khoá khi đọc lại", () => {
    const drafts = paramsToDraft({ n: 0.55, b: true, s: "MEDIAN", o: { a: 1 } });
    expect(drafts.map((draft) => draft.kind)).toEqual(["number", "boolean", "string", "json"]);
    expect(draftToParams(drafts)).toEqual({ ok: true, params: { n: 0.55, b: true, s: "MEDIAN", o: { a: 1 } } });
  });

  it("chấp nhận dấu phẩy thập phân; báo khoá nhập sai", () => {
    expect(draftToParams([{ key: "n", kind: "number", value: "6,45" }])).toEqual({ ok: true, params: { n: 6.45 } });
    expect(
      draftToParams([
        { key: "n", kind: "number", value: "abc" },
        { key: "b", kind: "boolean", value: "có" },
        { key: "o", kind: "json", value: "{" },
      ]),
    ).toEqual({ ok: false, invalidKeys: ["n", "b", "o"] });
  });

  it("stableStringify không phụ thuộc thứ tự khoá", () => {
    expect(stableStringify({ b: 1, a: { d: 2, c: [1, { y: 1, x: 2 }] } })).toBe(
      stableStringify({ a: { c: [1, { x: 2, y: 1 }], d: 2 }, b: 1 }),
    );
    expect(stableStringify(undefined)).toBe("null");
  });
});

describe("buildRulePatch (L39 merge-patch)", () => {
  it("chỉ gửi trường đã đổi, kèm reason đã trim", () => {
    const base = rule();
    const draft = { ...ruleToDraft(base), enabled: false, cooldownHours: "48" };
    expect(buildRulePatch(base, draft, `  ${REASON}  `)).toEqual({
      ok: true,
      payload: { enabled: false, cooldownHours: 48, reason: REASON },
    });
  });

  it("đổi một tham số thì gửi CẢ map (server thay toàn bộ)", () => {
    const base = rule();
    const draft = ruleToDraft(base);
    draft.params = draft.params.map((param) => (param.key === "minCount" ? { ...param, value: "3" } : param));
    const result = buildRulePatch(base, draft, REASON);
    expect(result.ok && result.payload.params).toEqual({
      windowHours: 48,
      minCount: 3,
      sameDirection: true,
      minConfidence: 0.55,
    });
  });

  it("không đổi gì ⇒ unchanged; thiếu lý do ⇒ reason", () => {
    const base = rule();
    expect(buildRulePatch(base, ruleToDraft(base), REASON)).toEqual({ ok: false, errors: { unchanged: true } });
    expect(buildRulePatch(base, { ...ruleToDraft(base), enabled: false }, "ngắn")).toEqual({
      ok: false,
      errors: { reason: true },
    });
  });

  it("cooldown = 0 chỉ hợp lệ cho URGENT_CLINICAL_SIGN; số âm/thập phân bị chặn", () => {
    const base = rule();
    expect(buildRulePatch(base, { ...ruleToDraft(base), cooldownHours: "0" }, REASON)).toMatchObject({
      ok: false,
      errors: { cooldownHours: "zeroNotAllowed" },
    });
    expect(buildRulePatch(base, { ...ruleToDraft(base), cooldownHours: "1.5" }, REASON)).toMatchObject({
      ok: false,
      errors: { cooldownHours: "invalid" },
    });
    const urgent = rule({ code: "URGENT_CLINICAL_SIGN", severity: "URGENT" });
    expect(buildRulePatch(urgent, { ...ruleToDraft(urgent), cooldownHours: "0" }, REASON)).toEqual({
      ok: true,
      payload: { cooldownHours: 0, reason: REASON },
    });
  });
});

describe("thao tác hàng loạt trên rule", () => {
  it("mỗi rule dùng etag của chính nó; rule đã đúng trạng thái bị bỏ qua", () => {
    const rules = [
      rule({ code: "A", enabled: true, etag: "e-A" }),
      rule({ code: "B", enabled: false, etag: "e-B" }),
      rule({ code: "C", enabled: false, etag: "e-C" }),
    ];
    expect(planRuleToggle(rules, new Set(["A", "B"]), false)).toEqual({
      tasks: [{ code: "A", etag: "e-A" }],
      skipped: ["B"],
    });
    expect(planRuleToggle(rules, new Set(["A", "B", "C"]), true)).toEqual({
      tasks: [
        { code: "B", etag: "e-B" },
        { code: "C", etag: "e-C" },
      ],
      skipped: ["A"],
    });
  });

  it("replaceRule thay đúng dòng bằng bản server trả (ETag mới), giữ thứ tự", () => {
    const rules = [rule({ code: "A", etag: "old" }), rule({ code: "B" })];
    const next = replaceRule(rules, rule({ code: "A", etag: "new", enabled: false }));
    expect(next.map((item) => [item.code, item.etag])).toEqual([
      ["A", "new"],
      ["B", rules[1]?.etag],
    ]);
  });
});

describe("chuyển scan sang mèo khác", () => {
  const scan = (scanId: string, catId: string | null, assignment = catId === null ? "SHARED_UNKNOWN" : "ASSIGNED") =>
    ({ scanId, catId, assignment }) as AdminScanItem;
  const scans = [scan("s1", "cat-1"), scan("s2", "cat-2"), scan("s3", null), scan("s4", "cat-1")];

  it("bỏ qua scan đã ở đúng mèo đích, chỉ xét dòng đã chọn", () => {
    expect(planReassign(scans, new Set(["s1", "s2", "s3"]), { kind: "cat", catId: "cat-1" })).toEqual({
      tasks: ["s2", "s3"],
      skipped: ["s1"],
    });
    expect(planReassign(scans, new Set(["s1", "s3"]), { kind: "shared" })).toEqual({
      tasks: ["s1"],
      skipped: ["s3"],
    });
  });

  it("body L18 có đúng một trong toCatId / toAssignment", () => {
    expect(reassignBody({ kind: "cat", catId: "cat-1" }, ` ${REASON} `)).toEqual({ toCatId: "cat-1", reason: REASON });
    expect(reassignBody({ kind: "shared" }, REASON)).toEqual({ toAssignment: "SHARED_UNKNOWN", reason: REASON });
  });
});

describe("hồ sơ sự cố", () => {
  const incident: AdminSecurityIncident = {
    id: "i1",
    publicRef: "INC-2026-0001",
    severity: "MEDIUM",
    category: "DATA_BREACH",
    summary: "Mô tả",
    affectedSubjectCount: 12,
    affectedDataCodes: ["A1"],
    detectedAt: "2026-10-01T00:00:00.000Z",
    classifiedAt: "2026-10-01T02:00:00.000Z",
    createdAt: "2026-10-01T00:05:00.000Z",
  };

  it("mã dữ liệu tách bằng dấu phẩy/khoảng trắng, mỗi mã ≤ 8 ký tự", () => {
    expect(parseDataCodes("A1, B2 ,, C3")).toEqual({ ok: true, codes: ["A1", "B2", "C3"] });
    expect(parseDataCodes("QUA_DAI_9")).toEqual({ ok: false });
  });

  it("mở hồ sơ: bắt buộc mô tả và lý do, bỏ trường trống", () => {
    expect(buildOpenIncidentPayload(emptyIncidentForm(), "")).toEqual({
      ok: false,
      errors: { summary: "required", reason: "required" },
    });
    const form = {
      ...emptyIncidentForm(),
      summary: " Lộ tệp xuất ",
      affectedDataCodes: "A1 B2",
      severity: "HIGH" as const,
    };
    expect(buildOpenIncidentPayload(form, REASON)).toEqual({
      ok: true,
      payload: {
        severity: "HIGH",
        category: "DATA_BREACH",
        summary: "Lộ tệp xuất",
        affectedDataCodes: ["A1", "B2"],
        reason: REASON,
      },
    });
  });

  it("sửa hồ sơ: chỉ gửi trường đổi, mốc thời gian gửi dạng ISO", () => {
    const form = incidentToForm(incident);
    const contained = toLocalInputValue("2026-10-02T09:30:00.000Z");
    const result = buildIncidentPatch(incident, { ...form, severity: "HIGH", containedAt: contained }, REASON);
    expect(result).toEqual({
      ok: true,
      payload: { severity: "HIGH", containedAt: "2026-10-02T09:30:00.000Z", reason: REASON },
    });
  });

  it("không xoá được mốc đã ghi hay số người bị ảnh hưởng; không đổi gì thì báo unchanged", () => {
    const form = incidentToForm(incident);
    expect(buildIncidentPatch(incident, { ...form, classifiedAt: "", affectedSubjectCount: "" }, REASON)).toEqual({
      ok: false,
      errors: { classifiedAt: "cannotClear", affectedSubjectCount: "cannotClear" },
    });
    expect(buildIncidentPatch(incident, form, REASON)).toEqual({ ok: false, errors: { unchanged: true } });
  });

  it("cụm xác nhận không phân biệt hoa thường và dấu", () => {
    expect(isConfirmPhraseMatch(" thu hoi ", "THU HỒI")).toBe(true);
    expect(isConfirmPhraseMatch("THU", "THU HỒI")).toBe(false);
    expect(isConfirmPhraseMatch("", "")).toBe(false);
  });
});

describe("phiên bản chính sách", () => {
  const now = new Date("2026-10-11T00:00:00.000Z");
  const future = toLocalInputValue("2026-11-01T00:00:00.000Z");
  const valid: PolicyDraftForm = {
    ...emptyPolicyDraft(),
    version: "2.1.0",
    title: "Điều khoản sử dụng",
    contentMd: "# Nội dung",
    effectiveFrom: future,
  };

  it("bản hợp lệ không có lỗi; payload chỉ mang một nguồn nội dung", () => {
    expect(validatePolicyDraft(valid, REASON, now)).toEqual({});
    const payload = policyDraftToPayload(valid, REASON);
    expect(payload).toMatchObject({ policyType: "TERMS", version: "2.1.0", locale: "vi", contentMd: "# Nội dung" });
    expect(payload).not.toHaveProperty("contentUrl");
    expect(payload).not.toHaveProperty("affectedPurposes");
    expect(payload.effectiveFrom).toBe("2026-11-01T00:00:00.000Z");
  });

  it("semver sai, ngày không ở tương lai, thiếu nguồn, thiếu mục đích khi bắt xác nhận lại", () => {
    expect(
      validatePolicyDraft(
        {
          ...valid,
          version: "v2",
          effectiveFrom: toLocalInputValue("2026-10-01T00:00:00.000Z"),
          source: "url",
          contentUrl: "ftp://x",
          requiresReconsent: true,
        },
        "",
        now,
      ),
    ).toEqual({
      version: "invalid",
      effectiveFrom: "notFuture",
      contentUrl: "invalid",
      affectedPurposes: "required",
      reason: "required",
    });
  });

  it("bắt xác nhận lại thì gửi kèm affectedPurposes", () => {
    const payload = policyDraftToPayload(
      { ...valid, requiresReconsent: true, affectedPurposes: ["ANALYTICS"] },
      REASON,
    );
    expect(payload.affectedPurposes).toEqual(["ANALYTICS"]);
  });
});

describe("latestConsentByPurpose", () => {
  const record = (id: string, purposeCode: string, status: AdminConsentRecord["status"], occurredAt: string) =>
    ({ id, purposeCode, status, occurredAt, userId: "u", method: "WEB_TOGGLE" }) as AdminConsentRecord;

  it("lấy bản ghi mới nhất của từng mục đích, sắp theo mã", () => {
    const latest = latestConsentByPurpose([
      record("1", "MARKETING", "WITHDRAWN", "2026-10-05T00:00:00Z"),
      record("2", "ANALYTICS", "GRANTED", "2026-10-04T00:00:00Z"),
      record("3", "MARKETING", "GRANTED", "2026-09-01T00:00:00Z"),
      record("4", "ANALYTICS", "DENIED", "2026-10-04T00:00:00Z"),
    ]);
    expect(latest.map((row) => [row.purposeCode, row.id])).toEqual([
      ["ANALYTICS", "2"],
      ["MARKETING", "1"],
    ]);
  });
});
