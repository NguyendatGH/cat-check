import { useTranslation } from "react-i18next";
import { cn } from "../cn";
import { Button } from "../Button/Button";

export interface ErrorStateProps {
  title: string;
  description?: string;
  onRetry?: () => void;
  retryLabel?: string;
  className?: string;
}

/** ErrorState — title/description/onRetry, dùng khi query lỗi. */
export function ErrorState({ title, description, onRetry, retryLabel, className }: ErrorStateProps) {
  // `retryLabel` là optional nhưng được render thẳng làm nội dung nút: gọi `<ErrorState
  // onRetry={…} />` mà quên truyền nhãn thì ra một nút RỖNG, không chữ, không `aria-label`.
  // Mặc định về khoá dùng chung `common:actions.retry` thay vì để nút câm.
  const { t } = useTranslation("common");
  return (
    <div className={cn("flex flex-col items-center gap-3 px-6 py-12 text-center", className)} role="alert">
      <p className="text-h3 text-danger-text">{title}</p>
      {description ? <p className="max-w-sm text-caption text-text-secondary">{description}</p> : null}
      {onRetry ? (
        <Button variant="tertiary" size="sm" onClick={onRetry} className="mt-2">
          {retryLabel ?? t("actions.retry")}
        </Button>
      ) : null}
    </div>
  );
}
