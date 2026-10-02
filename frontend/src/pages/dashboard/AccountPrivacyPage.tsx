import { useCallback, useMemo, useRef, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import {
  AlertTriangle,
  Check,
  Clock,
  Database,
  Download,
  FileText,
  History,
  Info,
  KeyRound,
  Loader2,
  Mail,
  PauseCircle,
  ShieldCheck,
  Trash2,
} from "lucide-react";
import {
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Input,
  SkeletonLoader,
  Switch,
} from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { cn } from "@/shared/lib/cn";
import { useSessionStore } from "@/entities/user";
import {
  DELETION_GRACE_DAYS,
  useCancelAccountDeletion,
  useConsentHistory,
  useConsentPurposes,
  useCreateDsarRequest,
  useCreateExportRequest,
  useCurrentConsents,
  useDataInventory,
  useDownloadExport,
  useDsarRequests,
  useExportStatus,
  useReauth,
  useRecordConsents,
  useRequestAccountDeletion,
  useRequestStepUpOtp,
  useSetRestriction,
  useVerifyStepUpOtp,
  type ConsentStatus,
  type DataInventoryView,
  type DsarRequestView,
  type ManualDsarType,
  type PurposeView,
  type StepUpMethod,
} from "@/features/privacy";

/**
 * `/account/privacy` — Trung tâm quyền riêng tư (p15 §15.3.3, §15.4).
 *
 * Nối API thật của `PrivacyController` (C1–C15, p8 §8.4.3): danh mục mục đích + trạng thái
 * consent hiện hành + sổ lịch sử append-only, bảng kiểm kê dữ liệu, xuất dữ liệu, tạm ngừng
 * xử lý, xoá tài khoản (ân hạn 7 ngày, có đường huỷ) và bốn loại DSAR phải có người xử lý.
 *
 * Ba nguyên tắc chi phối màn hình này, không phải lựa chọn thẩm mỹ:
 *  1. **Trạng thái hiện hành đọc từ server** (view `consent_current`, p4 B2) — không suy ra
 *     từ lịch sử ở client, vì lệch ở đây là lệch bằng chứng pháp lý.
 *  2. **Append-only**: rút đồng ý là INSERT một dòng `WITHDRAWN` (I16), nên UI nói rõ lịch
 *     sử là vĩnh viễn và không có nút xoá dòng nào.
 *  3. **Không tick sẵn** mục tuỳ chọn (I17, p15 §15.3.1 C4) — `NONE` hiển thị là TẮT kèm câu
 *     "chưa được hỏi không có nghĩa là đồng ý", không phải khoảng trắng im lặng.
 *
 * Những chỗ spec đòi mà backend chưa có endpoint đều hiện khối "Chưa có API" kèm số mục spec,
 * KHÔNG bịa dữ liệu: tải tệp bằng chứng đồng ý (§15.3.1 C6), thời hạn lưu bằng chữ (§15.5.1),
 * đọc thẳng trạng thái tạm ngừng xử lý (§15.4.7), lựa chọn nội dung cộng đồng khi xoá
 * (§15.4.6, Giai đoạn 2).
 *
 * Bố cục: trang nằm trong `TaskLayout` — layout đã cấp `px-4 py-6` và hộp 944px ở `lg`, trang
 * KHÔNG tự thêm. Cột nội dung co giãn + cột phụ 360px theo đúng idiom `SettingsSecurityPage`.
 */

/* ------------------------------------------------------------------ *
 * Tiện ích hiển thị
 * ------------------------------------------------------------------ */

const CARD = "flex flex-col gap-4 rounded-2xl bg-surface p-5 shadow-brand-md";
const SECTION_TITLE = "flex items-center gap-2 text-h3 font-bold text-text-primary";
const ASIDE_CARD = "flex flex-col gap-2 rounded-2xl bg-surface p-5 shadow-brand-md";

/** Yêu cầu còn đang mở — bốn trạng thái chưa đóng của `dsar_request`. */
const OPEN_DSAR_STATUSES = new Set(["RECEIVED", "IDENTITY_PENDING", "IN_PROGRESS", "EXTENDED"]);

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" });
}

function formatDate(value: Date): string {
  return value.toLocaleString("vi-VN", { dateStyle: "long", timeStyle: "short" });
}

function daysUntil(deadline: Date): number {
  return Math.max(0, Math.ceil((deadline.getTime() - Date.now()) / 86_400_000));
}

function errorCodeOf(error: unknown): string | undefined {
  return isApiError(error) ? error.code : undefined;
}

/** Khối "Chưa có API" — nói thẳng mục spec còn thiếu thay vì dựng UI giả. */
function MissingApiNote({ title, children }: { title: string; children: ReactNode }) {
  const { t } = useTranslation("legal");
  return (
    <div className="flex flex-col gap-1 rounded-lg border border-dashed border-border bg-background-alt p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge className="bg-warning-bg text-warning-text">{t("privacyCenter.noApiBadge")}</Badge>
        <span className="text-caption font-semibold text-text-primary">{title}</span>
      </div>
      <p className="text-caption text-text-secondary">{children}</p>
    </div>
  );
}

function InlineError({ message }: { message: string }) {
  return (
    <p role="alert" className="flex items-start gap-1.5 text-caption text-danger">
      <AlertTriangle size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
      {message}
    </p>
  );
}

/* ------------------------------------------------------------------ *
 * Step-up re-auth (p15 REQ-DSAR-04)
 * ------------------------------------------------------------------ */

interface StepUpDialogProps {
  open: boolean;
  email: string | null;
  targetAction: string;
  onClose: () => void;
  onVerified: () => void;
}

/**
 * Xác minh lại danh tính trước khi xuất/xoá dữ liệu. p15 REQ-DSAR-04 nêu đích danh OTP email
 * nên đó là cách mặc định; mật khẩu là đường thứ hai cho người đã đặt mật khẩu.
 * Luồng OTP: A4 `/auth/otp/request` → A5 `/auth/otp/verify` (lấy vé) → A12 `/auth/reauth`.
 */
function StepUpDialog({ open, email, targetAction, onClose, onVerified }: StepUpDialogProps) {
  const { t } = useTranslation("legal");
  const [method, setMethod] = useState<StepUpMethod>("EMAIL_OTP");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [maskedEmail, setMaskedEmail] = useState<string | null>(null);
  const [errorKey, setErrorKey] = useState<string | null>(null);

  const requestOtp = useRequestStepUpOtp();
  const verifyOtp = useVerifyStepUpOtp();
  const reauth = useReauth();

  const busy = requestOtp.isPending || verifyOtp.isPending || reauth.isPending;

  const reset = useCallback(() => {
    setCode("");
    setPassword("");
    setMaskedEmail(null);
    setErrorKey(null);
  }, []);

  const mapError = (error: unknown): string => {
    switch (errorCodeOf(error)) {
      case "OTP_INVALID":
        return "privacyCenter.stepUp.otpInvalid";
      case "OTP_EXPIRED":
        return "privacyCenter.stepUp.otpExpired";
      case "OTP_LOCKED":
        return "privacyCenter.stepUp.otpLocked";
      case "OTP_RESEND_COOLDOWN":
        return "privacyCenter.stepUp.cooldown";
      case "OTP_DELIVERY_FAILED":
        return "privacyCenter.stepUp.deliveryFailed";
      case "REAUTH_METHOD_UNAVAILABLE":
        return "privacyCenter.stepUp.passwordUnavailable";
      default:
        return "privacyCenter.stepUp.failed";
    }
  };

  const sendCode = async () => {
    if (email === null) {
      setErrorKey("privacyCenter.stepUp.noEmail");
      return;
    }
    setErrorKey(null);
    try {
      const result = await requestOtp.mutateAsync(email);
      setMaskedEmail(result.maskedEmail);
    } catch (error) {
      setErrorKey(mapError(error));
    }
  };

  const submit = async () => {
    setErrorKey(null);
    try {
      if (method === "EMAIL_OTP") {
        if (email === null) {
          setErrorKey("privacyCenter.stepUp.noEmail");
          return;
        }
        const ticket = await verifyOtp.mutateAsync({ email, code: code.trim() });
        await reauth.mutateAsync({ method, credential: ticket.otpTicket, targetAction });
      } else {
        await reauth.mutateAsync({ method, credential: password, targetAction });
      }
      reset();
      onVerified();
    } catch (error) {
      setErrorKey(mapError(error));
    }
  };

  const canSubmit = method === "EMAIL_OTP" ? code.trim().length > 0 : password.length > 0;

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          reset();
          onClose();
        }
      }}
    >
      <DialogContent className="flex flex-col gap-4">
        <DialogTitle className="flex items-center gap-2 font-bold">
          <KeyRound size={18} className="text-primary-dark" aria-hidden="true" />
          {t("privacyCenter.stepUp.title")}
        </DialogTitle>
        <DialogDescription>{t("privacyCenter.stepUp.intro")}</DialogDescription>

        <div className="flex flex-col gap-2">
          <p className="text-caption font-semibold text-text-primary">{t("privacyCenter.stepUp.methodLabel")}</p>
          <div className="flex flex-wrap gap-2">
            {(["EMAIL_OTP", "PASSWORD"] as const).map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => {
                  setMethod(option);
                  setErrorKey(null);
                }}
                aria-pressed={method === option}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-caption font-semibold",
                  method === option
                    ? "border-primary-dark bg-chip-bg text-primary-dark"
                    : "border-border text-text-secondary hover:bg-background-alt",
                )}
              >
                {option === "EMAIL_OTP" ? (
                  <Mail size={14} aria-hidden="true" />
                ) : (
                  <KeyRound size={14} aria-hidden="true" />
                )}
                {option === "EMAIL_OTP"
                  ? t("privacyCenter.stepUp.methodEmailOtp")
                  : t("privacyCenter.stepUp.methodPassword")}
              </button>
            ))}
          </div>
        </div>

        {method === "EMAIL_OTP" ? (
          <div className="flex flex-col gap-3">
            <Button
              type="button"
              variant="tertiary"
              size="sm"
              className="self-start"
              loading={requestOtp.isPending}
              onClick={() => {
                void sendCode();
              }}
            >
              {maskedEmail === null ? t("privacyCenter.stepUp.sendCode") : t("privacyCenter.stepUp.resendCode")}
            </Button>
            {maskedEmail === null ? null : (
              <p className="text-caption text-success-text">
                {t("privacyCenter.stepUp.codeSent", { email: maskedEmail })}
              </p>
            )}
            <Input
              label={t("privacyCenter.stepUp.codeLabel")}
              inputMode="numeric"
              autoComplete="one-time-code"
              value={code}
              onChange={(event) => {
                setCode(event.target.value);
              }}
            />
          </div>
        ) : (
          <Input
            label={t("privacyCenter.stepUp.passwordLabel")}
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
            }}
          />
        )}

        {errorKey === null ? null : <InlineError message={t(errorKey)} />}
        <p className="text-small text-text-tertiary">{t("privacyCenter.stepUp.windowNote")}</p>

        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="md"
            disabled={!canSubmit}
            loading={verifyOtp.isPending || reauth.isPending}
            onClick={() => {
              void submit();
            }}
          >
            {t("privacyCenter.stepUp.submit")}
          </Button>
          <Button
            type="button"
            variant="tertiary"
            size="md"
            disabled={busy}
            onClick={() => {
              reset();
              onClose();
            }}
          >
            {t("privacyCenter.stepUp.cancel")}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/* ------------------------------------------------------------------ *
 * Một dòng mục đích xử lý
 * ------------------------------------------------------------------ */

interface PurposeRowProps {
  purpose: PurposeView;
  status: ConsentStatus;
  pending: boolean;
  disabled: boolean;
  onChange: (granted: boolean) => void;
}

function PurposeRow({ purpose, status, pending, disabled, onChange }: PurposeRowProps) {
  const { t } = useTranslation("legal");
  const granted = status === "GRANTED";

  return (
    <li className="flex flex-col gap-2 border-b border-border py-4 last:border-b-0 last:pb-0">
      <div className="flex items-start justify-between gap-4">
        <div className="flex min-w-0 flex-col gap-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-body font-semibold text-text-primary">{purpose.label}</span>
            {purpose.mandatory ? <Badge tone="brand">{t("privacyCenter.consent.mandatoryBadge")}</Badge> : null}
            {/* Đ6.4 NĐ356 + p4 B3: dữ liệu nhạy cảm BẮT BUỘC có nhãn cạnh ô chọn. */}
            {purpose.sensitive ? (
              <Badge className="bg-danger-bg text-danger-text">{t("privacyCenter.consent.sensitiveBadge")}</Badge>
            ) : null}
            {purpose.phase > 1 ? (
              <Badge>{t("privacyCenter.consent.phaseBadge", { phase: purpose.phase })}</Badge>
            ) : null}
          </div>
          <p className="text-caption text-text-secondary">{purpose.description}</p>
          {purpose.withdrawEffect ? (
            <p className="text-caption text-text-tertiary">
              <span className="font-semibold">{t("privacyCenter.consent.withdrawEffectLabel")}</span>{" "}
              {purpose.withdrawEffect}
            </p>
          ) : null}
          {purpose.mandatory ? (
            <p className="text-caption text-text-tertiary">{t("privacyCenter.consent.mandatoryNote")}</p>
          ) : null}
        </div>

        <div className="flex shrink-0 flex-col items-end gap-1.5">
          {pending ? (
            <Loader2 size={18} className="animate-spin text-text-tertiary" aria-hidden="true" />
          ) : (
            <Switch
              checked={granted}
              disabled={disabled || purpose.mandatory}
              aria-label={t("privacyCenter.consent.toggleAria", { label: purpose.label })}
              onCheckedChange={onChange}
            />
          )}
          <span className={cn("text-small font-semibold", granted ? "text-success-text" : "text-text-tertiary")}>
            {t(`privacyCenter.consent.status.${status}`)}
          </span>
        </div>
      </div>
      {status === "NONE" ? (
        <p className="text-small text-text-tertiary">{t("privacyCenter.consent.noneHint")}</p>
      ) : null}
    </li>
  );
}

/* ------------------------------------------------------------------ *
 * Một dòng bảng kiểm kê dữ liệu
 * ------------------------------------------------------------------ */

function InventoryRow({ item }: { item: DataInventoryView }) {
  const { t } = useTranslation("legal");
  const field = (label: string, value: ReactNode) => (
    <div className="flex flex-col">
      <dt className="text-small text-text-tertiary">{label}</dt>
      <dd className="text-caption text-text-secondary">{value}</dd>
    </div>
  );

  return (
    <li className="flex flex-col gap-2 border-b border-border py-4 last:border-b-0 last:pb-0">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-body font-semibold text-text-primary">{item.category}</span>
        {item.sensitivity === "SENSITIVE" ? (
          <Badge className="bg-danger-bg text-danger-text">{t("privacyCenter.inventory.sensitivity.SENSITIVE")}</Badge>
        ) : (
          <Badge>{t("privacyCenter.inventory.sensitivity.BASIC")}</Badge>
        )}
      </div>
      <p className="text-caption text-text-secondary">{item.description}</p>
      <dl className="grid gap-x-4 gap-y-2 sm:grid-cols-2">
        {field(
          t("privacyCenter.inventory.labelLegalBasis"),
          t(`privacyCenter.inventory.legalBasis.${item.legalBasis}`),
        )}
        {field(t("privacyCenter.inventory.labelPurposes"), item.purposes.join(", "))}
        {field(t("privacyCenter.inventory.labelRetention"), item.retentionPolicyCode)}
        {field(t("privacyCenter.inventory.labelStorage"), item.storageLocation)}
        {field(
          t("privacyCenter.inventory.labelCrossBorder"),
          item.crossBorder ? t("privacyCenter.inventory.crossBorderYes") : t("privacyCenter.inventory.crossBorderNo"),
        )}
        {field(
          t("privacyCenter.inventory.labelRecipient"),
          item.recipient ?? t("privacyCenter.inventory.recipientNone"),
        )}
      </dl>
    </li>
  );
}

/* ------------------------------------------------------------------ *
 * Trang
 * ------------------------------------------------------------------ */

const MANUAL_DSAR_TYPES: readonly ManualDsarType[] = ["RECTIFY", "OBJECT", "PROTECTION_MEASURE", "COMPLAINT"];

export function AccountPrivacyPage() {
  const { t } = useTranslation(["legal", "common"]);
  const sessionEmail = useSessionStore((state) => state.user?.email ?? null);

  const purposes = useConsentPurposes();
  const consents = useCurrentConsents();
  const history = useConsentHistory();
  const inventory = useDataInventory();
  const requests = useDsarRequests();

  const recordConsents = useRecordConsents();
  const createExport = useCreateExportRequest();
  const downloadExport = useDownloadExport();
  const requestDeletion = useRequestAccountDeletion();
  const cancelDeletion = useCancelAccountDeletion();
  const setRestriction = useSetRestriction();
  const createDsarRequest = useCreateDsarRequest();

  const [stepUpOpen, setStepUpOpen] = useState(false);
  const [stepUpAction, setStepUpAction] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [showAllHistory, setShowAllHistory] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteEmail, setDeleteEmail] = useState("");
  const pendingActionRef = useRef<(() => Promise<void>) | null>(null);

  /**
   * Chạy một thao tác cần step-up. Không mở hộp thoại trước: phiên có thể đã step-up trong
   * cửa sổ 300 giây và không có endpoint nào đọc được trạng thái đó, nên cách trung thực là
   * thử — gặp đúng `DSAR_IDENTITY_VERIFICATION_REQUIRED` mới xin xác minh rồi chạy lại.
   */
  const runGuarded = useCallback(async (targetAction: string, action: () => Promise<void>) => {
    setActionError(null);
    setNotice(null);
    try {
      await action();
    } catch (error) {
      if (errorCodeOf(error) === "DSAR_IDENTITY_VERIFICATION_REQUIRED") {
        pendingActionRef.current = action;
        setStepUpAction(targetAction);
        setStepUpOpen(true);
        return;
      }
      throw error;
    }
  }, []);

  const consentStatusByCode = useMemo(() => {
    const map = new Map<string, ConsentStatus>();
    for (const state of consents.data ?? []) map.set(state.purposeCode, state.status);
    return map;
  }, [consents.data]);

  const openRequest = useCallback(
    (type: DsarRequestView["requestType"]) =>
      (requests.data ?? []).find((item) => item.requestType === type && OPEN_DSAR_STATUSES.has(item.status)) ?? null,
    [requests.data],
  );

  const openErase = openRequest("ERASE");
  const openRestrict = openRequest("RESTRICT");
  const latestExport = (requests.data ?? []).find((item) => item.requestType === "ACCESS_EXPORT") ?? null;
  const exportStatus = useExportStatus(latestExport?.publicRef ?? null);

  const eraseDeadline = openErase
    ? new Date(new Date(openErase.receivedAt).getTime() + DELETION_GRACE_DAYS * 86_400_000)
    : null;

  const consentErrorMessage = (() => {
    if (!recordConsents.isError) return null;
    switch (errorCodeOf(recordConsents.error)) {
      case "CONSENT_MANDATORY_CANNOT_WITHDRAW":
        return t("privacyCenter.consent.mandatoryError");
      case "CONSENT_PURPOSE_UNKNOWN":
        return t("privacyCenter.consent.unknownPurposeError");
      default:
        return t("privacyCenter.consent.saveError");
    }
  })();

  const handleActionError = useCallback(
    (error: unknown) => {
      switch (errorCodeOf(error)) {
        case "DSAR_EXPORT_RATE_LIMITED":
          setActionError(t("privacyCenter.export.rateLimited"));
          break;
        case "DSAR_EXPORT_NOT_READY":
          setActionError(t("privacyCenter.export.notReady"));
          break;
        case "DSAR_EXPORT_EXPIRED":
        case "DSAR_EXPORT_ALREADY_DOWNLOADED":
          setActionError(t("privacyCenter.export.gone"));
          break;
        case "DELETION_ALREADY_REQUESTED":
          setActionError(t("privacyCenter.deletion.alreadyRequested"));
          break;
        case "DELETION_GRACE_EXPIRED":
          setActionError(t("privacyCenter.deletionBanner.expired"));
          break;
        case "DELETION_NOT_REQUESTED":
          setActionError(t("privacyCenter.deletionBanner.notRequested"));
          break;
        case "RESTRICTION_ALREADY_ACTIVE":
          setActionError(t("privacyCenter.restriction.alreadyActive"));
          break;
        case "RATE_LIMITED":
          setActionError(t("privacyCenter.errors.rateLimited"));
          break;
        default:
          setActionError(t("privacyCenter.errors.generic"));
      }
    },
    [t],
  );

  /** Bọc một thao tác: chạy có step-up, bắt lỗi về một chỗ, đặt thông báo thành công. */
  const dispatch = useCallback(
    (targetAction: string, action: () => Promise<void>) => {
      void runGuarded(targetAction, action).catch(handleActionError);
    },
    [handleActionError, runGuarded],
  );

  const visibleHistory = showAllHistory ? (history.data ?? []) : (history.data ?? []).slice(0, 8);

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("privacyCenter.title")}</h1>
        <p className="pt-1 text-body text-text-secondary">{t("privacyCenter.intro")}</p>
      </header>

      {/* ---------- Băng cảnh báo: tài khoản đang trong ân hạn xoá (C9/C10) ---------- */}
      {openErase && eraseDeadline ? (
        <section className="flex flex-col gap-3 rounded-2xl border border-danger bg-danger-bg p-5">
          <h2 className="flex items-center gap-2 text-h3 font-bold text-danger-text">
            <AlertTriangle size={18} aria-hidden="true" />
            {t("privacyCenter.deletionBanner.title")}
          </h2>
          <p className="text-caption text-danger-text">
            {t("privacyCenter.deletionBanner.body", {
              publicRef: openErase.publicRef,
              receivedAt: formatDateTime(openErase.receivedAt),
              scheduledAt: formatDate(eraseDeadline),
            })}
          </p>
          <p className="text-caption text-danger-text">
            {t("privacyCenter.deletionBanner.graceNote", { days: daysUntil(eraseDeadline) })}
          </p>
          <Button
            type="button"
            variant="secondary"
            size="md"
            className="self-start"
            loading={cancelDeletion.isPending}
            onClick={() => {
              dispatch("CANCEL_ACCOUNT_ERASE", async () => {
                await cancelDeletion.mutateAsync();
                setNotice(t("privacyCenter.deletionBanner.cancelled"));
              });
            }}
          >
            {t("privacyCenter.deletionBanner.cancel")}
          </Button>
          <p className="text-small text-danger-text">{t("privacyCenter.deletionBanner.derivedNote")}</p>
        </section>
      ) : null}

      {notice === null ? null : (
        <p
          role="status"
          className="flex items-center gap-2 rounded-lg bg-success-bg p-3 text-caption text-success-text"
        >
          <Check size={16} aria-hidden="true" />
          {notice}
        </p>
      )}
      {actionError === null ? null : (
        <div className="rounded-lg bg-danger-bg p-3">
          <InlineError message={actionError} />
        </div>
      )}

      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-5">
          {/* ---------- Mục đích xử lý dữ liệu (C1 + C2 + C3) ---------- */}
          <section className={CARD}>
            <h2 className={SECTION_TITLE}>
              <ShieldCheck size={18} className="text-primary-dark" aria-hidden="true" />
              {t("privacyCenter.consent.title")}
            </h2>
            <p className="text-caption text-text-secondary">{t("privacyCenter.consent.intro")}</p>
            <p className="text-caption text-text-tertiary">{t("privacyCenter.consent.sourceNote")}</p>
            <p className="rounded-lg bg-chip-bg p-3 text-caption text-text-secondary">
              {t("privacyCenter.consent.appendOnlyNote")}
            </p>

            {purposes.isPending || consents.isPending ? (
              <SkeletonLoader className="h-40 w-full" />
            ) : purposes.isError || consents.isError ? (
              <InlineError message={t("privacyCenter.loadError")} />
            ) : purposes.data.length === 0 ? (
              <p className="text-caption text-text-tertiary">{t("privacyCenter.consent.empty")}</p>
            ) : (
              <ul className="flex flex-col">
                {purposes.data.map((purpose) => (
                  <PurposeRow
                    key={purpose.code}
                    purpose={purpose}
                    status={consentStatusByCode.get(purpose.code) ?? "NONE"}
                    pending={recordConsents.isPending && recordConsents.variables[0]?.purposeCode === purpose.code}
                    disabled={recordConsents.isPending}
                    onChange={(granted) => {
                      setActionError(null);
                      setNotice(null);
                      recordConsents.mutate([{ purposeCode: purpose.code, granted }]);
                    }}
                  />
                ))}
              </ul>
            )}
            {consentErrorMessage === null ? null : <InlineError message={consentErrorMessage} />}

            <MissingApiNote title={t("privacyCenter.consent.evidenceTitle")}>
              {t("privacyCenter.consent.evidenceMissing")}
            </MissingApiNote>
          </section>

          {/* ---------- Lịch sử đồng ý (C4) ---------- */}
          <section className={CARD}>
            <h2 className={SECTION_TITLE}>
              <History size={18} className="text-primary-dark" aria-hidden="true" />
              {t("privacyCenter.history.title")}
            </h2>
            <p className="text-caption text-text-secondary">{t("privacyCenter.history.intro")}</p>

            {history.isPending ? (
              <SkeletonLoader className="h-24 w-full" />
            ) : history.isError ? (
              <InlineError message={t("privacyCenter.loadError")} />
            ) : visibleHistory.length === 0 ? (
              <p className="text-caption text-text-tertiary">{t("privacyCenter.history.empty")}</p>
            ) : (
              <>
                <ul className="flex flex-col">
                  {visibleHistory.map((entry, index) => (
                    <li
                      key={`${entry.purposeCode}-${entry.occurredAt}-${String(index)}`}
                      className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1 border-b border-border py-3 last:border-b-0 last:pb-0"
                    >
                      <div className="flex min-w-0 flex-col">
                        <span className="text-caption font-semibold text-text-primary">{entry.purposeCode}</span>
                        <span className="text-small text-text-tertiary">
                          {entry.uiSurface
                            ? t("privacyCenter.history.surfaceLabel", { surface: entry.uiSurface })
                            : entry.method}
                        </span>
                      </div>
                      <div className="flex flex-col items-end">
                        <span
                          className={cn(
                            "text-caption font-semibold",
                            entry.status === "GRANTED" ? "text-success-text" : "text-text-secondary",
                          )}
                        >
                          {t(`privacyCenter.consent.status.${entry.status}`)}
                        </span>
                        <span className="text-small text-text-tertiary">{formatDateTime(entry.occurredAt)}</span>
                        {entry.policyVersion === null ? (
                          <span className="text-small text-text-tertiary">
                            {t("privacyCenter.history.policyUnknown")}
                          </span>
                        ) : (
                          <Link
                            to={`/legal/privacy/v/${entry.policyVersion}`}
                            className="text-small font-semibold text-primary hover:underline"
                          >
                            {t("privacyCenter.history.policyLink", { version: entry.policyVersion })}
                          </Link>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
                {history.data.length > 8 ? (
                  <Button
                    type="button"
                    variant="tertiary"
                    size="sm"
                    className="self-start"
                    onClick={() => {
                      setShowAllHistory((value) => !value);
                    }}
                  >
                    {showAllHistory ? t("privacyCenter.history.showLess") : t("privacyCenter.history.showAll")}
                  </Button>
                ) : null}
              </>
            )}
          </section>

          {/* ---------- Dữ liệu CatCheck đang giữ về bạn (C5) ---------- */}
          <section className={CARD}>
            <h2 className={SECTION_TITLE}>
              <Database size={18} className="text-primary-dark" aria-hidden="true" />
              {t("privacyCenter.inventory.title")}
            </h2>
            <p className="text-caption text-text-secondary">{t("privacyCenter.inventory.intro")}</p>

            {inventory.isPending ? (
              <SkeletonLoader className="h-32 w-full" />
            ) : inventory.isError ? (
              <InlineError message={t("privacyCenter.loadError")} />
            ) : inventory.data.length === 0 ? (
              <p className="text-caption text-text-tertiary">{t("privacyCenter.inventory.empty")}</p>
            ) : (
              <ul className="flex flex-col">
                {inventory.data.map((item) => (
                  <InventoryRow key={item.code} item={item} />
                ))}
              </ul>
            )}

            <MissingApiNote title={t("privacyCenter.inventory.labelRetention")}>
              {t("privacyCenter.inventory.retentionNote")}
            </MissingApiNote>
          </section>

          {/* ---------- Xuất dữ liệu (C6 + C7 + C8) ---------- */}
          <section className={CARD}>
            <h2 className={SECTION_TITLE}>
              <Download size={18} className="text-primary-dark" aria-hidden="true" />
              {t("privacyCenter.export.title")}
            </h2>
            <p className="text-caption text-text-secondary">{t("privacyCenter.export.intro")}</p>
            <p className="text-caption text-text-tertiary">{t("privacyCenter.export.sla")}</p>
            <p className="text-caption text-text-tertiary">{t("privacyCenter.export.linkRule")}</p>

            <Button
              type="button"
              size="md"
              className="self-start"
              loading={createExport.isPending}
              onClick={() => {
                dispatch("DATA_EXPORT", async () => {
                  const created = await createExport.mutateAsync();
                  setNotice(t("privacyCenter.export.created", { publicRef: created.publicRef }));
                });
              }}
            >
              {t("privacyCenter.export.cta")}
            </Button>

            {latestExport === null ? (
              <p className="text-caption text-text-tertiary">{t("privacyCenter.export.none")}</p>
            ) : (
              <div className="flex flex-col gap-2 rounded-lg bg-background-alt p-4">
                <p className="text-caption font-semibold text-text-primary">{t("privacyCenter.export.latestTitle")}</p>
                <p className="text-caption text-text-secondary">
                  {latestExport.publicRef} — {t(`privacyCenter.requests.status.${latestExport.status}`)}
                </p>
                <p className="text-small text-text-tertiary">
                  {t("privacyCenter.requests.fulfilDue", { date: formatDateTime(latestExport.fulfilDueAt) })}
                </p>
                {exportStatus.data?.resultExpiresAt == null ? null : (
                  <p className="text-small text-text-tertiary">
                    {t("privacyCenter.export.expiresAt", {
                      date: formatDateTime(exportStatus.data.resultExpiresAt),
                    })}
                  </p>
                )}
                <Button
                  type="button"
                  variant="tertiary"
                  size="sm"
                  className="self-start"
                  loading={downloadExport.isPending}
                  onClick={() => {
                    dispatch("DATA_EXPORT_DOWNLOAD", async () => {
                      await downloadExport.mutateAsync(latestExport.publicRef);
                    });
                  }}
                >
                  {t("privacyCenter.export.download")}
                </Button>
              </div>
            )}
          </section>

          {/* ---------- Tạm ngừng xử lý (C11 + C12) ---------- */}
          <section className={CARD}>
            <h2 className={SECTION_TITLE}>
              <PauseCircle size={18} className="text-primary-dark" aria-hidden="true" />
              {t("privacyCenter.restriction.title")}
            </h2>
            <p className="text-caption text-text-secondary">{t("privacyCenter.restriction.intro")}</p>
            <p className="text-caption text-text-tertiary">{t("privacyCenter.restriction.sla")}</p>

            <div className="flex flex-wrap items-center gap-3">
              <Badge className={openRestrict ? "bg-warning-bg text-warning-text" : undefined}>
                {openRestrict
                  ? t("privacyCenter.restriction.stateActive")
                  : t("privacyCenter.restriction.stateInactive")}
              </Badge>
              <Button
                type="button"
                variant="tertiary"
                size="sm"
                loading={setRestriction.isPending}
                onClick={() => {
                  dispatch("PROCESSING_RESTRICTION", async () => {
                    await setRestriction.mutateAsync(openRestrict === null);
                  });
                }}
              >
                {openRestrict ? t("privacyCenter.restriction.disable") : t("privacyCenter.restriction.enable")}
              </Button>
            </div>

            <MissingApiNote title={t("privacyCenter.restriction.title")}>
              {t("privacyCenter.restriction.derivedNote")}
            </MissingApiNote>
          </section>

          {/* ---------- Yêu cầu khác (C13 + C14) ---------- */}
          <section className={CARD}>
            <h2 className={SECTION_TITLE}>
              <FileText size={18} className="text-primary-dark" aria-hidden="true" />
              {t("privacyCenter.requests.title")}
            </h2>
            <p className="text-caption text-text-secondary">{t("privacyCenter.requests.intro")}</p>
            <p className="text-caption text-text-tertiary">{t("privacyCenter.requests.noContentField")}</p>

            <ul className="flex flex-col gap-2">
              {MANUAL_DSAR_TYPES.map((type) => (
                <li key={type} className="flex flex-wrap items-center justify-between gap-3">
                  <span className="text-caption text-text-secondary">{t(`privacyCenter.requests.type.${type}`)}</span>
                  <Button
                    type="button"
                    variant="tertiary"
                    size="sm"
                    loading={createDsarRequest.isPending && createDsarRequest.variables === type}
                    onClick={() => {
                      dispatch(`DSAR_${type}`, async () => {
                        const created = await createDsarRequest.mutateAsync(type);
                        setNotice(t("privacyCenter.requests.created", { publicRef: created.publicRef }));
                      });
                    }}
                  >
                    {t("privacyCenter.requests.open")}
                  </Button>
                </li>
              ))}
            </ul>

            <h3 className="pt-2 text-caption font-semibold text-text-primary">
              {t("privacyCenter.requests.listTitle")}
            </h3>
            {requests.isPending ? (
              <SkeletonLoader className="h-20 w-full" />
            ) : requests.isError ? (
              <InlineError message={t("privacyCenter.loadError")} />
            ) : requests.data.length === 0 ? (
              <p className="text-caption text-text-tertiary">{t("privacyCenter.requests.listEmpty")}</p>
            ) : (
              <ul className="flex flex-col">
                {requests.data.map((item) => (
                  <li
                    key={item.publicRef}
                    className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1 border-b border-border py-3 last:border-b-0 last:pb-0"
                  >
                    <div className="flex min-w-0 flex-col">
                      <span className="text-caption font-semibold text-text-primary">
                        {t(`privacyCenter.requests.type.${item.requestType}`)}
                      </span>
                      <span className="text-small text-text-tertiary">{item.publicRef}</span>
                    </div>
                    <div className="flex flex-col items-end">
                      <span className="text-caption text-text-secondary">
                        {t(`privacyCenter.requests.status.${item.status}`)}
                      </span>
                      <span className="text-small text-text-tertiary">
                        {t("privacyCenter.requests.ackDue", { date: formatDateTime(item.ackDueAt) })}
                      </span>
                      <span className="text-small text-text-tertiary">
                        {t("privacyCenter.requests.fulfilDue", { date: formatDateTime(item.fulfilDueAt) })}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* ---------- Xoá tài khoản (C9) ---------- */}
          <section className={cn(CARD, "border border-danger")}>
            <h2 className="flex items-center gap-2 text-h3 font-bold text-danger-text">
              <Trash2 size={18} aria-hidden="true" />
              {t("privacyCenter.deletion.title")}
            </h2>
            <p className="text-caption text-text-secondary">{t("privacyCenter.deletion.intro")}</p>

            <div className="flex flex-col gap-1">
              <h3 className="text-caption font-semibold text-text-primary">
                {t("privacyCenter.deletion.immediateTitle")}
              </h3>
              <ul className="list-disc space-y-1 pl-5 text-caption text-text-secondary">
                <li>{t("privacyCenter.deletion.immediate1")}</li>
                <li>{t("privacyCenter.deletion.immediate2")}</li>
                <li>{t("privacyCenter.deletion.immediate3")}</li>
              </ul>
            </div>

            <div className="flex flex-col gap-1">
              <h3 className="text-caption font-semibold text-text-primary">{t("privacyCenter.deletion.lostTitle")}</h3>
              <ul className="list-disc space-y-1 pl-5 text-caption text-text-secondary">
                <li>{t("privacyCenter.deletion.lost1")}</li>
                <li>{t("privacyCenter.deletion.lost2")}</li>
                <li>{t("privacyCenter.deletion.lost3")}</li>
                <li>{t("privacyCenter.deletion.lost4")}</li>
              </ul>
            </div>

            <div className="flex flex-col gap-1">
              <h3 className="text-caption font-semibold text-text-primary">{t("privacyCenter.deletion.keptTitle")}</h3>
              <ul className="list-disc space-y-1 pl-5 text-caption text-text-secondary">
                <li>{t("privacyCenter.deletion.kept1")}</li>
                <li>{t("privacyCenter.deletion.kept2")}</li>
                <li>{t("privacyCenter.deletion.kept3")}</li>
                <li>{t("privacyCenter.deletion.kept4")}</li>
              </ul>
            </div>

            <p className="rounded-lg bg-background-alt p-3 text-caption text-text-secondary">
              {t("privacyCenter.deletion.backupNote")}
            </p>
            <p className="rounded-lg bg-chip-bg p-3 text-caption text-text-secondary">
              <span className="font-semibold">{t("privacyCenter.deletion.exportFirstTitle")}</span>{" "}
              {t("privacyCenter.deletion.exportFirst")}
            </p>

            <Button
              type="button"
              variant="tertiary"
              size="md"
              className="self-start border-danger text-danger hover:bg-danger-bg"
              disabled={openErase !== null}
              onClick={() => {
                setDeleteEmail("");
                setActionError(null);
                setDeleteDialogOpen(true);
              }}
            >
              {t("privacyCenter.deletion.cta")}
            </Button>
            {openErase === null ? null : (
              <p className="text-caption text-text-tertiary">{t("privacyCenter.deletion.alreadyRequested")}</p>
            )}

            <MissingApiNote title={t("privacyCenter.deletion.title")}>
              {t("privacyCenter.deletion.optionsMissing")}
            </MissingApiNote>
          </section>
        </div>

        {/* ---------- Cột phụ 360px ---------- */}
        <aside className="flex flex-col gap-4 lg:sticky lg:top-6 lg:w-[360px] lg:shrink-0">
          <div className={ASIDE_CARD}>
            <p className="flex items-center gap-2 text-caption font-semibold text-text-primary">
              <Clock size={16} className="text-primary-dark" aria-hidden="true" />
              {t("privacyCenter.aside.slaTitle")}
            </p>
            <ul className="list-disc space-y-1 pl-5 text-caption text-text-secondary">
              <li>{t("privacyCenter.aside.slaAck")}</li>
              <li>{t("privacyCenter.aside.slaExport")}</li>
              <li>{t("privacyCenter.aside.slaErase")}</li>
              <li>{t("privacyCenter.aside.slaRestrict")}</li>
            </ul>
          </div>

          <div className={ASIDE_CARD}>
            <p className="text-caption font-semibold text-text-primary">{t("privacyCenter.aside.linksTitle")}</p>
            <Link to="/legal/privacy" className="text-caption font-semibold text-primary hover:underline">
              {t("privacyCenter.aside.linkPrivacy")}
            </Link>
            <Link to="/legal/data-requests" className="text-caption font-semibold text-primary hover:underline">
              {t("privacyCenter.aside.linkDataRequests")}
            </Link>
            <Link to="/legal/contact" className="text-caption font-semibold text-primary hover:underline">
              {t("privacyCenter.aside.linkContact")}
            </Link>
          </div>

          <div className={ASIDE_CARD}>
            <p className="flex items-center gap-2 text-caption font-semibold text-text-primary">
              <Info size={16} className="text-text-tertiary" aria-hidden="true" />
              {t("privacyCenter.aside.gapsTitle")}
            </p>
            <ul className="list-disc space-y-1 pl-5 text-caption text-text-secondary">
              <li>{t("privacyCenter.aside.gapEvidence")}</li>
              <li>{t("privacyCenter.aside.gapRetention")}</li>
              <li>{t("privacyCenter.aside.gapRestrictionState")}</li>
              <li>{t("privacyCenter.aside.gapCommunityChoice")}</li>
            </ul>
          </div>
        </aside>
      </div>

      {/* ---------- Hộp thoại xác nhận xoá ---------- */}
      <Dialog
        open={deleteDialogOpen}
        onOpenChange={(next) => {
          setDeleteDialogOpen(next);
        }}
      >
        <DialogContent className="flex flex-col gap-4">
          <DialogTitle className="flex items-center gap-2 font-bold text-danger-text">
            <Trash2 size={18} aria-hidden="true" />
            {t("privacyCenter.deletion.confirmTitle")}
          </DialogTitle>
          <DialogDescription>{t("privacyCenter.deletion.immediate1")}</DialogDescription>
          <ul className="list-disc space-y-1 pl-5 text-caption text-text-secondary">
            <li>{t("privacyCenter.deletion.lost1")}</li>
            <li>{t("privacyCenter.deletion.lost2")}</li>
            <li>{t("privacyCenter.deletion.lost3")}</li>
          </ul>
          <Input
            label={t("privacyCenter.deletion.confirmEmailLabel")}
            type="email"
            autoComplete="off"
            value={deleteEmail}
            onChange={(event) => {
              setDeleteEmail(event.target.value);
            }}
          />
          {deleteEmail.length > 0 && deleteEmail.trim().toLowerCase() !== (sessionEmail ?? "").toLowerCase() ? (
            <InlineError message={t("privacyCenter.deletion.confirmEmailMismatch")} />
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              size="md"
              className="bg-danger hover:bg-danger-text"
              disabled={sessionEmail === null || deleteEmail.trim().toLowerCase() !== sessionEmail.toLowerCase()}
              loading={requestDeletion.isPending}
              onClick={() => {
                setDeleteDialogOpen(false);
                dispatch("ACCOUNT_ERASE", async () => {
                  const created = await requestDeletion.mutateAsync();
                  setNotice(t("privacyCenter.deletion.requested", { publicRef: created.publicRef }));
                });
              }}
            >
              {t("privacyCenter.deletion.confirmCta")}
            </Button>
            <Button
              type="button"
              variant="tertiary"
              size="md"
              onClick={() => {
                setDeleteDialogOpen(false);
              }}
            >
              {t("privacyCenter.deletion.cancelCta")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <StepUpDialog
        open={stepUpOpen}
        email={sessionEmail}
        targetAction={stepUpAction}
        onClose={() => {
          pendingActionRef.current = null;
          setStepUpOpen(false);
        }}
        onVerified={() => {
          setStepUpOpen(false);
          const action = pendingActionRef.current;
          pendingActionRef.current = null;
          if (action) {
            void action().catch(handleActionError);
          }
        }}
      />
    </div>
  );
}
