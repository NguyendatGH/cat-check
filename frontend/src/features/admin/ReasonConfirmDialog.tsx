import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle } from "lucide-react";
import { Button, Dialog, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { isReasonValid } from "./api";
import { ReasonField } from "./components";
import { MutationErrorNote } from "./dense";

export interface ReasonConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /** Tên đối tượng bị tác động — luôn hiển thị trước khi xác nhận (p17 A3). */
  target: ReactNode;
  /** Hậu quả của hành động. */
  consequence: string;
  confirmLabel: string;
  onConfirm: (reason: string) => void;
  pending?: boolean;
  error?: unknown;
  /** false khi endpoint không nhận `reason` (vd. gỡ role, gửi lại outbox). */
  requireReason?: boolean;
  destructive?: boolean;
  children?: ReactNode;
}

/**
 * Flow A3: chọn item → xem đối tượng và hậu quả → nhập lý do → xác nhận.
 * Lý do chỉ được hỏi tại thời điểm hành động, không đặt thường trực trên đầu trang.
 */
export function ReasonConfirmDialog(props: ReasonConfirmDialogProps) {
  if (!props.open) return null;
  return <ReasonConfirmDialogInner {...props} />;
}

function ReasonConfirmDialogInner({
  open,
  onOpenChange,
  title,
  target,
  consequence,
  confirmLabel,
  onConfirm,
  pending = false,
  error,
  requireReason = true,
  destructive = false,
  children,
}: ReasonConfirmDialogProps) {
  const { t } = useTranslation("admin");
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const valid = !requireReason || isReasonValid(reason);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next && pending) return;
        onOpenChange(next);
      }}
    >
      <DialogContent className="max-w-lg">
        <DialogTitle className={cn("flex items-center gap-2 pr-10", destructive && "text-danger-text")}>
          {destructive ? <AlertTriangle size={18} aria-hidden="true" /> : null}
          {title}
        </DialogTitle>
        <DialogDescription asChild>
          <div className="flex flex-col gap-2 pt-2">
            <p className="break-words text-caption font-semibold text-text-primary">{target}</p>
            <p>{consequence}</p>
          </div>
        </DialogDescription>
        <form
          className="flex flex-col gap-3 pt-3"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            setSubmitted(true);
            if (!valid || pending) return;
            onConfirm(reason.trim());
          }}
        >
          {children}
          {requireReason ? (
            <ReasonField value={reason} onChange={setReason} showError={submitted} disabled={pending} />
          ) : null}
          <MutationErrorNote error={error} />
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button
              type="button"
              variant="tertiary"
              size="sm"
              disabled={pending}
              onClick={() => {
                onOpenChange(false);
              }}
            >
              {t("actions.cancel")}
            </Button>
            <Button
              type="submit"
              size="sm"
              className={destructive ? "bg-danger shadow-none hover:bg-danger/90" : undefined}
              disabled={pending}
              loading={pending}
            >
              {confirmLabel}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
