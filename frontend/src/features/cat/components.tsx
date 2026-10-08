import { useMemo, useRef, useState, type ReactNode } from "react";
import type { FieldErrors, UseFormRegister, UseFormSetValue, UseFormWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { AlertTriangle, Camera, Check, ChevronDown, ChevronUp, Search, Trash2 } from "lucide-react";
import { Badge, Button, Dialog, DialogContent, DialogDescription, DialogTitle, Input } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { CLINICAL_SIGNS, type CatBreed, type ClinicalSign } from "@/entities/cat";
import type { CatFormSchemaValues } from "./schemas";

/**
 * Component dùng chung cho `features/cat` — gom 1 file root theo khuôn mẫu bắt buộc của
 * `features/onboarding` (xem `docs/handovers/A7.md` mục 6: cấu trúc phẳng, không thư mục
 * con, để khớp quy ước chung giữa các agent chạy song song ở wave này).
 */

/* ---------------- FieldError ---------------- */

export function FieldError({ message }: { message?: string }) {
  const { t } = useTranslation("cat");
  if (!message) return null;
  return (
    <p role="alert" className="text-small font-medium text-danger-text">
      {t(message)}
    </p>
  );
}

/* ---------------- AvatarUpload ---------------- */

const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // p8 §8.2.4(c)
const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];

interface AvatarUploadProps {
  previewUrl: string | null;
  onChange: (file: File | null) => void;
  onRemoveExisting?: () => void;
  hasExisting?: boolean;
  error?: string;
  disabled?: boolean;
}

export function AvatarUpload({
  previewUrl,
  onChange,
  onRemoveExisting,
  hasExisting,
  error,
  disabled,
}: AvatarUploadProps) {
  const { t } = useTranslation("cat");
  const inputRef = useRef<HTMLInputElement>(null);
  const [localError, setLocalError] = useState<string | null>(null);

  const handlePick = (picked: File | null) => {
    setLocalError(null);
    if (!picked) return;
    if (!ACCEPTED_TYPES.includes(picked.type)) {
      setLocalError(t("avatar.invalidType"));
      return;
    }
    if (picked.size > MAX_FILE_SIZE_BYTES) {
      setLocalError(t("avatar.tooLarge"));
      return;
    }
    onChange(picked);
  };

  const displayedError = error ?? localError;
  const showRemove = Boolean(previewUrl) && (Boolean(onRemoveExisting) || Boolean(hasExisting));

  return (
    <div className="flex flex-col items-center gap-2">
      <div className="relative">
        <button
          type="button"
          disabled={disabled}
          onClick={() => inputRef.current?.click()}
          aria-label={previewUrl ? t("avatar.change") : t("avatar.add")}
          className={cn(
            "flex size-28 items-center justify-center overflow-hidden rounded-full border-2 border-dashed bg-background-alt transition-colors",
            "hover:border-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
            displayedError ? "border-danger" : "border-border",
            disabled && "cursor-not-allowed opacity-50",
          )}
        >
          {previewUrl ? (
            <img src={previewUrl} alt="" className="size-full object-cover" />
          ) : (
            <Camera className="size-8 text-text-tertiary" aria-hidden="true" />
          )}
        </button>
        {showRemove ? (
          <button
            type="button"
            disabled={disabled}
            onClick={() => {
              onChange(null);
              onRemoveExisting?.();
            }}
            aria-label={t("avatar.remove")}
            className="absolute -right-1 -top-1 flex size-9 items-center justify-center rounded-full border border-border bg-surface text-text-secondary shadow-sm transition-colors hover:text-danger-text focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
          >
            <Trash2 className="size-4" aria-hidden="true" />
          </button>
        ) : null}
      </div>

      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="hidden"
        onChange={(event) => {
          handlePick(event.target.files?.[0] ?? null);
          event.target.value = "";
        }}
      />

      <p className="text-caption text-text-tertiary">{t("avatar.hint")}</p>
      {displayedError ? (
        <p role="alert" className="text-small font-medium text-danger-text">
          {displayedError}
        </p>
      ) : null}
    </div>
  );
}

/* ---------------- BreedPicker ---------------- */

interface BreedPickerProps {
  breeds: CatBreed[] | undefined;
  loading: boolean;
  value: string;
  onChange: (breedCode: string) => void;
  error?: string;
}

export function BreedPicker({ breeds, loading, value, onChange, error }: BreedPickerProps) {
  const { t } = useTranslation("cat");
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");

  const selected = breeds?.find((b) => b.code === value);
  const popular = useMemo(() => breeds?.filter((b) => b.popular) ?? [], [breeds]);

  const filtered = useMemo(() => {
    if (!breeds) return [];
    const q = query.trim().toLowerCase();
    if (!q) return breeds;
    return breeds.filter((b) => b.name.toLowerCase().includes(q));
  }, [breeds, query]);

  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-caption font-semibold text-text-secondary">{t("form.breed.label")}</span>
      <Dialog open={open} onOpenChange={setOpen}>
        <Button
          type="button"
          variant="tertiary"
          className={cn("w-full justify-between", error && "border-danger")}
          onClick={() => {
            setOpen(true);
          }}
        >
          <span className={cn("truncate text-left", !selected && "text-text-tertiary")}>
            {selected ? selected.name : t("form.breed.placeholder")}
          </span>
          <ChevronDown className="size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
        </Button>
        <DialogContent aria-label={t("form.breed.label")}>
          <DialogTitle>{t("form.breed.label")}</DialogTitle>
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
              placeholder={t("form.breed.searchPlaceholder")}
              aria-label={t("form.breed.searchPlaceholder")}
              className="min-h-11 w-full rounded-md border border-border bg-surface pl-9 pr-3 text-body text-text-primary placeholder:text-text-tertiary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
            />
          </div>
          {!query && popular.length > 0 ? (
            <div className="mt-3 flex flex-wrap gap-2">
              {popular.map((breed) => (
                <button
                  key={breed.code}
                  type="button"
                  onClick={() => {
                    onChange(breed.code);
                    setOpen(false);
                    setQuery("");
                  }}
                  className="min-h-9 rounded-full border border-border bg-background-alt px-3 text-caption font-medium text-text-primary hover:border-primary"
                >
                  {breed.name}
                </button>
              ))}
            </div>
          ) : null}
          <ul
            className="mt-3 flex max-h-64 flex-col gap-1 overflow-y-auto"
            role="listbox"
            aria-label={t("form.breed.label")}
          >
            {loading ? (
              <li className="px-3 py-6 text-center text-body text-text-tertiary">{t("form.breed.loading")}</li>
            ) : filtered.length === 0 ? (
              <li className="px-3 py-6 text-center text-body text-text-tertiary">{t("form.breed.empty")}</li>
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
      <FieldError message={error} />
    </div>
  );
}

/* ---------------- OptionCard (radio/checkbox lớn) ---------------- */

interface OptionCardProps {
  name: string;
  value: string;
  checked: boolean;
  onChange: (value: string) => void;
  type?: "radio" | "checkbox";
  title: string;
  disabled?: boolean;
  /** Đệm/khoảng cách hẹp hơn — cho hàng 3 lựa chọn ngắn ở bề ngang điện thoại. */
  compact?: boolean;
}

export function OptionCard({
  name,
  value,
  checked,
  onChange,
  type = "radio",
  title,
  disabled,
  compact = false,
}: OptionCardProps) {
  return (
    <label
      className={cn(
        "relative flex min-h-11 cursor-pointer items-center rounded-xl border-2 bg-surface transition-colors",
        compact ? "gap-2 px-2.5 py-2.5" : "gap-3 p-3",
        checked ? "border-primary bg-chip-bg" : "border-border hover:border-border-strong",
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
        className="peer sr-only"
      />
      <span
        aria-hidden="true"
        className={cn(
          "flex size-5 shrink-0 items-center justify-center border-2 transition-colors",
          type === "radio" ? "rounded-full" : "rounded-sm",
          checked ? "border-primary" : "border-border-strong",
        )}
      >
        {checked ? (
          <span className={cn("bg-primary", type === "radio" ? "size-2.5 rounded-full" : "size-3 rounded-sm")} />
        ) : null}
      </span>
      <span className={cn("font-medium text-text-primary", compact ? "text-caption md:text-body" : "text-body")}>
        {title}
      </span>
    </label>
  );
}

/* ---------------- CatFormFields ---------------- */

interface CatFormFieldsProps {
  register: UseFormRegister<CatFormSchemaValues>;
  watch: UseFormWatch<CatFormSchemaValues>;
  setValue: UseFormSetValue<CatFormSchemaValues>;
  errors: FieldErrors<CatFormSchemaValues>;
  breeds: CatBreed[] | undefined;
  breedsLoading: boolean;
  avatarPreviewUrl: string | null;
  onAvatarChange: (file: File | null) => void;
  onRemoveExistingAvatar?: () => void;
  hasExistingAvatar?: boolean;
}

/** Bộ field dùng chung cho `CatNewPage` và `CatEditPage` (D2/D4, p8 §8.4.4). */
export function CatFormFields({
  register,
  watch,
  setValue,
  errors,
  breeds,
  breedsLoading,
  avatarPreviewUrl,
  onAvatarChange,
  onRemoveExistingAvatar,
  hasExistingAvatar,
}: CatFormFieldsProps) {
  const { t } = useTranslation("cat");
  const sex = watch("sex");
  const ageMode = watch("ageMode");

  return (
    <div className="flex flex-col gap-6">
      <AvatarUpload
        previewUrl={avatarPreviewUrl}
        onChange={onAvatarChange}
        onRemoveExisting={onRemoveExistingAvatar}
        hasExisting={hasExistingAvatar}
      />

      <div>
        <Input
          label={t("form.name.label")}
          placeholder={t("form.name.placeholder")}
          autoComplete="off"
          {...register("name")}
        />
        <FieldError message={errors.name?.message} />
      </div>

      <BreedPicker
        breeds={breeds}
        loading={breedsLoading}
        value={watch("breedCode")}
        onChange={(code) => {
          setValue("breedCode", code, { shouldValidate: true });
        }}
        error={errors.breedCode?.message ? t(errors.breedCode.message) : undefined}
      />

      <Input
        label={t("form.breedOther.label")}
        placeholder={t("form.breedOther.placeholder")}
        autoComplete="off"
        {...register("breedOther")}
      />

      <Input
        label={t("form.coatColor.label")}
        placeholder={t("form.coatColor.placeholder")}
        autoComplete="off"
        {...register("coatColor")}
      />

      <fieldset className="flex flex-col gap-3">
        <legend className="text-caption font-semibold text-text-secondary">{t("form.sex.label")}</legend>
        {/* Ba lựa chọn ngắn — giữ một hàng ở mọi bề ngang (bản compact), không để "Chưa rõ"
            vỡ hai dòng ở 390px như trước. */}
        <div className="grid grid-cols-3 gap-2 md:gap-3">
          {(["MALE", "FEMALE", "UNKNOWN"] as const).map((value) => (
            <OptionCard
              key={value}
              compact
              name="sex"
              value={value}
              checked={sex === value}
              onChange={(v) => {
                setValue("sex", v, { shouldValidate: true });
              }}
              title={t(`form.sex.${value === "MALE" ? "male" : value === "FEMALE" ? "female" : "unknown"}`)}
            />
          ))}
        </div>
        <FieldError message={errors.sex?.message} />
      </fieldset>

      <label className="flex min-h-11 cursor-pointer items-center gap-3 rounded-xl border border-border bg-surface p-3">
        <input
          type="checkbox"
          checked={watch("neutered")}
          onChange={(event) => {
            setValue("neutered", event.target.checked);
          }}
          className="size-5 shrink-0 accent-primary"
        />
        <span className="text-body text-text-primary">{t("form.neutered.label")}</span>
      </label>

      <fieldset className="flex flex-col gap-3">
        <legend className="text-caption font-semibold text-text-secondary">{t("form.ageMode.label")}</legend>
        {/* `sm` của dự án là 375px — hai cột ở 390px làm "Chỉ biết tuổi ước lượng" vỡ dòng. */}
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <OptionCard
            name="ageMode"
            value="birthDate"
            checked={ageMode === "birthDate"}
            onChange={() => {
              setValue("ageMode", "birthDate", { shouldValidate: true });
            }}
            title={t("form.ageMode.birthDate")}
          />
          <OptionCard
            name="ageMode"
            value="approx"
            checked={ageMode === "approx"}
            onChange={() => {
              setValue("ageMode", "approx", { shouldValidate: true });
            }}
            title={t("form.ageMode.approx")}
          />
        </div>

        {ageMode === "birthDate" ? (
          <div className="flex flex-col gap-1.5">
            <label htmlFor="cat-birth-date" className="text-caption font-semibold text-text-secondary">
              {t("form.birthDate.label")}
            </label>
            <input
              id="cat-birth-date"
              type="date"
              max={new Date().toISOString().slice(0, 10)}
              aria-invalid={errors.birthDate ? true : undefined}
              className={cn(
                "min-h-11 rounded-md border bg-surface px-3 text-body text-text-primary",
                "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
                errors.birthDate ? "border-danger" : "border-border",
              )}
              {...register("birthDate")}
            />
            <p className="text-small text-text-tertiary">{t("form.birthDate.hint")}</p>
            <FieldError message={errors.birthDate?.message} />
          </div>
        ) : (
          <div>
            <Input
              type="number"
              inputMode="numeric"
              min={0}
              max={360}
              label={t("form.approxAgeMonths.label")}
              placeholder={t("form.approxAgeMonths.placeholder")}
              {...register("approxAgeMonths")}
            />
            <FieldError message={errors.approxAgeMonths?.message} />
          </div>
        )}
      </fieldset>

      <div>
        <Input
          type="number"
          inputMode="decimal"
          step="0.1"
          min={0.01}
          max={29.99}
          label={t("form.weight.label")}
          placeholder={t("form.weight.placeholder")}
          {...register("weightKg")}
        />
        <FieldError message={errors.weightKg?.message} />
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="cat-notes" className="text-caption font-semibold text-text-secondary">
          {t("form.notes.label")}
        </label>
        <textarea
          id="cat-notes"
          rows={3}
          placeholder={t("form.notes.placeholder")}
          className="rounded-md border border-border bg-surface px-3 py-2 text-body text-text-primary placeholder:text-text-tertiary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
          {...register("notes")}
        />
      </div>
    </div>
  );
}

/* ---------------- StatusBadge / PrimaryBadge ---------------- */

export function ArchivedBadge() {
  const { t } = useTranslation("cat");
  return <Badge tone="neutral">{t("status.archived")}</Badge>;
}

export function PrimaryBadge() {
  const { t } = useTranslation("cat");
  return (
    <Badge tone="brand" className="gap-1">
      {t("status.primary")}
    </Badge>
  );
}

/* ---------------- ConfirmDialog ---------------- */

interface ConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: ReactNode;
  confirmLabel: string;
  cancelLabel: string;
  destructive?: boolean;
  loading?: boolean;
  onConfirm: () => void;
}

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  cancelLabel,
  destructive,
  loading,
  onConfirm,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogTitle>{title}</DialogTitle>
        {description ? <DialogDescription>{description}</DialogDescription> : null}
        <div className="mt-4 flex flex-col gap-3">
          <Button
            type="button"
            variant="primary"
            className={destructive ? "bg-danger hover:opacity-90" : undefined}
            loading={loading}
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
          <Button
            type="button"
            variant="tertiary"
            onClick={() => {
              onOpenChange(false);
            }}
          >
            {cancelLabel}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/* ---------------- EmergencyDisclaimerBanner / ClinicalSignEmergencyNotice ---------------- */

/**
 * Nội dung khối `EMERGENCY` (p15 §15.7.3, nguyên văn) — dùng chung cho 2 nơi:
 *  1. `ClinicalSignEmergencyNotice` — bắt buộc hiện TRƯỚC khi cho khai dấu hiệu lâm sàng
 *     (domain `CatClinicalSignReport.blockingShown`, p4 C5), luôn mở, không được rút gọn.
 *  2. `EmergencyDisclaimerBanner` — biến thể `EMERGENCY` trên `/cats/:catId` (p9 §9.13.5 /
 *     p15 §15.7.2 P3): mặc định thu gọn, tự bung khi có `warning_flags`.
 * Copy đặt tại `cat.json` (`disclaimer.emergency.*`), KHÔNG bịa thêm chữ.
 */
function EmergencyContent({ showTitle = true }: { showTitle?: boolean }) {
  const { t } = useTranslation("cat");
  const points = t("disclaimer.emergency.points", { returnObjects: true }) as string[];
  return (
    <div className="flex flex-col gap-2">
      {showTitle ? <p className="text-body font-bold text-danger-text">{t("disclaimer.emergency.title")}</p> : null}
      <p className="text-caption text-danger-text">{t("disclaimer.emergency.intro")}</p>
      <ul className="list-disc pl-5 text-caption text-danger-text">
        {points.map((point) => (
          <li key={point}>{point}</li>
        ))}
      </ul>
      <p className="text-caption font-semibold text-danger-text">{t("disclaimer.emergency.outro")}</p>
    </div>
  );
}

export function ClinicalSignEmergencyNotice({ className }: { className?: string }) {
  return (
    <div role="alert" className={cn("rounded-xl border-2 border-danger bg-danger-bg p-4", className)}>
      <div className="flex items-start gap-2">
        <AlertTriangle className="mt-0.5 size-5 shrink-0 text-danger-text" aria-hidden="true" />
        <EmergencyContent />
      </div>
    </div>
  );
}

export interface EmergencyDisclaimerBannerProps {
  /** `true` khi mèo có `unacknowledgedFlagCount > 0` — tự bung, không cho thu lại. */
  forceExpanded?: boolean;
  className?: string;
}

export function EmergencyDisclaimerBanner({ forceExpanded = false, className }: EmergencyDisclaimerBannerProps) {
  const { t } = useTranslation("cat");
  const [manuallyExpanded, setManuallyExpanded] = useState(false);
  const expanded = forceExpanded || manuallyExpanded;

  return (
    <div
      role={forceExpanded ? "alert" : undefined}
      className={cn(
        "rounded-xl border-2 p-4 transition-colors",
        forceExpanded ? "border-danger bg-danger-bg" : "border-border bg-background-alt",
        className,
      )}
    >
      <button
        type="button"
        onClick={() => {
          setManuallyExpanded((v) => !v);
        }}
        disabled={forceExpanded}
        className={cn(
          "flex w-full min-h-11 items-center justify-between gap-2 text-left",
          forceExpanded && "cursor-default",
        )}
      >
        <span className="flex items-center gap-2">
          <AlertTriangle
            className={cn("size-5 shrink-0", forceExpanded ? "text-danger-text" : "text-text-tertiary")}
            aria-hidden="true"
          />
          <span className={cn("text-body font-semibold", forceExpanded ? "text-danger-text" : "text-text-primary")}>
            {forceExpanded ? t("disclaimer.emergency.title") : t("disclaimer.emergencyCollapsedTitle")}
          </span>
        </span>
        {!forceExpanded ? (
          expanded ? (
            <ChevronUp className="size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
          ) : (
            <ChevronDown className="size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
          )
        ) : null}
      </button>
      {expanded ? (
        <div className="mt-3">
          <EmergencyContent showTitle={!forceExpanded} />
        </div>
      ) : null}
    </div>
  );
}

/* ---------------- ClinicalSignPicker ---------------- */

interface ClinicalSignPickerProps {
  value: ClinicalSign[];
  onChange: (signs: ClinicalSign[]) => void;
  error?: string;
}

export function ClinicalSignPicker({ value, onChange, error }: ClinicalSignPickerProps) {
  const { t } = useTranslation("cat");

  const toggle = (sign: ClinicalSign) => {
    onChange(value.includes(sign) ? value.filter((s) => s !== sign) : [...value, sign]);
  };

  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="text-body font-semibold text-text-primary">{t("clinicalSigns.form.signs.label")}</legend>
      <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
        {CLINICAL_SIGNS.map((sign) => (
          <label
            key={sign}
            className={cn(
              "flex min-h-11 cursor-pointer items-start gap-3 rounded-xl border-2 bg-surface p-3 transition-colors",
              value.includes(sign) ? "border-primary bg-chip-bg" : "border-border",
            )}
          >
            <input
              type="checkbox"
              checked={value.includes(sign)}
              onChange={() => {
                toggle(sign);
              }}
              className="peer sr-only"
            />
            <span
              aria-hidden="true"
              className={cn(
                "mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-sm border-2 transition-colors",
                value.includes(sign) ? "border-primary bg-primary" : "border-border-strong",
              )}
            >
              {value.includes(sign) ? <Check className="size-3.5 text-white" aria-hidden="true" /> : null}
            </span>
            <span className="text-body text-text-primary">{t(`clinicalSigns.signs.${sign}`)}</span>
          </label>
        ))}
      </div>
      <FieldError message={error} />
    </fieldset>
  );
}

/* ---------------- SectionHeading ---------------- */

export function SectionHeading({ children, hint, id }: { children: ReactNode; hint?: string; id?: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <h2 id={id} className="text-h3 font-semibold text-text-primary">
        {children}
      </h2>
      {hint ? <p className="text-caption text-text-secondary">{hint}</p> : null}
    </div>
  );
}
