import { useEffect, useRef, type ClipboardEvent, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import { Check } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import "./otpCodeInput.css";

gsap.registerPlugin(useGSAP);

const OTP_LENGTH = 6;

export type OtpStatus = "idle" | "verifying" | "success" | "error";

export interface OtpCodeInputProps {
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
  focusOnMount?: boolean;
  status?: OtpStatus;
  /** Fires once the user fills the last digit, for auto-submit. */
  onComplete?: (code: string) => void;
  /** Increment on every failed attempt to replay the shake and clear the boxes. */
  errorSignal?: number;
}

function reducedMotion() {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function OtpCodeInput({
  value,
  onChange,
  error,
  disabled,
  focusOnMount,
  status = "idle",
  onComplete,
  errorSignal = 0,
}: OtpCodeInputProps) {
  const { t } = useTranslation("auth");
  const rowRef = useRef<HTMLDivElement>(null);
  const inputsRef = useRef<(HTMLInputElement | null)[]>([]);
  const prevValue = useRef(value);
  const digits = Array.from({ length: OTP_LENGTH }, (_, i) => value[i] ?? "");
  const locked = disabled || status === "verifying" || status === "success";
  const shownStatus = error && status === "idle" ? "error" : status;

  useEffect(() => {
    if (focusOnMount) inputsRef.current[0]?.focus();
  }, [focusOnMount]);

  useGSAP(
    () => {
      if (reducedMotion()) return;
      gsap.from(inputsRef.current, {
        autoAlpha: 0,
        y: 12,
        scale: 0.9,
        duration: 0.42,
        ease: "back.out(1.6)",
        stagger: 0.05,
        clearProps: "transform,opacity,visibility",
      });
    },
    { scope: rowRef },
  );

  useEffect(() => {
    const before = prevValue.current;
    prevValue.current = value;
    if (value.length <= before.length || reducedMotion()) return;
    const boxes = inputsRef.current.slice(before.length, value.length).filter(Boolean);
    gsap.fromTo(boxes, { scale: 0.82 }, { scale: 1, duration: 0.3, ease: "back.out(2.6)", clearProps: "transform" });
  }, [value]);

  const onCompleteRef = useRef(onComplete);
  onCompleteRef.current = onComplete;
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  useEffect(() => {
    if (errorSignal === 0) return;
    const row = rowRef.current;
    const reset = () => {
      onChangeRef.current("");
      prevValue.current = "";
      inputsRef.current[0]?.focus();
    };
    if (!row || reducedMotion()) {
      reset();
      return;
    }
    const tl = gsap.timeline({ onComplete: reset });
    tl.to(row, { keyframes: { x: [0, -10, 10, -7, 7, -3, 0] }, duration: 0.45, ease: "power1.out" }).to({}, { duration: 0.35 });
    return () => {
      tl.kill();
      gsap.set(row, { x: 0 });
    };
  }, [errorSignal]);

  const commit = (next: string[], focusIndex: number) => {
    const code = next.join("");
    onChange(code);
    inputsRef.current[Math.min(focusIndex, OTP_LENGTH - 1)]?.focus();
    if (code.length === OTP_LENGTH && !next.includes("")) onCompleteRef.current?.(code);
  };

  const fillFrom = (index: number, chars: string) => {
    const next = digits.slice();
    chars
      .slice(0, OTP_LENGTH - index)
      .split("")
      .forEach((char, offset) => {
        next[index + offset] = char;
      });
    commit(next, index + chars.length);
  };

  const handleChange = (index: number, raw: string) => {
    const clean = raw.replace(/\D/g, "");
    if (!clean) {
      const next = digits.slice();
      next[index] = "";
      onChange(next.join(""));
      return;
    }
    const typed = clean.length > 1 && digits[index] && clean.startsWith(digits[index]) ? clean.slice(1) : clean;
    fillFrom(index, typed);
  };

  const handleKeyDown = (index: number, event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Backspace" && !digits[index] && index > 0) {
      event.preventDefault();
      const next = digits.slice();
      next[index - 1] = "";
      onChange(next.join(""));
      inputsRef.current[index - 1]?.focus();
    } else if (event.key === "ArrowLeft" && index > 0) {
      inputsRef.current[index - 1]?.focus();
    } else if (event.key === "ArrowRight" && index < OTP_LENGTH - 1) {
      inputsRef.current[index + 1]?.focus();
    }
  };

  const handlePaste = (index: number, event: ClipboardEvent<HTMLInputElement>) => {
    const pasted = event.clipboardData.getData("text").replace(/\D/g, "");
    if (!pasted) return;
    event.preventDefault();
    fillFrom(index, pasted);
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="relative">
        <div
          ref={rowRef}
          role="group"
          aria-label={t("verifyOtp.fields.code.label")}
          aria-busy={status === "verifying" || undefined}
          data-state={shownStatus}
          className="otp-row flex justify-between gap-2 sm:gap-3"
        >
          {digits.map((digit, index) => (
            <input
              key={index}
              ref={(el) => {
                inputsRef.current[index] = el;
              }}
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              autoComplete={index === 0 ? "one-time-code" : "off"}
              aria-label={t("verifyOtp.fields.code.digit", { index: index + 1 })}
              maxLength={OTP_LENGTH}
              value={digit}
              disabled={locked}
              data-filled={digit !== "" || undefined}
              aria-invalid={error ? true : undefined}
              style={{ animationDelay: `${String(index * 90)}ms` }}
              onFocus={(event) => {
                event.target.select();
              }}
              onChange={(event) => {
                handleChange(index, event.target.value);
              }}
              onKeyDown={(event) => {
                handleKeyDown(index, event);
              }}
              onPaste={(event) => {
                handlePaste(index, event);
              }}
              className="otp-box"
            />
          ))}
        </div>
        {status === "success" ? (
          <span
            className="otp-check absolute -right-2 -top-2 flex size-7 items-center justify-center rounded-full bg-success text-white shadow-sm"
            aria-hidden="true"
          >
            <Check size={16} strokeWidth={3} />
          </span>
        ) : null}
      </div>
      <p
        role={error ? "alert" : "status"}
        className={cn(
          "min-h-5 text-center text-small font-medium transition-colors",
          error ? "text-danger-text" : status === "success" ? "text-success-text" : "text-text-secondary",
        )}
      >
        {error ?? (status === "verifying" ? t("verifyOtp.verifying") : status === "success" ? t("verifyOtp.verified") : "")}
      </p>
    </div>
  );
}
