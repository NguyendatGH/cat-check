import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { Check, ShieldCheck, UserPlus } from "lucide-react";
import { Button, EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { usePhBands } from "@/entities/ph-bands";
import {
  MultiCatTip,
  SelectCatOption,
  SharedTrayOption,
  useActiveCatsForCapture,
  useScanCaptureStore,
} from "@/features/scan";

/** `/scan/select-cat` (TaskLayout) — bước chọn mèo TRƯỚC khi mở camera (xem `features/scan/store.ts`
 * để biết vì sao thứ tự này khác mockup gốc). */
export function SelectCatPage() {
  const { t } = useTranslation(["scan", "common"]);
  const navigate = useNavigate();
  const { data: cats, isPending, isError, refetch } = useActiveCatsForCapture();
  const { data: bands } = usePhBands();
  const setSelectedCat = useScanCaptureStore((s) => s.setSelectedCat);
  const setSharedUnknown = useScanCaptureStore((s) => s.setSharedUnknown);

  const [selection, setSelection] = useState<{ id: string; name: string } | "SHARED" | null>(null);

  const handleConfirm = () => {
    if (selection === "SHARED") {
      setSharedUnknown();
    } else if (selection) {
      setSelectedCat(selection);
    } else {
      return;
    }
    void navigate("/scan");
  };

  if (isPending) {
    return (
      <div className="flex flex-col gap-3 p-4">
        <SkeletonLoader shape="card" className="h-20" />
        <SkeletonLoader shape="card" className="h-20" />
      </div>
    );
  }

  if (isError) {
    return (
      <ErrorState
        title={t("selectCat.loadError")}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  }

  if (cats.length === 0) {
    return (
      <EmptyState
        icon={<UserPlus className="size-6" aria-hidden="true" />}
        title={t("selectCat.emptyTitle")}
        description={t("selectCat.emptyDescription")}
        action={
          <Button
            variant="primary"
            onClick={() => {
              void navigate("/cats/new");
            }}
          >
            {t("selectCat.goAddCat")}
          </Button>
        }
      />
    );
  }

  const confirmLabel =
    selection === "SHARED"
      ? t("selectCat.confirmCtaShared")
      : selection
        ? t("selectCat.confirmCtaNamed", { name: selection.name })
        : t("selectCat.confirmCtaGeneric");

  return (
    <div className="flex flex-col gap-4 p-4 pb-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-h2 font-bold text-text-primary">{t("selectCat.heading")}</h1>
        <p className="text-caption text-text-secondary">{t("selectCat.description")}</p>
      </div>

      {/* Ghi chú riêng tư (mockup `05`): chấm tròn xanh + tiêu đề trên, nội dung dưới. */}
      <div className="flex items-start gap-3 rounded-xl bg-chip-bg p-4">
        <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary-dark text-white">
          <ShieldCheck className="size-4" aria-hidden="true" />
        </span>
        <span className="flex min-w-0 flex-col gap-0.5">
          <span className="text-caption font-bold text-primary-dark">{t("selectCat.privacyNoteTitle")}</span>
          <span className="text-caption leading-snug text-text-secondary">{t("selectCat.privacyNoteBody")}</span>
        </span>
      </div>

      <div className="flex flex-col gap-3">
        {cats.map((cat) => (
          <SelectCatOption
            key={cat.id}
            cat={cat}
            bands={bands ?? []}
            selected={selection !== "SHARED" && selection?.id === cat.id}
            onSelect={() => {
              setSelection({ id: cat.id, name: cat.name });
            }}
          />
        ))}
        <SharedTrayOption
          title={t("selectCat.sharedOptionTitle")}
          description={t("selectCat.sharedOptionDescription")}
          selected={selection === "SHARED"}
          onSelect={() => {
            setSelection("SHARED");
          }}
        />
        <button
          type="button"
          onClick={() => {
            void navigate("/cats/new");
          }}
          className="flex min-h-[var(--touch-target-min)] w-full items-center justify-center gap-2 rounded-2xl bg-surface p-3 text-caption font-bold text-primary-dark"
        >
          <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-info">
            <UserPlus className="size-4" aria-hidden="true" />
          </span>
          {t("selectCat.addAnotherCat")}
        </button>
      </div>

      <MultiCatTip />

      <div className="flex flex-col gap-1">
        <Button
          variant="primary"
          size="lg"
          className="w-full"
          disabled={!selection}
          leftIcon={<Check className="size-4" aria-hidden="true" />}
          onClick={handleConfirm}
        >
          {confirmLabel}
        </Button>
        <Button
          variant="tertiary"
          className="w-full border-transparent"
          onClick={() => {
            void navigate(-1);
          }}
        >
          {t("selectCat.cancel")}
        </Button>
      </div>
    </div>
  );
}
