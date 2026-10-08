import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { Archive, ArchiveRestore, Crown, MoreVertical, Pencil, Plus, Stethoscope, Trash2 } from "lucide-react";
import {
  Badge,
  Button,
  Card,
  Dialog,
  DialogContent,
  DialogTitle,
  EmptyState,
  ErrorState,
  ListItem,
  SkeletonLoader,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { useBreakpoint } from "@/shared/lib/hooks/useBreakpoint";
import { isApiError } from "@/shared/api";
import {
  useFormatCatAge,
  type Cat,
  type CatHealthSurveyAnswers,
  type ClinicalSign,
  type NoteType,
} from "@/entities/cat";
import { usePhBands } from "@/entities/ph-bands";
import {
  ClinicalSignEmergencyNotice,
  ClinicalSignPicker,
  clinicalSignFormSchema,
  ConfirmDialog,
  EmergencyDisclaimerBanner,
  FieldError,
  OptionCard,
  SectionHeading,
  surveyFormSchema,
  noteFormSchema,
  useArchiveCat,
  useCat,
  useCatList,
  useCatNotes,
  useCatSummary,
  useCreateNote,
  useDeleteNote,
  useHealthSurvey,
  usePatchNote,
  useReportClinicalSigns,
  useSetPrimaryCat,
  useSoftDeleteCat,
  useSubmitHealthSurvey,
  useUnarchiveCat,
  type CatSummaryResponse,
  type ClinicalSignFormSchemaValues,
  type NoteFormSchemaValues,
  type SurveyFormSchemaValues,
} from "@/features/cat";
import {
  formatScanTimestamp,
  MonthGroupHeader,
  ScanTimelineItem,
  monthGroupKey,
  parseMonthGroupKey,
  useScanHistory,
  useScanSummary,
} from "@/features/history";
import { useExportWizardStore } from "@/features/export";
import { useScanCaptureStore } from "@/features/scan";
import { HealthFlagDisclosure } from "@/features/insight";
import { CatClinicalColumn, CatIdentityExtras, CatProfileBar } from "./CatMedicalPanels";
import { CatProfileHero } from "./CatProfileHero";
import { CatBiomarkerCard, CatProfileActions, CatRecentScansCard, CatStatGrid } from "./CatOverviewPanels";

const NOTE_TYPES: NoteType[] = ["GENERAL", "DIET_CHANGE", "SYMPTOM", "VET_VISIT", "LITTER_CHANGE"];
const SURVEY_QUESTION_NAMES = ["litterType", "urinaryHistory", "dietType", "urinationFrequency"] as const;

/**
 * `/cats/:catId` — M2 06 · W1 10 · P15 §15.7.2 P3 (p9 route #29).
 *
 * Hai bố cục, chọn bằng `useBreakpoint("lg")` để CHỈ MỘT cây được dựng (tránh hai dialog
 * "thao tác khác" cùng mở và truy vấn trùng):
 *  - mobile (mockup `06`): thẻ nhận diện + 3 tab Tổng quan / Lịch sử sức khoẻ / Ghi chú;
 *  - desktop (Figma `Web - 06 & 07`): thanh chọn hồ sơ đầu trang + lưới 5/7. Cột trái là hồ sơ
 *    (nhận diện, thông tin, cơ sở thú y), cột phải là theo dõi (chỉ số, thang pH, dòng thời
 *    gian có sửa ghi chú, khảo sát + báo dấu hiệu) — hai cột cao xấp xỉ nhau thay vì cột trái
 *    dài gấp đôi cột phải như trước.
 */
export function CatDetailPage() {
  const { t } = useTranslation(["cat", "common"]);
  const navigate = useNavigate();
  const { catId } = useParams<{ catId: string }>();
  const isDesktop = useBreakpoint("lg");

  const { data: cat, isPending, isError, refetch } = useCat(catId);
  // D12 — số liệu lâm sàng thật. Không chặn render: lỗi/đang tải thì panel tự rơi về
  // trạng thái "chưa có dữ liệu" (mèo mới tạo chưa quét lần nào là hợp lệ).
  const { data: catSummary } = useCatSummary(catId);
  // Tổng số lần quét THẬT cho nhãn "Xem toàn bộ lịch sử (N bản ghi)" ở panel dòng thời gian.
  const { data: catScanSummary } = useScanSummary(catId);
  const { data: phBands } = usePhBands();
  // Thanh chọn hồ sơ (desktop) — cùng query với thẻ chuyển bé ở sidebar nên không gọi thêm.
  const { data: activeCats } = useCatList("ACTIVE", isDesktop);
  const setExportCat = useExportWizardStore((s) => s.setCat);
  const archiveCat = useArchiveCat(catId ?? "");
  const unarchiveCat = useUnarchiveCat(catId ?? "");
  const setPrimary = useSetPrimaryCat(catId ?? "");
  const softDelete = useSoftDeleteCat(catId ?? "");

  const [menuOpen, setMenuOpen] = useState(false);
  const [confirmAction, setConfirmAction] = useState<"archive" | "unarchive" | "delete" | "setPrimary" | null>(null);
  const [surveyDialogOpen, setSurveyDialogOpen] = useState(false);
  const [signsDialogOpen, setSignsDialogOpen] = useState(false);
  const [noteDialogOpen, setNoteDialogOpen] = useState(false);
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null);
  const [deletingNoteId, setDeletingNoteId] = useState<string | null>(null);

  if (!catId) return null;

  if (isPending) {
    return (
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-5 lg:max-w-[1200px] lg:px-0 lg:py-0">
        <SkeletonLoader shape="circle" className="mx-auto size-28" />
        <SkeletonLoader shape="text" className="mx-auto w-40" />
        <SkeletonLoader shape="card" />
      </div>
    );
  }

  if (isError) {
    return (
      <ErrorState
        title={t("detail.notFoundTitle")}
        description={t("detail.notFoundDescription")}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  }

  const flagged = (cat.unacknowledgedFlagCount ?? 0) > 0;

  const runAction = async () => {
    try {
      if (confirmAction === "archive") {
        await archiveCat.mutateAsync();
        toast.success(t("confirm.archive.confirm"));
      } else if (confirmAction === "unarchive") {
        await unarchiveCat.mutateAsync();
        toast.success(t("confirm.unarchive.confirm"));
      } else if (confirmAction === "delete") {
        await softDelete.mutateAsync();
        toast.success(t("confirm.delete.confirm"));
        void navigate("/cats");
        return;
      } else if (confirmAction === "setPrimary") {
        await setPrimary.mutateAsync();
        toast.success(t("confirm.setPrimary.confirm"));
      }
      setConfirmAction(null);
    } catch (error) {
      const code = isApiError(error) ? error.code : undefined;
      const messageKey =
        code === "CAT_ALREADY_DELETED"
          ? "confirm.delete.alreadyDeleted"
          : code === "CAT_ALREADY_PRIMARY"
            ? "confirm.setPrimary.alreadyPrimary"
            : code === "CAT_PRIMARY_REQUIRES_ACTIVE"
              ? "confirm.setPrimary.requiresActive"
              : "errors.generic";
      toast.error(t(messageKey));
      setConfirmAction(null);
    }
  };

  const actionLoading = archiveCat.isPending || unarchiveCat.isPending || softDelete.isPending || setPrimary.isPending;

  const openExport = () => {
    // Chọn sẵn đúng bé đang xem ở bước 1 của màn xuất hồ sơ.
    setExportCat({ id: cat.id, name: cat.name });
    void navigate("/export");
  };

  const menuButton = (
    <Button
      type="button"
      variant="tertiary"
      size="sm"
      aria-label={t("actions.moreActions")}
      className="border-0 bg-surface/90"
      onClick={() => {
        setMenuOpen(true);
      }}
    >
      <MoreVertical className="size-5" aria-hidden="true" />
    </Button>
  );

  const openSurvey = () => {
    setSurveyDialogOpen(true);
  };
  const openSigns = () => {
    setSignsDialogOpen(true);
  };
  const addNote = () => {
    setEditingNoteId(null);
    setNoteDialogOpen(true);
  };
  const editNote = (noteId: string) => {
    setEditingNoteId(noteId);
    setNoteDialogOpen(true);
  };

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-5 lg:max-w-[1200px] lg:px-0 lg:py-0">
      {isDesktop ? (
        <>
          <CatProfileBar cats={activeCats?.items ?? []} currentCat={cat} onExport={openExport} />

          <div className="grid grid-cols-12 items-start gap-6">
            <div className="col-span-5 flex flex-col gap-6">
              <CatProfileHero cat={cat} menu={menuButton} />
              <EmergencyDisclaimerBanner forceExpanded={flagged} />
              {/* `unacknowledgedFlagCount` (D10/D12) bấm được: bung danh sách THẬT từ G1 và
                  xác nhận từng mục bằng G3. */}
              <HealthFlagDisclosure catId={catId} count={cat.unacknowledgedFlagCount ?? 0} />
              <CatInfoCard cat={cat} />
              <CatIdentityExtras />
            </div>

            <div className="col-span-7">
              <CatClinicalColumn
                cat={cat}
                summary={catSummary}
                bands={phBands ?? []}
                scanCount={catScanSummary?.count ?? null}
                onViewAllHistory={() => {
                  void navigate(`/cats/${catId}/history`);
                }}
                onOpenScan={(scanId) => {
                  void navigate(`/scans/${scanId}`);
                }}
                onAddNote={addNote}
                onEditNote={editNote}
                onDeleteNote={setDeletingNoteId}
                footer={
                  <div className="grid grid-cols-2 gap-5">
                    <SurveyCard catId={catId} onOpen={openSurvey} />
                    <ClinicalSignsCard onOpen={openSigns} />
                  </div>
                }
              />
            </div>
          </div>
        </>
      ) : (
        <>
          <CatProfileHero cat={cat} menu={menuButton} />
          <EmergencyDisclaimerBanner forceExpanded={flagged} />
          <HealthFlagDisclosure catId={catId} count={cat.unacknowledgedFlagCount ?? 0} />

          <Tabs defaultValue="overview">
            <TabsList>
              <TabsTrigger value="overview">{t("detail.tabs.overview")}</TabsTrigger>
              <TabsTrigger value="history">{t("detail.tabs.history")}</TabsTrigger>
              <TabsTrigger value="notes">{t("detail.tabs.notes")}</TabsTrigger>
            </TabsList>

            <TabsContent value="overview">
              <OverviewTab
                catId={catId}
                cat={cat}
                summary={catSummary}
                onOpenSurvey={openSurvey}
                onOpenSigns={openSigns}
              />
            </TabsContent>

            <TabsContent value="history">
              <HistoryTab catId={catId} />
            </TabsContent>

            <TabsContent value="notes">
              <NotesTab catId={catId} onAdd={addNote} onEdit={editNote} onDelete={setDeletingNoteId} />
            </TabsContent>
          </Tabs>
        </>
      )}

      {/* Dialog "thao tác khác" dựng MỘT lần, nút ⋮ trên thẻ nhận diện chỉ mở nó. */}
      <Dialog open={menuOpen} onOpenChange={setMenuOpen}>
        <DialogContent aria-label={t("actions.moreActions")}>
          <DialogTitle>{t("actions.moreActions")}</DialogTitle>
          <div className="mt-4 flex flex-col gap-1">
            <ListItem
              leading={<Pencil className="size-5" aria-hidden="true" />}
              title={t("actions.edit")}
              onClick={() => {
                setMenuOpen(false);
                void navigate(`/cats/${catId}/edit`);
              }}
            />
            {!cat.isPrimary && cat.status === "ACTIVE" ? (
              <ListItem
                leading={<Crown className="size-5" aria-hidden="true" />}
                title={t("actions.setPrimary")}
                onClick={() => {
                  setMenuOpen(false);
                  setConfirmAction("setPrimary");
                }}
              />
            ) : null}
            {cat.status === "ACTIVE" ? (
              <ListItem
                leading={<Archive className="size-5" aria-hidden="true" />}
                title={t("actions.archive")}
                onClick={() => {
                  setMenuOpen(false);
                  setConfirmAction("archive");
                }}
              />
            ) : (
              <ListItem
                leading={<ArchiveRestore className="size-5" aria-hidden="true" />}
                title={t("actions.unarchive")}
                onClick={() => {
                  setMenuOpen(false);
                  setConfirmAction("unarchive");
                }}
              />
            )}
            <ListItem
              leading={<Trash2 className="size-5 text-danger-text" aria-hidden="true" />}
              title={<span className="text-danger-text">{t("actions.delete")}</span>}
              onClick={() => {
                setMenuOpen(false);
                setConfirmAction("delete");
              }}
            />
          </div>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={confirmAction !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmAction(null);
        }}
        title={
          confirmAction === "archive"
            ? t("confirm.archive.title", { name: cat.name })
            : confirmAction === "unarchive"
              ? t("confirm.unarchive.title", { name: cat.name })
              : confirmAction === "setPrimary"
                ? t("confirm.setPrimary.title", { name: cat.name })
                : t("confirm.delete.title", { name: cat.name })
        }
        description={
          confirmAction === "archive"
            ? t("confirm.archive.description")
            : confirmAction === "unarchive"
              ? t("confirm.unarchive.description")
              : confirmAction === "setPrimary"
                ? t("confirm.setPrimary.description")
                : t("confirm.delete.description")
        }
        confirmLabel={
          confirmAction === "archive"
            ? t("confirm.archive.confirm")
            : confirmAction === "unarchive"
              ? t("confirm.unarchive.confirm")
              : confirmAction === "setPrimary"
                ? t("confirm.setPrimary.confirm")
                : t("confirm.delete.confirm")
        }
        cancelLabel={
          confirmAction === "archive"
            ? t("confirm.archive.cancel")
            : confirmAction === "unarchive"
              ? t("confirm.unarchive.cancel")
              : confirmAction === "setPrimary"
                ? t("confirm.setPrimary.cancel")
                : t("confirm.delete.cancel")
        }
        destructive={confirmAction === "delete"}
        loading={actionLoading}
        onConfirm={() => {
          void runAction();
        }}
      />

      <HealthSurveyDialog catId={catId} catName={cat.name} open={surveyDialogOpen} onOpenChange={setSurveyDialogOpen} />
      <ClinicalSignsDialog catId={catId} open={signsDialogOpen} onOpenChange={setSignsDialogOpen} />
      <NoteFormDialog catId={catId} noteId={editingNoteId} open={noteDialogOpen} onOpenChange={setNoteDialogOpen} />
      <DeleteNoteConfirm
        catId={catId}
        noteId={deletingNoteId}
        onClose={() => {
          setDeletingNoteId(null);
        }}
      />
    </div>
  );
}

/* ================================================================== Thông tin hồ sơ */

/** Bảng thông tin hồ sơ — toàn bộ từ `GET /cats/{id}`; ghi chú chung là `notes` của chính bé. */
function CatInfoCard({ cat }: { cat: Cat }) {
  const { t } = useTranslation("cat");
  const formatAge = useFormatCatAge();

  const rows: { label: string; value: string }[] = [
    { label: t("detail.breedLabel"), value: cat.breedName ?? cat.breedOther ?? "—" },
    {
      label: t("form.sex.label"),
      value: t(`form.sex.${cat.sex === "MALE" ? "male" : cat.sex === "FEMALE" ? "female" : "unknown"}`),
    },
    { label: t("detail.ageFieldLabel"), value: formatAge(cat.ageMonths) ?? "—" },
    {
      label: t("form.weight.label"),
      value: cat.weightKg !== null ? t("detail.weightLabel", { weight: cat.weightKg }) : "—",
    },
    { label: t("detail.coatColorLabel"), value: cat.coatColor ?? "—" },
    {
      label: t("detail.neuteredLabel"),
      value:
        cat.neutered === true
          ? t("detail.neuteredYes")
          : cat.neutered === false
            ? t("detail.neuteredNo")
            : t("detail.neuteredUnknown"),
    },
  ];

  return (
    <Card className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-body font-bold text-text-primary">{t("web.profile.infoTitle")}</h2>
        <Link
          to={`/cats/${cat.id}/edit`}
          className="inline-flex min-h-9 items-center gap-1.5 rounded-lg px-2 text-caption font-semibold text-primary-dark hover:bg-background-alt"
        >
          <Pencil className="size-3.5" aria-hidden="true" />
          {t("actions.edit")}
        </Link>
      </div>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
        {rows.map((row) => (
          <div key={row.label} className="flex min-w-0 flex-col gap-0.5">
            <dt className="text-caption text-text-secondary">{row.label}</dt>
            <dd className="break-words text-body font-medium text-text-primary">{row.value}</dd>
          </div>
        ))}
      </dl>
      {cat.notes ? (
        <div className="border-t border-border pt-4">
          <p className="text-caption text-text-secondary">{t("detail.notesLabel")}</p>
          <p className="mt-1 whitespace-pre-wrap break-words text-body text-text-primary">{cat.notes}</p>
        </div>
      ) : null}
    </Card>
  );
}

/* ================================================================== Thẻ hành động */

function SurveyCard({ catId, onOpen, className }: { catId: string; onOpen: () => void; className?: string }) {
  const { t, i18n } = useTranslation("cat");
  const { data: survey, isPending } = useHealthSurvey(catId);

  return (
    <Card className={cn("flex flex-col gap-3", className)}>
      <SectionHeading>{t("survey.title")}</SectionHeading>
      {isPending ? (
        <SkeletonLoader shape="text" />
      ) : !survey || survey.skipped ? (
        <p className="text-caption text-text-secondary">{t("survey.notSubmittedDescription")}</p>
      ) : survey.submittedAt ? (
        <p className="text-caption text-text-secondary">
          {t("survey.lastSubmittedAt", { date: new Date(survey.submittedAt).toLocaleDateString(i18n.language) })}
        </p>
      ) : null}
      <Button type="button" variant="tertiary" size="sm" onClick={onOpen} className="mt-auto w-fit">
        {survey && !survey.skipped ? t("survey.updateCta") : t("survey.startCta")}
      </Button>
    </Card>
  );
}

function ClinicalSignsCard({ onOpen, className }: { onOpen: () => void; className?: string }) {
  const { t } = useTranslation("cat");
  return (
    <Card className={cn("flex flex-col gap-3", className)}>
      <SectionHeading hint={t("clinicalSigns.subtitle")}>{t("clinicalSigns.title")}</SectionHeading>
      <Button
        type="button"
        variant="secondary"
        size="sm"
        leftIcon={<Stethoscope className="size-4" aria-hidden="true" />}
        onClick={onOpen}
        className="mt-auto w-fit"
      >
        {t("clinicalSigns.cta")}
      </Button>
    </Card>
  );
}

/* ================================================================== Overview tab (mobile) */

function OverviewTab({
  catId,
  cat,
  summary,
  onOpenSurvey,
  onOpenSigns,
}: {
  catId: string;
  cat: Cat;
  summary: CatSummaryResponse | undefined;
  onOpenSurvey: () => void;
  onOpenSigns: () => void;
}) {
  const { t } = useTranslation(["cat", "history"]);
  const navigate = useNavigate();
  const { data: bands } = usePhBands();
  const { data: scanSummary } = useScanSummary(catId);
  const setScanCat = useScanCaptureStore((s) => s.setSelectedCat);

  const timestampLabel = (iso: string) =>
    formatScanTimestamp(iso, t("timestamp.today", { ns: "history" }), t("timestamp.yesterday", { ns: "history" }));

  return (
    <div className="flex flex-col gap-4 pt-4">
      {/* Mockup `06` — khối chỉ thị sinh học + 4 chỉ số + lần quét gần đây. */}
      <CatBiomarkerCard bands={bands ?? []} summary={summary} />
      <CatStatGrid catId={catId} summary={summary} scanSummary={scanSummary} />
      <CatRecentScansCard
        catId={catId}
        bands={bands ?? []}
        totalCount={scanSummary?.count ?? 0}
        timestampLabel={timestampLabel}
        onOpenScan={(scanId) => {
          void navigate(`/scans/${scanId}`);
        }}
        onViewAll={() => {
          void navigate(`/cats/${catId}/history`);
        }}
      />

      <CatInfoCard cat={cat} />
      <SurveyCard catId={catId} onOpen={onOpenSurvey} />
      <ClinicalSignsCard onOpen={onOpenSigns} />

      {/* Hai nút hành động chính + ghi chú miễn trừ cuối màn (mockup `06`). */}
      <CatProfileActions
        name={cat.name}
        onScan={() => {
          // "Bắt đầu quét mới cho {tên}" — chọn sẵn đúng bé rồi vào thẳng bước chụp.
          setScanCat({ id: cat.id, name: cat.name });
          void navigate("/scan");
        }}
        onEdit={() => {
          void navigate(`/cats/${catId}/edit`);
        }}
      />
      <p className="text-small leading-relaxed text-text-tertiary">{t("disclaimer.short")}</p>
    </div>
  );
}

/* ================================================================== History tab */

/**
 * Tab "Lịch sử sức khoẻ" của mockup `06` — timeline quét THẬT (`GET /scans`), gom nhóm
 * theo tháng như màn `/cats/:catId/history`, kèm link sang màn đầy đủ.
 */
function HistoryTab({ catId }: { catId: string }) {
  const { t } = useTranslation(["cat", "history", "common"]);
  const navigate = useNavigate();
  const { data: bands } = usePhBands();
  const { data, isPending, isError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useScanHistory(
    catId,
    "ALL",
  );

  const items = data?.pages.flatMap((p) => p.items) ?? [];
  const groups = new Map<string, typeof items>();
  items.forEach((item) => {
    const key = monthGroupKey(item.capturedAt);
    groups.set(key, [...(groups.get(key) ?? []), item]);
  });

  if (isPending) return <SkeletonLoader shape="card" className="mt-4 h-40" />;
  if (isError) {
    return (
      <ErrorState
        className="mt-4"
        title={t("state.loadError", { ns: "history" })}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  }
  if (items.length === 0) {
    return (
      <EmptyState
        className="mt-4"
        title={t("state.emptyTitle", { ns: "history" })}
        description={t("state.emptyDescription", { ns: "history" })}
        action={
          <Button
            type="button"
            onClick={() => {
              void navigate("/scan/select-cat");
            }}
          >
            {t("state.emptyCta", { ns: "history" })}
          </Button>
        }
      />
    );
  }

  return (
    <div className="flex flex-col gap-4 pt-4">
      {[...groups.entries()].map(([key, group]) => {
        const { month, year } = parseMonthGroupKey(key);
        return (
          <div key={key} className="flex flex-col gap-2">
            <MonthGroupHeader
              label={t("monthGroup.label", { ns: "history", month, year })}
              count={t("monthGroup.count", { ns: "history", count: group.length })}
            />
            {group.map((scan) => (
              <ScanTimelineItem
                key={scan.scanId}
                scan={scan}
                bands={bands ?? []}
                timestampLabel={formatScanTimestamp(
                  scan.capturedAt,
                  t("timestamp.today", { ns: "history" }),
                  t("timestamp.yesterday", { ns: "history" }),
                )}
                disputedLabel={t("item.disputedBadge", { ns: "history" })}
                detailLabel={t("item.detailLink", { ns: "history" })}
                onOpen={(scanId) => {
                  void navigate(`/scans/${scanId}`);
                }}
              />
            ))}
          </div>
        );
      })}

      {hasNextPage ? (
        <Button
          type="button"
          variant="tertiary"
          loading={isFetchingNextPage}
          onClick={() => {
            void fetchNextPage();
          }}
        >
          {t("state.loadMore", { ns: "history" })}
        </Button>
      ) : null}

      <Button
        type="button"
        variant="tertiary"
        onClick={() => {
          void navigate(`/cats/${catId}/history`);
        }}
      >
        {t("overview.recentAllShort")}
      </Button>
    </div>
  );
}

/* ================================================================== Notes tab */

function NotesTab({
  catId,
  onAdd,
  onEdit,
  onDelete,
}: {
  catId: string;
  onAdd: () => void;
  onEdit: (noteId: string) => void;
  onDelete: (noteId: string) => void;
}) {
  const { t } = useTranslation("cat");
  const { data, isPending, isError, refetch } = useCatNotes(catId);
  const items = data?.items ?? [];

  return (
    <div className="flex flex-col gap-4 pt-4">
      <div className="flex justify-end">
        <Button type="button" size="sm" leftIcon={<Plus className="size-4" aria-hidden="true" />} onClick={onAdd}>
          {t("actions.addNote")}
        </Button>
      </div>

      {isPending ? (
        <SkeletonLoader shape="card" />
      ) : isError ? (
        <ErrorState
          title={t("notes.errorTitle")}
          onRetry={() => {
            void refetch();
          }}
        />
      ) : items.length === 0 ? (
        <EmptyState title={t("notes.emptyTitle")} description={t("notes.emptyDescription")} />
      ) : (
        <ul className="flex flex-col gap-2">
          {items.map((note) => (
            <li key={note.id}>
              <Card className="flex flex-col gap-1">
                <div className="flex items-center justify-between gap-2">
                  <Badge tone="neutral">{t(`notes.types.${note.noteType}`)}</Badge>
                  <div className="flex gap-1">
                    <Button
                      type="button"
                      variant="tertiary"
                      size="sm"
                      onClick={() => {
                        onEdit(note.id);
                      }}
                    >
                      {t("notes.editAction")}
                    </Button>
                    <Button
                      type="button"
                      variant="tertiary"
                      size="sm"
                      onClick={() => {
                        onDelete(note.id);
                      }}
                    >
                      {t("notes.deleteAction")}
                    </Button>
                  </div>
                </div>
                <p className="whitespace-pre-wrap text-body text-text-primary">{note.body}</p>
                <p className="text-small text-text-tertiary">
                  {new Date(note.occurredOn ?? note.createdAt).toLocaleDateString("vi-VN")}
                </p>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/* ================================================================== Health survey dialog */

const EMPTY_SURVEY_ANSWERS: CatHealthSurveyAnswers = {
  litterType: "",
  urinaryHistory: "",
  dietType: "",
  urinationFrequency: "",
  symptoms: [],
};

function HealthSurveyDialog({
  catId,
  catName,
  open,
  onOpenChange,
}: {
  catId: string;
  catName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation("cat");
  const { data: survey } = useHealthSurvey(catId);
  const submitSurvey = useSubmitHealthSurvey(catId);

  const {
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<SurveyFormSchemaValues>({
    resolver: zodResolver(surveyFormSchema),
    defaultValues: EMPTY_SURVEY_ANSWERS,
  });

  const watchedSymptoms = watch("symptoms");

  const handleOpenChange = (next: boolean) => {
    if (next) {
      reset(survey?.answers ?? EMPTY_SURVEY_ANSWERS);
    }
    onOpenChange(next);
  };

  const handleSymptomChange = (value: string, checked: boolean) => {
    let next: string[];
    if (value === "NONE") {
      next = checked ? ["NONE"] : [];
    } else {
      next = checked
        ? [...watchedSymptoms.filter((s) => s !== "NONE"), value]
        : watchedSymptoms.filter((s) => s !== value);
    }
    setValue("symptoms", next, { shouldValidate: true });
  };

  const onSubmit = async (values: SurveyFormSchemaValues) => {
    try {
      await submitSurvey.mutateAsync({ answers: values as CatHealthSurveyAnswers, skipped: false });
      toast.success(t("survey.submit"));
      onOpenChange(false);
    } catch {
      toast.error(t("errors.generic"));
    }
  };

  const questionOptions = (key: string): Record<string, string> =>
    t(key, { returnObjects: true }) as Record<string, string>;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        <DialogTitle>{t("survey.title")}</DialogTitle>
        <p className="mt-1 text-caption text-text-secondary">{t("survey.subtitle", { catName })}</p>

        <div className="mt-4 flex flex-col gap-5">
          {SURVEY_QUESTION_NAMES.map((name) => (
            <fieldset key={name} className="flex flex-col gap-2">
              <legend className="text-body font-semibold text-text-primary">
                {t(`survey.questions.${name}.label`)}
              </legend>
              <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                {Object.entries(questionOptions(`survey.questions.${name}.options`)).map(([value, label]) => (
                  <OptionCard
                    key={value}
                    name={name}
                    value={value}
                    checked={watch(name) === value}
                    onChange={(v) => {
                      setValue(name, v, { shouldValidate: true });
                    }}
                    title={label}
                  />
                ))}
              </div>
              <FieldError message={errors[name]?.message} />
            </fieldset>
          ))}

          <fieldset className="flex flex-col gap-2">
            <legend className="text-body font-semibold text-text-primary">
              {t("survey.questions.symptoms.label")}
            </legend>
            <p className="text-caption text-text-tertiary">{t("survey.questions.symptoms.hint")}</p>
            <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
              {Object.entries(questionOptions("survey.questions.symptoms.options")).map(([value, label]) => (
                <OptionCard
                  key={value}
                  type="checkbox"
                  name="symptoms"
                  value={value}
                  checked={watchedSymptoms.includes(value)}
                  onChange={() => {
                    handleSymptomChange(value, !watchedSymptoms.includes(value));
                  }}
                  title={label}
                />
              ))}
            </div>
            <FieldError message={errors.symptoms?.message} />
          </fieldset>
        </div>

        <div className="mt-6 flex flex-col gap-3">
          <Button
            type="button"
            loading={submitSurvey.isPending}
            onClick={() => {
              void handleSubmit(onSubmit)();
            }}
          >
            {submitSurvey.isPending ? t("survey.saving") : t("survey.submit")}
          </Button>
          <Button
            type="button"
            variant="tertiary"
            onClick={() => {
              onOpenChange(false);
            }}
          >
            {t("survey.cancel")}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/* ================================================================== Clinical signs dialog */

function ClinicalSignsDialog({
  catId,
  open,
  onOpenChange,
}: {
  catId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation("cat");
  const reportSigns = useReportClinicalSigns(catId);
  const [acknowledged, setAcknowledged] = useState(false);
  const [result, setResult] = useState<{ triggered: boolean } | null>(null);

  const {
    handleSubmit,
    watch,
    setValue,
    register,
    reset,
    formState: { errors },
  } = useForm<ClinicalSignFormSchemaValues>({
    resolver: zodResolver(clinicalSignFormSchema),
    defaultValues: { signs: [], note: "" },
  });

  const handleOpenChange = (next: boolean) => {
    if (next) {
      reset({ signs: [], note: "" });
      setAcknowledged(false);
      setResult(null);
    }
    onOpenChange(next);
  };

  const onSubmit = async (values: ClinicalSignFormSchemaValues) => {
    if (!acknowledged) return;
    try {
      const response = await reportSigns.mutateAsync({
        signs: values.signs,
        source: "MANUAL",
        note: values.note || undefined,
      });
      setResult({ triggered: response.triggeredFlag !== null });
    } catch {
      toast.error(t("errors.generic"));
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        <DialogTitle>{t("clinicalSigns.title")}</DialogTitle>

        {result ? (
          <div className="mt-4 flex flex-col gap-4 text-center">
            <p className="text-body font-bold text-text-primary">
              {result.triggered ? t("clinicalSigns.result.triggeredTitle") : t("clinicalSigns.result.okTitle")}
            </p>
            <p className="text-caption text-text-secondary">
              {result.triggered
                ? t("clinicalSigns.result.triggeredDescription")
                : t("clinicalSigns.result.okDescription")}
            </p>
            {result.triggered ? <ClinicalSignEmergencyNotice /> : null}
            <Button
              type="button"
              onClick={() => {
                onOpenChange(false);
              }}
            >
              {t("clinicalSigns.result.close")}
            </Button>
          </div>
        ) : (
          <div className="mt-4 flex flex-col gap-4">
            <p className="text-caption text-text-secondary">{t("clinicalSigns.subtitle")}</p>
            <ClinicalSignEmergencyNotice />

            <ClinicalSignPicker
              value={watch("signs") as ClinicalSign[]}
              onChange={(signs) => {
                setValue("signs", signs, { shouldValidate: true });
              }}
              error={errors.signs?.message}
            />

            <div className="flex flex-col gap-1.5">
              <label htmlFor="clinical-sign-note" className="text-caption font-semibold text-text-secondary">
                {t("clinicalSigns.form.note.label")}
              </label>
              <textarea
                id="clinical-sign-note"
                rows={3}
                placeholder={t("clinicalSigns.form.note.placeholder")}
                className="rounded-md border border-border bg-surface px-3 py-2 text-body text-text-primary placeholder:text-text-tertiary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
                {...register("note")}
              />
              <FieldError message={errors.note?.message} />
            </div>

            <label className="flex min-h-11 cursor-pointer items-start gap-3 rounded-xl border border-border bg-surface p-3">
              <input
                type="checkbox"
                checked={acknowledged}
                onChange={(event) => {
                  setAcknowledged(event.target.checked);
                }}
                className="mt-0.5 size-5 shrink-0 accent-primary"
              />
              <span className="text-body text-text-primary">{t("clinicalSigns.form.acknowledge")}</span>
            </label>
            {!acknowledged ? (
              <p className="text-small text-text-tertiary">{t("clinicalSigns.form.acknowledgeRequired")}</p>
            ) : null}

            <div className="flex flex-col gap-3">
              <Button
                type="button"
                loading={reportSigns.isPending}
                disabled={!acknowledged}
                onClick={() => {
                  void handleSubmit(onSubmit)();
                }}
              >
                {reportSigns.isPending ? t("clinicalSigns.form.saving") : t("clinicalSigns.form.submit")}
              </Button>
              <Button
                type="button"
                variant="tertiary"
                onClick={() => {
                  onOpenChange(false);
                }}
              >
                {t("clinicalSigns.form.cancel")}
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

/* ================================================================== Note form dialog */

function NoteFormDialog({
  catId,
  noteId,
  open,
  onOpenChange,
}: {
  catId: string;
  noteId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation("cat");
  const { data } = useCatNotes(catId);
  const createNote = useCreateNote(catId);
  const patchNote = usePatchNote(catId, noteId ?? "");
  const existing = data?.items.find((n) => n.id === noteId) ?? null;

  const {
    handleSubmit,
    register,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<NoteFormSchemaValues>({
    resolver: zodResolver(noteFormSchema),
    defaultValues: { noteType: "GENERAL", body: "", occurredOn: "" },
  });

  const handleOpenChange = (next: boolean) => {
    if (next) {
      reset(
        existing
          ? { noteType: existing.noteType, body: existing.body, occurredOn: existing.occurredOn ?? "" }
          : { noteType: "GENERAL", body: "", occurredOn: "" },
      );
    }
    onOpenChange(next);
  };

  const isSaving = createNote.isPending || patchNote.isPending;

  const onSubmit = async (values: NoteFormSchemaValues) => {
    try {
      if (existing) {
        await patchNote.mutateAsync({
          noteType: values.noteType as NoteType,
          body: values.body,
          occurredOn: values.occurredOn || null,
        });
      } else {
        await createNote.mutateAsync({
          noteType: values.noteType as NoteType,
          body: values.body,
          occurredOn: values.occurredOn || null,
        });
      }
      onOpenChange(false);
    } catch {
      toast.error(t("errors.generic"));
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogTitle>{existing ? t("notes.editTitle") : t("notes.addTitle")}</DialogTitle>
        <div className="mt-4 flex flex-col gap-4">
          <fieldset className="flex flex-col gap-2">
            <legend className="text-caption font-semibold text-text-secondary">{t("notes.form.noteType.label")}</legend>
            <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
              {NOTE_TYPES.map((type) => (
                <OptionCard
                  key={type}
                  name="noteType"
                  value={type}
                  checked={watch("noteType") === type}
                  onChange={(v) => {
                    setValue("noteType", v as NoteType, { shouldValidate: true });
                  }}
                  title={t(`notes.types.${type}`)}
                />
              ))}
            </div>
          </fieldset>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="note-body" className="text-caption font-semibold text-text-secondary">
              {t("notes.form.body.label")}
            </label>
            <textarea
              id="note-body"
              rows={4}
              placeholder={t("notes.form.body.placeholder")}
              className="rounded-md border border-border bg-surface px-3 py-2 text-body text-text-primary placeholder:text-text-tertiary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
              {...register("body")}
            />
            <FieldError message={errors.body?.message} />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="note-occurred-on" className="text-caption font-semibold text-text-secondary">
              {t("notes.form.occurredOn.label")}
            </label>
            <input
              id="note-occurred-on"
              type="date"
              max={new Date().toISOString().slice(0, 10)}
              className={cn(
                "min-h-11 rounded-md border bg-surface px-3 text-body text-text-primary",
                "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
                errors.occurredOn ? "border-danger" : "border-border",
              )}
              {...register("occurredOn")}
            />
            <FieldError message={errors.occurredOn?.message} />
          </div>

          <div className="flex flex-col gap-3">
            <Button
              type="button"
              loading={isSaving}
              onClick={() => {
                void handleSubmit(onSubmit)();
              }}
            >
              {isSaving ? t("notes.form.saving") : t("notes.form.submit")}
            </Button>
            <Button
              type="button"
              variant="tertiary"
              onClick={() => {
                onOpenChange(false);
              }}
            >
              {t("actions.cancel", { ns: "common" })}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/* ================================================================== Delete note confirm */

function DeleteNoteConfirm({ catId, noteId, onClose }: { catId: string; noteId: string | null; onClose: () => void }) {
  const { t } = useTranslation("cat");
  const deleteNote = useDeleteNote(catId, noteId ?? "");

  return (
    <ConfirmDialog
      open={noteId !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
      title={t("notes.deleteTitle")}
      description={t("notes.deleteDescription")}
      confirmLabel={t("notes.deleteConfirm")}
      cancelLabel={t("notes.deleteCancel")}
      destructive
      loading={deleteNote.isPending}
      onConfirm={() => {
        deleteNote.mutate(undefined, {
          onSuccess: onClose,
          onError: () => {
            toast.error(t("errors.generic"));
          },
        });
      }}
    />
  );
}
