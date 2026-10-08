import { Fragment, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Camera, Check, ChevronDown, ChevronLeft, Search, Trash2 } from "lucide-react";
import { Button, Dialog, DialogContent, DialogTitle, DialogTrigger, Input } from "@/shared/ui";
import { LogoPawIcon } from "@/shared/assets/icons/AppIcons";
import { cn } from "@/shared/lib/cn";
import { ONBOARDING_STEPS, type Breed, type OnboardingStep, type PhBand } from "./types";
import { formatActivationCode } from "./schemas";

/**
 * Component dùng chung cho luồng onboarding — gom trong 1 file root vì config
 * boundaries (eslint.config.js) thiếu policy feature→feature nên import giữa các file
 * cùng feature bị chặn. W3 sửa config thì tách lại thư mục components/ (xem
 * docs/handovers/A7.md).
 */

/* ---------------- OnboardingStepper ---------------- */

const STEP_I18N_KEYS: Record<OnboardingStep, string> = {
  1: "cat",
  2: "healthSurvey",
  3: "disclaimer",
  4: "activate",
  5: "success",
};

interface OnboardingStepperProps {
  current: OnboardingStep;
  onStepClick?: (step: OnboardingStep) => void;
}

export function OnboardingStepper({ current, onStepClick }: OnboardingStepperProps) {
  const { t } = useTranslation("onboarding");
  const total = ONBOARDING_STEPS.length;
  const progressPercent = Math.round((current / total) * 100);
  const currentLabel = t(`stepper.steps.${STEP_I18N_KEYS[current]}`);

  return (
    // Desktop (W1 Web-01c-3/4/5): dải bước nằm trong một thẻ trắng riêng phía trên nội dung.
    <div className="w-full lg:rounded-2xl lg:border lg:border-border lg:bg-surface lg:p-5 lg:shadow-brand-md">
      <div className="mb-2 flex items-center justify-between gap-3">
        {/* M1 01c-3/4: nhãn bước là chữ đậm màu primary-dark kèm icon chân mèo, kèm tên bước
            hiện tại khi chưa có hàng tròn bên dưới (dưới md). */}
        <p className="flex min-w-0 items-center gap-2 text-caption font-bold text-primary-dark">
          <LogoPawIcon size={16} />
          <span className="truncate">
            {t("stepper.label", { current, total })}
            <span className="md:hidden">{` · ${currentLabel}`}</span>
          </span>
        </p>
        <p className="shrink-0 text-caption font-bold text-primary-dark" aria-hidden="true">
          {progressPercent}%
        </p>
      </div>

      <div
        className="h-1.5 w-full overflow-hidden rounded-full bg-chip-bg"
        role="progressbar"
        aria-valuenow={current}
        aria-valuemin={1}
        aria-valuemax={total}
        aria-label={t("stepper.label", { current, total })}
      >
        <div
          className="h-full rounded-full bg-primary-dark transition-[width] duration-300 ease-standard"
          style={{ width: `${String(progressPercent)}%` }}
        />
      </div>

      <ol className="mt-4 hidden items-start md:flex">
        {ONBOARDING_STEPS.map((step, index) => {
          const label = t(`stepper.steps.${STEP_I18N_KEYS[step]}`);
          const isDone = step < current;
          const isCurrent = step === current;
          const isClickable = isDone && onStepClick;
          return (
            <Fragment key={step}>
              {/* `min-w-20` + `whitespace-nowrap`: nhãn "Miễn trừ y tế"/"Kích hoạt gói" không bị
                  bẻ thành 2 dòng lệch khi cột chỉ rộng 80px. */}
              <li className="flex min-w-20 shrink-0 flex-col items-center gap-1.5">
                <button
                  type="button"
                  disabled={!isClickable}
                  onClick={() => onStepClick?.(step)}
                  aria-current={isCurrent ? "step" : undefined}
                  className={cn(
                    "flex min-h-11 w-full flex-col items-center gap-1.5 rounded-md py-1",
                    isClickable &&
                      "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
                  )}
                >
                  <span
                    className={cn(
                      "flex size-8 items-center justify-center rounded-full border-2 text-caption font-bold transition-colors",
                      isDone && "border-primary bg-primary text-white",
                      isCurrent && "border-primary bg-chip-bg text-primary",
                      !isDone && !isCurrent && "border-border bg-surface text-text-tertiary",
                    )}
                  >
                    {isDone ? <Check className="size-4" aria-hidden="true" /> : step}
                  </span>
                  <span
                    className={cn(
                      "whitespace-nowrap px-1 text-center text-small font-medium",
                      isCurrent ? "text-text-primary" : isDone ? "text-text-secondary" : "text-text-tertiary",
                    )}
                  >
                    {label}
                  </span>
                </button>
              </li>
              {index < total - 1 ? (
                <li aria-hidden="true" className="mx-1 mt-4 h-0.5 min-w-4 flex-1">
                  <span
                    className={cn("block h-full w-full rounded-full", step < current ? "bg-primary" : "bg-border")}
                  />
                </li>
              ) : null}
            </Fragment>
          );
        })}
      </ol>
    </div>
  );
}

/* ---------------- OnboardingShell ---------------- */

interface OnboardingShellProps {
  step: OnboardingStep;
  title: string;
  subtitle?: string;
  onBack?: () => void;
  onStepClick?: (step: OnboardingStep) => void;
  children: ReactNode;
  footer: ReactNode;
  /**
   * Khối full-width chen giữa header và vùng 2 cột (hero màn Hoàn tất, W1 Web-01c-5).
   * Hiện ở MỌI breakpoint — mobile vẫn xếp dọc như cũ.
   */
  hero?: ReactNode;
  /**
   * Cột phải của bản web 1280px (W1 Web-01c-3/4/5 — "2 cột: form + panel preview").
   * CHỈ render từ `lg` trở lên; mobile không dựng DOM này nên bố cục 1 cột giữ nguyên.
   */
  aside?: ReactNode;
  /**
   * Desktop (W1 Web-01c-3/4): cột trái là một THẺ TRẮNG chứa cả tiêu đề + form + CTA,
   * không phải nội dung trần trên nền trang. Mobile không đổi (thẻ chỉ bật từ `lg`).
   */
  panel?: boolean;
  /**
   * Ẩn tiêu đề trang khỏi phần nhìn thấy (vẫn giữ `h1` cho screen reader) — màn Hoàn tất
   * dùng hero làm tiêu đề, thiết kế không có dòng tiêu đề thứ hai (M1 01d, W1 Web-01c-5).
   */
  titleHidden?: boolean;
}

export function OnboardingShell({
  step,
  title,
  subtitle,
  onBack,
  onStepClick,
  children,
  footer,
  hero,
  aside,
  panel,
  titleHidden,
}: OnboardingShellProps) {
  const { t } = useTranslation(["onboarding", "common"]);

  const header = titleHidden ? (
    <h1 className="sr-only">{title}</h1>
  ) : (
    <header className="flex items-start gap-3">
      {onBack ? (
        <Button
          type="button"
          variant="tertiary"
          size="sm"
          onClick={onBack}
          aria-label={t("actions.back", { ns: "common" })}
          leftIcon={<ChevronLeft className="size-5" aria-hidden="true" />}
          className="mt-0.5 min-h-11 min-w-11 shrink-0 px-0"
        >
          <span className="sr-only">{t("actions.back", { ns: "common" })}</span>
        </Button>
      ) : null}
      <div>
        <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{title}</h1>
        {subtitle ? <p className="mt-1 text-body text-text-secondary">{subtitle}</p> : null}
      </div>
    </header>
  );

  return (
    <div
      className={cn(
        "mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6",
        // Bản web rộng 1280px: nới khung để 2 cột 7/5 có chỗ (OnboardingLayout đã có px-4).
        aside && "lg:max-w-6xl",
      )}
    >
      <OnboardingStepper current={step} onStepClick={onStepClick} />

      {panel ? null : header}

      {hero}

      <div className={cn("flex flex-1 flex-col gap-6", aside && "lg:flex-row lg:items-start lg:gap-8")}>
        <div
          className={cn(
            "flex min-w-0 flex-1 flex-col gap-6",
            aside && "lg:basis-7/12",
            panel && "lg:rounded-3xl lg:border lg:border-border lg:bg-surface lg:p-8 lg:shadow-brand-md",
          )}
        >
          {panel ? header : null}

          <div className="flex flex-1 flex-col gap-6 lg:flex-none">{children}</div>

          {/* Desktop: CTA nằm ngay dưới nội dung (Figma Web-01c-3/4, DSL Web-01c-4a/4b: nút
              theo sau ô nhập với pt 8) — KHÔNG dính đáy màn. Thanh dính + `mt-auto` trên màn
              1440 đẩy nút xuống đáy, để lại khoảng trống ~300px giữa ô nhập mã và nút. */}
          <footer className="sticky bottom-0 mt-auto border-t border-border bg-background py-4 lg:static lg:mt-0 lg:border-0 lg:bg-transparent lg:pb-0 lg:pt-2">
            {footer}
          </footer>
        </div>

        {aside ? (
          // Dính theo cuộn: cột trái (form khảo sát) dài gấp 2–3 lần cột phải.
          <aside className="hidden lg:sticky lg:top-6 lg:flex lg:basis-5/12 lg:shrink-0 lg:flex-col lg:gap-5">
            {aside}
          </aside>
        ) : null}
      </div>
    </div>
  );
}

/* ---------------- PhBandBar ---------------- */

/** Màu từng dải pH theo `severity` của `GET /reference/ph-bands` (token `--color-ph-*`). */
const PH_SEVERITY_BAR: Record<PhBand["severity"], string> = {
  NORMAL: "bg-[var(--color-ph-normal)]",
  ATTENTION: "bg-[var(--color-ph-mild)]",
  WATCH: "bg-[var(--color-ph-abnormal)]",
  NEUTRAL: "bg-[var(--color-ph-unknown)]",
};

/**
 * Thanh dải pH tham chiếu (M1 01d, W1 Web-01c-3/4/5) — một segment cho mỗi band trả về,
 * KHÔNG hard-code ngưỡng. Rỗng khi API chưa trả dữ liệu.
 */
export function PhBandBar({ bands, className }: { bands: PhBand[]; className?: string }) {
  // Dải không có biên nào (INCONCLUSIVE — "chưa đủ dữ liệu") là một trạng thái, không phải một
  // khoảng pH: vẽ nó thành đoạn xám cuối thang khiến thang trông như có vùng pH "không rõ".
  const ranged = bands.filter((b) => typeof b.phMin === "number" || typeof b.phMax === "number");
  if (ranged.length === 0) return null;
  return (
    <div className={cn("flex h-2 overflow-hidden rounded-full", className)} aria-hidden="true">
      {ranged.map((band) => (
        <span key={band.code} className={cn("h-full flex-1", PH_SEVERITY_BAR[band.severity])} />
      ))}
    </div>
  );
}

/* ---------------- OptionCard + QuestionField ---------------- */

interface OptionCardProps {
  name: string;
  value: string;
  checked: boolean;
  onChange: (value: string) => void;
  type?: "radio" | "checkbox";
  title: string;
  description?: string;
  badge?: string;
  disabled?: boolean;
  /** Ảnh minh hoạ bên trái (vd 3 loại cát vệ sinh, M1 01c-4) — khớp thumbnail thật của Figma. */
  imageUrl?: string;
  /** Icon nhỏ bên trái khi Figma dùng icon thay vì ảnh (vd tiền sử bệnh, loại thức ăn). */
  icon?: ReactNode;
  /** Chú thích phụ căn phải trong hàng tiêu đề (M1 01c-4 câu 2: "Cần giám sát nhạy"…). */
  meta?: string;
  /**
   * `row` (mặc định) — icon/ảnh bên trái, nội dung bên phải.
   * `stack` — icon ở trên, tiêu đề + mô tả bên dưới (M1 01c-4 câu "Chế độ ăn").
   */
  layout?: "row" | "stack";
}

/**
 * Thẻ chọn 1/nhiều lựa chọn — khớp Figma (CatCheck-Demo 26mOVF2zdu4cI1EPz2Syxw, M1 01c-3/4):
 * lựa chọn ĐANG CHỌN tô nền đặc `primary-dark` + chữ trắng (không phải viền + tint nhạt như
 * trước) — nền đặc tự thân đã là tín hiệu chọn nên ẩn luôn chấm radio/checkbox phụ.
 */
export function OptionCard({
  name,
  value,
  checked,
  onChange,
  type = "radio",
  title,
  description,
  badge,
  disabled,
  imageUrl,
  icon,
  meta,
  layout = "row",
}: OptionCardProps) {
  // Figma W1 Web-01c-4: lựa chọn KHÔNG có ảnh/icon vẫn có ô chọn (tròn cho radio, vuông cho
  // checkbox) ở mép trái — thiếu nó thẻ trông như một ô chữ trơn, không ra "đang chọn được".
  const hasVisual = Boolean(imageUrl ?? icon);
  const leading = imageUrl ? (
    <img src={imageUrl} alt="" className="size-11 shrink-0 rounded-lg object-cover" />
  ) : icon ? (
    <span
      className={cn(
        "flex size-11 shrink-0 items-center justify-center rounded-lg",
        checked ? "bg-white/15 text-white" : "bg-info text-primary-dark",
      )}
    >
      {icon}
    </span>
  ) : (
    <span
      aria-hidden="true"
      className={cn(
        "mt-0.5 flex size-5 shrink-0 items-center justify-center border-2 transition-colors",
        type === "radio" ? "rounded-full" : "rounded-md",
        checked ? "border-white" : "border-border-strong",
      )}
    >
      {checked ? (
        type === "radio" ? (
          <span className="size-2.5 rounded-full bg-white" />
        ) : (
          <Check className="size-3 text-white" strokeWidth={3} />
        )
      ) : null}
    </span>
  );

  return (
    <label
      className={cn(
        "relative flex min-h-11 cursor-pointer rounded-xl border p-4 shadow-xs transition-colors",
        layout === "stack" ? "flex-col gap-3" : "items-start gap-3",
        checked ? "border-primary-dark bg-primary-dark" : "border-border bg-surface hover:bg-background-alt",
        disabled && "cursor-not-allowed opacity-50",
      )}
    >
      <input
        type={type}
        name={name}
        value={value}
        checked={checked}
        disabled={disabled}
        onChange={() => {
          onChange(value);
        }}
        className="sr-only"
      />
      {leading}
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        {/* `flex-wrap`: huy hiệu "Khuyến dùng" xuống dòng khi chật, thay vì bóp tiêu đề vỡ 3 dòng. */}
        <span className="flex flex-wrap items-start gap-x-2 gap-y-1">
          <span className={cn("text-body font-semibold", checked ? "text-white" : "text-text-primary")}>{title}</span>
          {badge ? (
            <span className="mt-0.5 shrink-0 rounded-full bg-secondary px-2 py-0.5 text-overline font-semibold text-secondary-text-on">
              {badge}
            </span>
          ) : null}
          {meta !== undefined || (checked && hasVisual) ? (
            <span className="ml-auto flex items-start gap-2 pl-1">
              {meta ? (
                <span
                  className={cn("max-w-28 text-right text-small", checked ? "text-white/70" : "text-text-tertiary")}
                >
                  {meta}
                </span>
              ) : null}
              {checked && hasVisual ? (
                <Check className="mt-0.5 size-4 shrink-0 text-white" aria-hidden="true" strokeWidth={3} />
              ) : null}
            </span>
          ) : null}
        </span>
        {description ? (
          <span className={cn("text-caption", checked ? "text-white/80" : "text-text-secondary")}>{description}</span>
        ) : null}
      </span>
    </label>
  );
}

interface QuestionFieldProps {
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
  /** Số thứ tự câu hỏi (huy hiệu tròn bên trái nhãn) — khớp Figma M1 01c-4. */
  number?: number;
  /** Chú thích căn phải cùng hàng với nhãn ("Chọn 1", "Ảnh hưởng độ pH" — M1 01c-4). */
  meta?: string;
}

export function QuestionField({ label, hint, error, children, number, meta }: QuestionFieldProps) {
  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="sr-only">{label}</legend>
      <div className="flex flex-col gap-1">
        <div className="flex items-start justify-between gap-3">
          <p className="flex items-center gap-2 text-body font-semibold text-text-primary">
            {number ? (
              <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-info text-caption font-bold text-primary-dark">
                {number}
              </span>
            ) : null}
            {label}
          </p>
          {meta ? <p className="max-w-28 shrink-0 pt-0.5 text-right text-small text-text-tertiary">{meta}</p> : null}
        </div>
        {hint ? <p className="text-caption text-text-tertiary">{hint}</p> : null}
      </div>
      {children}
      {error ? (
        <p role="alert" className="text-small font-medium text-danger-text">
          {error}
        </p>
      ) : null}
    </fieldset>
  );
}

/* ---------------- FieldLabel ---------------- */

interface FieldLabelProps {
  children: ReactNode;
  /** Gắn nhãn vào input cụ thể; bỏ trống khi nhãn đứng trước một nhóm (fieldset). */
  htmlFor?: string;
  /** Dấu `*` đỏ sau nhãn — M1 01c-3 đánh dấu 3 trường bắt buộc (tên, giống, giới tính). */
  required?: boolean;
  /** Hành động căn phải cùng hàng nhãn ("Tất cả giống" — M1 01c-3). */
  action?: ReactNode;
}

/** Nhãn trường của luồng onboarding — có dấu bắt buộc + slot hành động bên phải. */
export function FieldLabel({ children, htmlFor, required, action }: FieldLabelProps) {
  const text = (
    <>
      {children}
      {required ? (
        <span className="ml-1 text-danger-text" aria-hidden="true">
          *
        </span>
      ) : null}
    </>
  );

  return (
    <div className="flex items-center justify-between gap-3">
      {htmlFor ? (
        <label htmlFor={htmlFor} className="text-caption font-semibold text-text-secondary">
          {text}
        </label>
      ) : (
        <span className="text-caption font-semibold text-text-secondary">{text}</span>
      )}
      {action}
    </div>
  );
}

/* ---------------- AvatarUpload ---------------- */

const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
const ACCEPTED_TYPES = ["image/jpeg", "image/png"];

interface AvatarUploadProps {
  file: File | null;
  previewUrl: string | null;
  onChange: (file: File | null) => void;
  error?: string;
}

export function AvatarUpload({ file, previewUrl, onChange, error }: AvatarUploadProps) {
  const { t } = useTranslation("onboarding");
  const inputRef = useRef<HTMLInputElement>(null);
  const [localError, setLocalError] = useState<string | null>(null);

  const handlePick = (picked: File | null) => {
    setLocalError(null);
    if (!picked) return;
    if (!ACCEPTED_TYPES.includes(picked.type)) {
      setLocalError(t("cat.avatar.invalidType"));
      return;
    }
    if (picked.size > MAX_FILE_SIZE_BYTES) {
      setLocalError(t("cat.avatar.tooLarge"));
      return;
    }
    onChange(picked);
  };

  const displayedError = error ?? localError;

  return (
    // Desktop (W1 Web-01c-3): khối upload nằm ngang trong panel nền nhạt thay vì cột giữa.
    <div className="flex flex-col items-center gap-2 lg:flex-row lg:gap-5 lg:rounded-2xl lg:bg-background-alt lg:p-5">
      <div className="relative shrink-0">
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          aria-label={file ? t("cat.avatar.change") : t("cat.avatar.add")}
          className={cn(
            "flex size-28 flex-col items-center justify-center gap-1 overflow-hidden rounded-full bg-chip-bg transition-colors",
            "shadow-[0_0_0_4px_var(--color-secondary-light)]",
            "hover:shadow-[0_0_0_4px_var(--color-secondary)] focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
            displayedError && "shadow-[0_0_0_4px_var(--color-danger)]",
          )}
        >
          {previewUrl ? (
            <img src={previewUrl} alt="" className="size-full object-cover" />
          ) : (
            <>
              <LogoPawIcon size={28} className="text-primary-dark" />
              <span className="text-overline font-semibold text-primary-dark">{t("cat.avatar.add")}</span>
            </>
          )}
        </button>
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          aria-label={file ? t("cat.avatar.change") : t("cat.avatar.add")}
          className="absolute bottom-0 right-0 flex size-9 items-center justify-center rounded-full border-2 border-surface bg-primary-dark text-white shadow-sm transition-colors focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
        >
          <Camera className="size-4" aria-hidden="true" />
        </button>
        {file ? (
          <button
            type="button"
            onClick={() => {
              onChange(null);
            }}
            aria-label={t("cat.avatar.remove")}
            className="absolute -left-1 -top-1 flex size-8 items-center justify-center rounded-full bg-surface text-text-secondary shadow-sm transition-colors hover:text-danger-text focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
          >
            <Trash2 className="size-3.5" aria-hidden="true" />
          </button>
        ) : null}
      </div>

      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png"
        className="hidden"
        onChange={(event) => {
          handlePick(event.target.files?.[0] ?? null);
          event.target.value = "";
        }}
      />

      <div className="flex flex-col items-center gap-1 lg:flex-1 lg:items-start">
        {/* Desktop (W1 Web-01c-3): nút tải ảnh hiện tường minh cạnh khung tròn. */}
        <Button
          type="button"
          variant="tertiary"
          size="sm"
          className="hidden lg:inline-flex"
          leftIcon={<Camera className="size-4" aria-hidden="true" />}
          onClick={() => inputRef.current?.click()}
        >
          {file ? t("cat.avatar.change") : t("cat.avatar.add")}
        </Button>
        <p className="text-caption text-text-tertiary lg:text-left">{t("cat.avatar.hint")}</p>
        {displayedError ? (
          <p role="alert" className="text-small font-medium text-danger-text">
            {displayedError}
          </p>
        ) : null}
      </div>
    </div>
  );
}

/* ---------------- BreedPicker ---------------- */

interface BreedPickerProps {
  breeds: Breed[] | undefined;
  loading: boolean;
  value: string;
  onChange: (breedCode: string) => void;
  error?: string;
}

/** Số giống hiện sẵn thành chip chọn nhanh (M1 01c-3: hàng chip cuộn ngang + "Tất cả giống"). */
const QUICK_BREED_COUNT = 6;

export function BreedPicker({ breeds, loading, value, onChange, error }: BreedPickerProps) {
  const { t } = useTranslation("onboarding");
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");

  const selected = breeds?.find((b) => b.code === value);

  const filtered = useMemo(() => {
    if (!breeds) return [];
    const q = query.trim().toLowerCase();
    if (!q) return breeds;
    return breeds.filter((b) => b.name.toLowerCase().includes(q));
  }, [breeds, query]);

  // Giống đang chọn luôn nằm trong hàng chip, kể cả khi nó được chọn từ hộp thoại đầy đủ.
  const quick = useMemo(() => {
    if (!breeds) return [];
    const head = breeds.slice(0, QUICK_BREED_COUNT);
    if (!selected || head.some((b) => b.code === selected.code)) return head;
    return [selected, ...head.slice(0, QUICK_BREED_COUNT - 1)];
  }, [breeds, selected]);

  return (
    <div className="flex flex-col gap-1.5">
      <Dialog open={open} onOpenChange={setOpen}>
        <FieldLabel
          required
          action={
            <DialogTrigger asChild>
              <button
                type="button"
                className="rounded-md px-1 text-caption font-semibold text-primary hover:underline focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
              >
                {t("cat.breed.all")}
              </button>
            </DialogTrigger>
          }
        >
          {t("cat.breed.label")}
        </FieldLabel>

        {loading ? <p className="py-2 text-caption text-text-tertiary">{t("cat.breed.loading")}</p> : null}

        {/* W1 Web-01c-3: trên desktop giống mèo là MỘT ô chọn cạnh ô tên, không phải hàng chip
            — hàng chip cuộn ngang bị thân thẻ cột trái cắt cụt, nhìn như lỗi tràn. */}
        {/* Mở hộp thoại qua `setOpen` chứ KHÔNG phải `DialogTrigger` thứ hai: Radix chỉ giữ một
            `triggerRef` (cái mount sau cùng) để trả focus lúc đóng — nút này ẩn ở mobile nên
            focus sẽ rơi về `body`. Trigger duy nhất là link "Tất cả giống" luôn hiển thị. */}
        {loading ? null : (
          <button
            type="button"
            onClick={() => {
              setOpen(true);
            }}
            className={cn(
              "h-12 w-full items-center justify-between gap-2 rounded-xl bg-surface px-4 text-body shadow-xs lg:border lg:border-border-strong lg:shadow-none focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
              selected ? "text-text-primary" : "text-text-tertiary",
              quick.length === 0 ? "flex" : "hidden lg:flex",
              error && "outline outline-2 outline-danger",
            )}
          >
            <span className="truncate">{selected?.name ?? t("cat.breed.placeholder")}</span>
            <ChevronDown className="size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
          </button>
        )}

        {loading || quick.length === 0 ? null : (
          <div
            role="group"
            aria-label={t("cat.breed.label")}
            className={cn(
              "-mx-1 flex gap-2 overflow-x-auto px-1 py-0.5 lg:hidden",
              error && "rounded-xl outline outline-2 outline-danger",
            )}
          >
            {quick.map((breed) => {
              const isSelected = breed.code === value;
              return (
                <button
                  key={breed.code}
                  type="button"
                  aria-pressed={isSelected}
                  onClick={() => {
                    onChange(breed.code);
                  }}
                  className={cn(
                    "flex min-h-11 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full px-4 text-caption font-semibold transition-colors",
                    "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
                    isSelected ? "bg-primary-dark text-white" : "bg-chip-bg text-text-primary hover:bg-info",
                  )}
                >
                  {isSelected ? <Check className="size-4" aria-hidden="true" strokeWidth={3} /> : null}
                  {breed.name}
                </button>
              );
            })}
          </div>
        )}

        <DialogContent aria-label={t("cat.breed.label")}>
          <DialogTitle>{t("cat.breed.label")}</DialogTitle>
          <div className="relative mt-4">
            <Search
              className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-text-tertiary"
              aria-hidden="true"
            />
            <input
              type="search"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
              }}
              placeholder={t("cat.breed.searchPlaceholder")}
              aria-label={t("cat.breed.searchPlaceholder")}
              className="h-12 w-full rounded-xl bg-surface pl-9 pr-3 text-body text-text-primary shadow-xs placeholder:text-text-tertiary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
            />
          </div>
          <ul
            className="mt-3 flex max-h-64 flex-col gap-1 overflow-y-auto"
            role="listbox"
            aria-label={t("cat.breed.label")}
          >
            {loading ? (
              <li className="px-3 py-6 text-center text-body text-text-tertiary">{t("cat.breed.loading")}</li>
            ) : filtered.length === 0 ? (
              <li className="px-3 py-6 text-center text-body text-text-tertiary">{t("cat.breed.empty")}</li>
            ) : (
              filtered.map((breed) => {
                const isSelected = breed.code === value;
                return (
                  <li key={breed.code}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={isSelected}
                      onClick={() => {
                        onChange(breed.code);
                        setOpen(false);
                        setQuery("");
                      }}
                      className={cn(
                        "flex min-h-11 w-full items-center justify-between gap-2 rounded-md px-3 py-2 text-body transition-colors",
                        isSelected
                          ? "bg-chip-bg font-semibold text-primary"
                          : "text-text-primary hover:bg-background-alt",
                      )}
                    >
                      <span>{breed.name}</span>
                      {isSelected ? <Check className="size-4 shrink-0" aria-hidden="true" /> : null}
                    </button>
                  </li>
                );
              })
            )}
          </ul>
        </DialogContent>
      </Dialog>
      {error ? (
        <p role="alert" className="text-small font-medium text-danger-text">
          {error}
        </p>
      ) : null}
    </div>
  );
}

/* ---------------- ActivationCodeInput ---------------- */

interface ActivationCodeInputProps {
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
}

export function ActivationCodeInput({ value, onChange, error, disabled }: ActivationCodeInputProps) {
  const { t } = useTranslation("onboarding");

  return (
    <Input
      label={t("activate.inputLabel")}
      value={value}
      onChange={(event) => {
        onChange(formatActivationCode(event.target.value));
      }}
      placeholder={t("activate.inputPlaceholder")}
      helperText={t("activate.formatHint")}
      error={error}
      disabled={disabled}
      autoComplete="off"
      autoCapitalize="characters"
      spellCheck={false}
      inputMode="text"
      maxLength={24}
    />
  );
}

/** Dạng gửi lên API: `CC-<GÓI>-<10 ký tự>` (có gạch — backend cắt thân mã theo dấu gạch cuối). */
export function toCanonicalCode(value: string): string {
  return formatActivationCode(value);
}

/* ---------------- DisclaimerScroll ---------------- */

const BOTTOM_THRESHOLD_PX = 8;

interface DisclaimerScrollProps {
  children: ReactNode;
  onReachBottom: () => void;
}

export function DisclaimerScroll({ children, onReachBottom }: DisclaimerScrollProps) {
  const { t } = useTranslation("onboarding");
  const scrollRef = useRef<HTMLDivElement>(null);
  const [reachedBottom, setReachedBottom] = useState(false);

  const handleScroll = () => {
    const el = scrollRef.current;
    if (!el) return;
    const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    if (distanceToBottom <= BOTTOM_THRESHOLD_PX && !reachedBottom) {
      setReachedBottom(true);
      onReachBottom();
    }
  };

  return (
    <div className="flex flex-col gap-3">
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="max-h-[50dvh] overflow-y-auto rounded-xl border border-border bg-surface p-4"
        role="region"
        aria-label={t("disclaimer.title")}
      >
        {children}
      </div>
      <p className={cn("text-caption", reachedBottom ? "text-ph-normal-text" : "text-text-tertiary")}>
        {reachedBottom ? t("disclaimer.readDone") : t("disclaimer.scrollHint")}
      </p>
    </div>
  );
}

/* ---------------- Checkbox ---------------- */

interface CheckboxProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  description?: string;
  error?: string;
  disabled?: boolean;
  /** Tô nền cảnh báo (vàng nhạt) khi mô tả là 1 lời cảnh báo — khớp Figma 01c-3 "Đã triệt sản". */
  warn?: boolean;
}

export function Checkbox({ checked, onChange, label, description, error, disabled, warn }: CheckboxProps) {
  const id = useId();
  const errorId = `${id}-error`;

  return (
    <div className="flex flex-col gap-1">
      <label
        className={cn(
          "flex min-h-11 cursor-pointer items-start gap-3 rounded-xl p-4 shadow-xs transition-colors",
          // Web: ô nằm trong thẻ trắng (panel) — thêm viền, nếu không trắng chồng trắng.
          warn ? "bg-warning-bg" : "bg-surface lg:border lg:border-border lg:shadow-none",
          disabled && "cursor-not-allowed opacity-50",
        )}
      >
        <input
          id={id}
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(event) => {
            onChange(event.target.checked);
          }}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          className="peer sr-only"
        />
        <span
          aria-hidden="true"
          className={cn(
            "mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-md border-2 bg-surface transition-colors peer-checked:border-primary-dark peer-checked:bg-primary-dark",
            checked ? "border-primary-dark" : "border-border-strong",
          )}
        >
          {checked ? <Check className="size-3.5 text-white" aria-hidden="true" strokeWidth={3} /> : null}
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="text-body font-semibold text-text-primary">{label}</span>
          {description ? (
            <span className={cn("text-caption", warn ? "text-warning-text" : "text-text-secondary")}>
              {description}
            </span>
          ) : null}
        </span>
      </label>
      {error ? (
        <p id={errorId} role="alert" className="text-small font-medium text-danger-text">
          {error}
        </p>
      ) : null}
    </div>
  );
}

/* ---------------- FieldError ---------------- */

export function FieldError({ message }: { message?: string }) {
  const { t } = useTranslation("onboarding");
  if (!message) return null;
  return (
    <p role="alert" className="text-small font-medium text-danger-text">
      {t(message)}
    </p>
  );
}
