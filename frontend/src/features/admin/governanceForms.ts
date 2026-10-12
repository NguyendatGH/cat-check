import { isReasonValid } from "./api";
import { normalizeSearchText } from "./tableTools";
import type {
  AdminConsentRecord,
  AdminMonitoringRule,
  AdminScanItem,
  AdminSecurityIncident,
  CreatePolicyVersionPayload,
  IncidentCategory,
  IncidentSeverity,
  OpenSecurityIncidentPayload,
  PolicyLocale,
  PolicyType,
  ReassignTarget,
  UpdateMonitoringRulePayload,
  UpdateSecurityIncidentPayload,
} from "./governanceTypes";


const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: string): boolean {
  return UUID_PATTERN.test(value.trim());
}


function pad(value: number): string {
  return String(value).padStart(2, "0");
}

export function toLocalInputValue(iso: string | null | undefined): string {
  if (iso === null || iso === undefined || iso === "") {
    return "";
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return `${String(date.getFullYear())}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function fromLocalInputValue(value: string): string | null {
  if (value.trim() === "") {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}


export type ParamKind = "number" | "boolean" | "string" | "json";

export interface ParamDraft {
  key: string;
  kind: ParamKind;
  value: string;
}

export interface RuleDraft {
  enabled: boolean;
  pushEnabled: boolean;
  cooldownHours: string;
  params: ParamDraft[];
}

function kindOf(value: unknown): ParamKind {
  if (typeof value === "number") return "number";
  if (typeof value === "boolean") return "boolean";
  if (typeof value === "string") return "string";
  return "json";
}

export function paramsToDraft(params: Record<string, unknown> | null | undefined): ParamDraft[] {
  return Object.entries(params ?? {}).map(([key, value]) => {
    const kind = kindOf(value);
    return { key, kind, value: kind === "json" ? JSON.stringify(value) : String(value) };
  });
}

export function draftToParams(
  drafts: readonly ParamDraft[],
): { ok: true; params: Record<string, unknown> } | { ok: false; invalidKeys: string[] } {
  const params: Record<string, unknown> = {};
  const invalidKeys: string[] = [];
  for (const draft of drafts) {
    const raw = draft.value.trim();
    if (draft.kind === "number") {
      const parsed = Number(raw.replace(",", "."));
      if (raw === "" || !Number.isFinite(parsed)) {
        invalidKeys.push(draft.key);
      } else {
        params[draft.key] = parsed;
      }
    } else if (draft.kind === "boolean") {
      if (raw !== "true" && raw !== "false") {
        invalidKeys.push(draft.key);
      } else {
        params[draft.key] = raw === "true";
      }
    } else if (draft.kind === "string") {
      params[draft.key] = draft.value;
    } else {
      try {
        params[draft.key] = JSON.parse(raw) as unknown;
      } catch {
        invalidKeys.push(draft.key);
      }
    }
  }
  return invalidKeys.length > 0 ? { ok: false, invalidKeys } : { ok: true, params };
}

export function stableStringify(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(",")}]`;
  }
  if (value !== null && typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>).sort(([a], [b]) => a.localeCompare(b));
    return `{${entries.map(([key, inner]) => `${JSON.stringify(key)}:${stableStringify(inner)}`).join(",")}}`;
  }
  return value === undefined || typeof value === "function" ? "null" : JSON.stringify(value);
}

export function ruleToDraft(rule: AdminMonitoringRule): RuleDraft {
  return {
    enabled: rule.enabled,
    pushEnabled: rule.pushEnabled,
    cooldownHours: String(rule.cooldownHours),
    params: paramsToDraft(rule.params),
  };
}

export const ZERO_COOLDOWN_RULE = "URGENT_CLINICAL_SIGN";

export interface RulePatchErrors {
  cooldownHours?: "invalid" | "zeroNotAllowed";
  params?: string[];
  reason?: true;
  unchanged?: true;
}

export function buildRulePatch(
  rule: AdminMonitoringRule,
  draft: RuleDraft,
  reason: string,
): { ok: true; payload: UpdateMonitoringRulePayload } | { ok: false; errors: RulePatchErrors } {
  const errors: RulePatchErrors = {};
  const payload: UpdateMonitoringRulePayload = { reason: reason.trim() };

  if (draft.enabled !== rule.enabled) payload.enabled = draft.enabled;
  if (draft.pushEnabled !== rule.pushEnabled) payload.pushEnabled = draft.pushEnabled;

  const cooldownRaw = draft.cooldownHours.trim();
  const cooldown = Number(cooldownRaw);
  if (cooldownRaw === "" || !Number.isInteger(cooldown) || cooldown < 0) {
    errors.cooldownHours = "invalid";
  } else if (cooldown === 0 && rule.code !== ZERO_COOLDOWN_RULE) {
    errors.cooldownHours = "zeroNotAllowed";
  } else if (cooldown !== rule.cooldownHours) {
    payload.cooldownHours = cooldown;
  }

  const parsed = draftToParams(draft.params);
  if (!parsed.ok) {
    errors.params = parsed.invalidKeys;
  } else if (stableStringify(parsed.params) !== stableStringify(rule.params ?? {})) {
    payload.params = parsed.params;
  }

  if (!isReasonValid(reason)) errors.reason = true;

  const changed = Object.keys(payload).some((key) => key !== "reason");
  if (!changed && errors.cooldownHours === undefined && errors.params === undefined) errors.unchanged = true;

  return Object.keys(errors).length > 0 ? { ok: false, errors } : { ok: true, payload };
}

export interface RuleToggleTask {
  code: string;
  etag: string;
}

export function planRuleToggle(
  rules: readonly AdminMonitoringRule[],
  selectedCodes: ReadonlySet<string>,
  enabled: boolean,
): { tasks: RuleToggleTask[]; skipped: string[] } {
  const tasks: RuleToggleTask[] = [];
  const skipped: string[] = [];
  for (const rule of rules) {
    if (!selectedCodes.has(rule.code)) continue;
    if (rule.enabled === enabled) {
      skipped.push(rule.code);
    } else {
      tasks.push({ code: rule.code, etag: rule.etag });
    }
  }
  return { tasks, skipped };
}

export function replaceRule(
  rules: readonly AdminMonitoringRule[],
  updated: AdminMonitoringRule,
): AdminMonitoringRule[] {
  return rules.map((rule) => (rule.code === updated.code ? updated : rule));
}


export const SHARED_UNKNOWN = "SHARED_UNKNOWN";

export function planReassign(
  scans: readonly AdminScanItem[],
  selectedIds: ReadonlySet<string>,
  target: ReassignTarget,
): { tasks: string[]; skipped: string[] } {
  const tasks: string[] = [];
  const skipped: string[] = [];
  for (const scan of scans) {
    if (!selectedIds.has(scan.scanId)) continue;
    const alreadyThere =
      target.kind === "cat" ? scan.catId === target.catId : scan.assignment === SHARED_UNKNOWN && !scan.catId;
    if (alreadyThere) {
      skipped.push(scan.scanId);
    } else {
      tasks.push(scan.scanId);
    }
  }
  return { tasks, skipped };
}

export function reassignBody(target: ReassignTarget, reason: string): Record<string, string> {
  return target.kind === "cat"
    ? { toCatId: target.catId, reason: reason.trim() }
    : { toAssignment: SHARED_UNKNOWN, reason: reason.trim() };
}


export interface IncidentForm {
  severity: IncidentSeverity;
  category: IncidentCategory;
  summary: string;
  affectedSubjectCount: string;
  affectedDataCodes: string;
  detectedAt: string;
  classifiedAt: string;
  containedAt: string;
  authorityNotifiedAt: string;
  subjectsNotifiedAt: string;
  resolvedAt: string;
  reportRef: string;
}

export const INCIDENT_MILESTONES = [
  "classifiedAt",
  "containedAt",
  "authorityNotifiedAt",
  "subjectsNotifiedAt",
  "resolvedAt",
] as const;
export type IncidentMilestone = (typeof INCIDENT_MILESTONES)[number];

export type IncidentFormErrorCode = "required" | "tooLong" | "invalid" | "cannotClear";
export type IncidentFormErrors = Partial<Record<keyof IncidentForm | "reason", IncidentFormErrorCode>> & {
  unchanged?: true;
};

export function emptyIncidentForm(): IncidentForm {
  return {
    severity: "MEDIUM",
    category: "DATA_BREACH",
    summary: "",
    affectedSubjectCount: "",
    affectedDataCodes: "",
    detectedAt: "",
    classifiedAt: "",
    containedAt: "",
    authorityNotifiedAt: "",
    subjectsNotifiedAt: "",
    resolvedAt: "",
    reportRef: "",
  };
}

export function incidentToForm(incident: AdminSecurityIncident): IncidentForm {
  return {
    severity: incident.severity,
    category: incident.category,
    summary: incident.summary,
    affectedSubjectCount:
      incident.affectedSubjectCount === null || incident.affectedSubjectCount === undefined
        ? ""
        : String(incident.affectedSubjectCount),
    affectedDataCodes: (incident.affectedDataCodes ?? []).join(", "),
    detectedAt: toLocalInputValue(incident.detectedAt),
    classifiedAt: toLocalInputValue(incident.classifiedAt),
    containedAt: toLocalInputValue(incident.containedAt),
    authorityNotifiedAt: toLocalInputValue(incident.authorityNotifiedAt),
    subjectsNotifiedAt: toLocalInputValue(incident.subjectsNotifiedAt),
    resolvedAt: toLocalInputValue(incident.resolvedAt),
    reportRef: incident.reportRef ?? "",
  };
}

export function parseDataCodes(value: string): { ok: true; codes: string[] } | { ok: false } {
  const codes = value
    .split(/[,\s]+/)
    .map((code) => code.trim())
    .filter((code) => code.length > 0);
  return codes.every((code) => code.length <= 8) ? { ok: true, codes } : { ok: false };
}

function parseCount(value: string): number | null | "invalid" {
  const raw = value.trim();
  if (raw === "") return null;
  const parsed = Number(raw);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : "invalid";
}

function validateIncidentCommon(form: IncidentForm, errors: IncidentFormErrors): void {
  if (form.summary.trim() === "") errors.summary = "required";
  else if (form.summary.length > 4000) errors.summary = "tooLong";
  if (form.reportRef.length > 500) errors.reportRef = "tooLong";
  if (parseCount(form.affectedSubjectCount) === "invalid") errors.affectedSubjectCount = "invalid";
  if (!parseDataCodes(form.affectedDataCodes).ok) errors.affectedDataCodes = "invalid";
}

export function buildOpenIncidentPayload(
  form: IncidentForm,
  reason: string,
): { ok: true; payload: OpenSecurityIncidentPayload } | { ok: false; errors: IncidentFormErrors } {
  const errors: IncidentFormErrors = {};
  validateIncidentCommon(form, errors);
  const detectedAt = fromLocalInputValue(form.detectedAt);
  if (form.detectedAt.trim() !== "" && detectedAt === null) errors.detectedAt = "invalid";
  if (!isReasonValid(reason)) errors.reason = "required";
  if (Object.keys(errors).length > 0) return { ok: false, errors };

  const payload: OpenSecurityIncidentPayload = {
    severity: form.severity,
    category: form.category,
    summary: form.summary.trim(),
    reason: reason.trim(),
  };
  const count = parseCount(form.affectedSubjectCount);
  if (typeof count === "number") payload.affectedSubjectCount = count;
  const codes = parseDataCodes(form.affectedDataCodes);
  if (codes.ok && codes.codes.length > 0) payload.affectedDataCodes = codes.codes;
  if (detectedAt !== null) payload.detectedAt = detectedAt;
  if (form.reportRef.trim() !== "") payload.reportRef = form.reportRef.trim();
  return { ok: true, payload };
}

export function buildIncidentPatch(
  incident: AdminSecurityIncident,
  form: IncidentForm,
  reason: string,
): { ok: true; payload: UpdateSecurityIncidentPayload } | { ok: false; errors: IncidentFormErrors } {
  const errors: IncidentFormErrors = {};
  validateIncidentCommon(form, errors);
  const original = incidentToForm(incident);
  const payload: UpdateSecurityIncidentPayload = { reason: reason.trim() };

  if (form.severity !== original.severity) payload.severity = form.severity;
  if (form.category !== original.category) payload.category = form.category;
  if (form.summary.trim() !== original.summary.trim() && errors.summary === undefined) {
    payload.summary = form.summary.trim();
  }
  if (form.reportRef.trim() !== original.reportRef.trim() && errors.reportRef === undefined) {
    if (form.reportRef.trim() === "") errors.reportRef = "cannotClear";
    else payload.reportRef = form.reportRef.trim();
  }

  const count = parseCount(form.affectedSubjectCount);
  if (count !== "invalid" && form.affectedSubjectCount.trim() !== original.affectedSubjectCount) {
    if (count === null) errors.affectedSubjectCount = "cannotClear";
    else payload.affectedSubjectCount = count;
  }

  const codes = parseDataCodes(form.affectedDataCodes);
  const originalCodes = parseDataCodes(original.affectedDataCodes);
  if (codes.ok && originalCodes.ok && codes.codes.join(",") !== originalCodes.codes.join(",")) {
    payload.affectedDataCodes = codes.codes;
  }

  for (const field of INCIDENT_MILESTONES) {
    if (form[field] === original[field]) continue;
    if (form[field].trim() === "") {
      errors[field] = "cannotClear";
      continue;
    }
    const iso = fromLocalInputValue(form[field]);
    if (iso === null) errors[field] = "invalid";
    else payload[field] = iso;
  }

  if (!isReasonValid(reason)) errors.reason = "required";
  const changed = Object.keys(payload).some((key) => key !== "reason");
  if (!changed && Object.keys(errors).every((key) => key === "reason")) errors.unchanged = true;

  return Object.keys(errors).length > 0 ? { ok: false, errors } : { ok: true, payload };
}

export function isConfirmPhraseMatch(input: string, phrase: string): boolean {
  const expected = normalizeSearchText(phrase);
  return expected.length > 0 && normalizeSearchText(input) === expected;
}


export interface PolicyDraftForm {
  policyType: PolicyType;
  version: string;
  locale: PolicyLocale;
  title: string;
  source: "markdown" | "url";
  contentMd: string;
  contentUrl: string;
  summaryOfChanges: string;
  requiresReconsent: boolean;
  affectedPurposes: string[];
  effectiveFrom: string;
}

export type PolicyDraftErrorCode = "required" | "tooLong" | "invalid" | "notFuture";
export type PolicyDraftErrors = Partial<Record<keyof PolicyDraftForm | "reason", PolicyDraftErrorCode>>;

const POLICY_VERSION_PATTERN = /^\d{1,4}\.\d{1,4}(\.\d{1,4})?$/;

export function emptyPolicyDraft(): PolicyDraftForm {
  return {
    policyType: "TERMS",
    version: "",
    locale: "vi",
    title: "",
    source: "markdown",
    contentMd: "",
    contentUrl: "",
    summaryOfChanges: "",
    requiresReconsent: false,
    affectedPurposes: [],
    effectiveFrom: "",
  };
}

export function validatePolicyDraft(form: PolicyDraftForm, reason: string, now: Date): PolicyDraftErrors {
  const errors: PolicyDraftErrors = {};
  if (!POLICY_VERSION_PATTERN.test(form.version.trim())) errors.version = "invalid";
  if (form.title.trim() === "") errors.title = "required";
  else if (form.title.length > 300) errors.title = "tooLong";
  if (form.source === "markdown" && form.contentMd.trim() === "") errors.contentMd = "required";
  if (form.source === "url") {
    const url = form.contentUrl.trim();
    if (url === "") errors.contentUrl = "required";
    else if (!/^(https?:\/\/|\/)/i.test(url)) errors.contentUrl = "invalid";
  }
  if (form.summaryOfChanges.length > 4000) errors.summaryOfChanges = "tooLong";
  if (form.requiresReconsent && form.affectedPurposes.length === 0) errors.affectedPurposes = "required";
  const effectiveFrom = fromLocalInputValue(form.effectiveFrom);
  if (form.effectiveFrom.trim() === "") errors.effectiveFrom = "required";
  else if (effectiveFrom === null) errors.effectiveFrom = "invalid";
  else if (new Date(effectiveFrom).getTime() <= now.getTime()) errors.effectiveFrom = "notFuture";
  if (!isReasonValid(reason)) errors.reason = "required";
  return errors;
}

export function policyDraftToPayload(form: PolicyDraftForm, reason: string): CreatePolicyVersionPayload {
  const payload: CreatePolicyVersionPayload = {
    policyType: form.policyType,
    version: form.version.trim(),
    locale: form.locale,
    title: form.title.trim(),
    requiresReconsent: form.requiresReconsent,
    effectiveFrom: fromLocalInputValue(form.effectiveFrom) ?? "",
    reason: reason.trim(),
  };
  if (form.source === "markdown") payload.contentMd = form.contentMd;
  else payload.contentUrl = form.contentUrl.trim();
  if (form.summaryOfChanges.trim() !== "") payload.summaryOfChanges = form.summaryOfChanges.trim();
  if (form.requiresReconsent) payload.affectedPurposes = [...form.affectedPurposes];
  return payload;
}


export function latestConsentByPurpose(records: readonly AdminConsentRecord[]): AdminConsentRecord[] {
  const latest = new Map<string, AdminConsentRecord>();
  for (const record of records) {
    const current = latest.get(record.purposeCode);
    if (current === undefined || Date.parse(record.occurredAt) > Date.parse(current.occurredAt)) {
      latest.set(record.purposeCode, record);
    }
  }
  return [...latest.values()].sort((a, b) => a.purposeCode.localeCompare(b.purposeCode));
}
