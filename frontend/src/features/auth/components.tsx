import {
  useEffect,
  useId,
  useRef,
  useState,
  type InputHTMLAttributes,
  type ReactNode,
} from "react";
import { useTranslation } from "react-i18next";
import { Check, Copy, Eye, EyeOff, Lock, Clock } from "lucide-react";
import { Button } from "@/shared/ui";
import { GoogleGlyph } from "@/shared/assets/icons/GoogleGlyph";
import { cn } from "@/shared/lib/cn";
import { intlLocale } from "@/shared/lib/format/locale";
import { estimatePasswordStrength, type PasswordStrength } from "./schemas";
import type { ConsentPurposeOption, SessionItem } from "./types";



interface PasswordFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
}

export function PasswordField({ className, label, error, helperText, id, ...props }: PasswordFieldProps) {
  const { t } = useTranslation("auth");
  const [visible, setVisible] = useState(false);
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const helperId = `${inputId}-helper`;
  const errorId = `${inputId}-error`;

  return (
    <div className="flex flex-col gap-1.5">
      {label ? (
        <label htmlFor={inputId} className="text-caption font-semibold text-text-secondary">
          {label}
        </label>
      ) : null}
      <div className="relative flex items-center">
        <span className="pointer-events-none absolute left-3.5 flex items-center text-text-tertiary" aria-hidden="true">
          <Lock className="size-4" />
        </span>
        <input
          id={inputId}
          type={visible ? "text" : "password"}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : helperText ? helperId : undefined}
          className={cn(
            "h-12 w-full rounded-xl border border-border-strong bg-surface pl-11 pr-11 text-body text-text-primary shadow-xs",
            "placeholder:text-text-tertiary",
            "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
            "disabled:cursor-not-allowed disabled:opacity-50",
            error && "outline outline-2 outline-danger",
            className,
          )}
          {...props}
        />
        <button
          type="button"
          onClick={() => {
            setVisible((v) => !v);
          }}
          aria-label={visible ? t("common.hidePassword") : t("common.showPassword")}
          className="absolute right-2 flex size-8 items-center justify-center rounded-lg text-text-tertiary hover:text-text-secondary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
        >
          {visible ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
        </button>
      </div>
      {error ? (
        <p id={errorId} className="text-small text-danger-text">
          {error}
        </p>
      ) : helperText ? (
        <p id={helperId} className="text-small text-text-tertiary">
          {helperText}
        </p>
      ) : null}
    </div>
  );
}


const STRENGTH_LEVEL: Record<PasswordStrength, number> = { empty: 0, weak: 1, medium: 2, strong: 3 };
const STRENGTH_COLOR: Record<PasswordStrength, string> = {
  empty: "bg-border",
  weak: "bg-danger",
  medium: "bg-warning",
  strong: "bg-success",
};

export function PasswordStrengthMeter({ password }: { password: string }) {
  const { t } = useTranslation("auth");
  const strength = estimatePasswordStrength(password);
  const level = STRENGTH_LEVEL[strength];

  return (
    <div className="flex flex-col gap-1.5 rounded-xl bg-background-alt px-3 py-2">
      <div className="flex items-center justify-between gap-2">
        <span className="text-overline font-medium text-text-secondary">{t("register.passwordStrength.label")}</span>
        <span className="text-overline font-medium text-text-tertiary">
          {strength === "empty" ? t("register.passwordStrength.empty") : t(`register.passwordStrength.${strength}`)}
        </span>
      </div>
      <div className="flex gap-1.5">
        {[1, 2, 3].map((segment) => (
          <span
            key={segment}
            className={cn("h-1.5 flex-1 rounded-full bg-border", segment <= level && STRENGTH_COLOR[strength])}
          />
        ))}
      </div>
    </div>
  );
}


interface ResendCountdownProps {
  seconds: number;
  onExpire?: () => void;
  onResend: () => void;
  disabled?: boolean;
}

export function ResendCountdown({ seconds, onExpire, onResend, disabled }: ResendCountdownProps) {
  const { t } = useTranslation("auth");
  const [remaining, setRemaining] = useState(seconds);
  const expiredRef = useRef(false);

  useEffect(() => {
    setRemaining(seconds);
    expiredRef.current = false;
  }, [seconds]);

  useEffect(() => {
    if (remaining <= 0) {
      if (!expiredRef.current) {
        expiredRef.current = true;
        onExpire?.();
      }
      return;
    }
    const timer = window.setTimeout(() => {
      setRemaining((r) => r - 1);
    }, 1000);
    return () => {
      window.clearTimeout(timer);
    };
  }, [remaining, onExpire]);

  if (remaining > 0) {
    return (
      <p className="flex w-fit items-center gap-1.5 rounded-full bg-background-alt px-3 py-1 text-caption text-text-secondary lg:bg-transparent lg:px-0">
        <Clock size={14} aria-hidden="true" className="text-primary-dark" />
        {t("verifyOtp.resendCountdown", { seconds: remaining })}
      </p>
    );
  }

  return (
    <button
      type="button"
      onClick={onResend}
      disabled={disabled}
      className="min-h-11 text-caption font-semibold text-primary hover:underline disabled:opacity-50"
    >
      {t("verifyOtp.resendNow")}
    </button>
  );
}


interface ConsentCheckboxListProps {
  purposes: ConsentPurposeOption[];
  values: Record<string, boolean | undefined>;
  onChange: (code: string, granted: boolean) => void;
}

export function ConsentCheckboxList({ purposes, values, onChange }: ConsentCheckboxListProps) {
  const { t } = useTranslation("auth");
  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="sr-only">{t("register.consents.legend")}</legend>
      {purposes.map((purpose) => {
        const checked = purpose.mandatory ? true : Boolean(values[purpose.code]);
        const inputId = `consent-${purpose.code}`;
        return (
          <label
            key={purpose.code}
            htmlFor={inputId}
            aria-label={purpose.label}
            className={cn(
              "flex min-h-11 cursor-pointer items-start gap-3 rounded-lg border border-border bg-surface p-3",
              purpose.mandatory && "cursor-default bg-background-alt",
            )}
          >
            <input
              id={inputId}
              type="checkbox"
              checked={checked}
              disabled={purpose.mandatory}
              onChange={(event) => {
                onChange(purpose.code, event.target.checked);
              }}
              className="mt-0.5 size-5 shrink-0 rounded-sm border-2 border-border-strong text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
            />
            <span className="flex flex-col gap-0.5">
              <span className="flex flex-wrap items-center gap-2">
                <span className="text-caption font-semibold text-text-primary">{purpose.label}</span>
                {purpose.mandatory ? (
                  <span className="rounded-full bg-chip-bg px-2 py-0.5 text-overline font-semibold text-primary">
                    {t("register.consents.mandatoryBadge")}
                  </span>
                ) : null}
                {purpose.sensitive ? (
                  <span className="rounded-full bg-danger-bg px-2 py-0.5 text-overline font-semibold text-danger-text">
                    {t("register.consents.sensitiveBadge")}
                  </span>
                ) : null}
              </span>
              <span className="text-small text-text-secondary">{purpose.description}</span>
            </span>
          </label>
        );
      })}
    </fieldset>
  );
}


export function GoogleAuthButton({ label, className }: { label: string; className?: string }) {
  return (
    <Button
      type="button"
      variant="tertiary"
      size="lg"
      className={cn("w-full bg-surface text-text-primary shadow-xs", className)}
      onClick={() => {
        window.location.assign("/oauth2/authorization/google");
      }}
      leftIcon={<GoogleGlyph className="size-5" />}
    >
      {label}
    </Button>
  );
}


interface SessionRowProps {
  session: SessionItem;
  onRevoke?: (id: string) => void;
  revoking?: boolean;
}

export function SessionRow({ session, onRevoke, revoking }: SessionRowProps) {
  const { t } = useTranslation("auth");
  return (
    <li className="flex items-center justify-between gap-3 rounded-lg border border-border bg-surface p-3">
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="flex items-center gap-2 text-body font-semibold text-text-primary">
          <span className="truncate">{session.deviceLabel ?? t("sessions.unknownDevice")}</span>
          {session.current ? (
            <span className="shrink-0 rounded-full bg-chip-bg px-2 py-0.5 text-overline font-semibold text-primary">
              {t("sessions.currentBadge")}
            </span>
          ) : null}
        </span>
        <span className="text-caption text-text-tertiary">
          {session.ipMasked ? `${session.ipMasked} • ` : ""}
          {t("sessions.lastSeen", { date: new Date(session.lastSeenAt).toLocaleString(intlLocale()) })}
        </span>
      </div>
      {!session.current && onRevoke ? (
        <Button
          type="button"
          variant="tertiary"
          size="sm"
          loading={revoking}
          onClick={() => {
            onRevoke(session.id);
          }}
        >
          {t("sessions.revoke")}
        </Button>
      ) : null}
    </li>
  );
}


export function RecoveryCodeGrid({ codes }: { codes: string[] }) {
  const { t } = useTranslation("auth");
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    void navigator.clipboard.writeText(codes.join("\n")).then(() => {
      setCopied(true);
      window.setTimeout(() => {
        setCopied(false);
      }, 2000);
    });
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-2 gap-2 rounded-lg border border-border bg-background-alt p-3 font-mono">
        {codes.map((code) => (
          <span key={code} className="text-caption text-text-primary">
            {code}
          </span>
        ))}
      </div>
      <Button
        type="button"
        variant="tertiary"
        size="sm"
        onClick={handleCopy}
        leftIcon={copied ? <Check className="size-4" /> : <Copy className="size-4" />}
      >
        {copied ? t("mfa.recoveryCodes.copied") : t("mfa.recoveryCodes.copy")}
      </Button>
    </div>
  );
}


export function InlineAlert({ children, tone = "danger" }: { children: ReactNode; tone?: "danger" | "info" }) {
  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      className={cn(
        "rounded-lg border p-3 text-small",
        tone === "danger"
          ? "border-danger bg-danger-bg text-danger-text"
          : "border-border bg-background-alt text-text-secondary",
      )}
    >
      {children}
    </div>
  );
}
