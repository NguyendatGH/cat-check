import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Dialog, DialogContent, DialogDescription, DialogTitle, Input } from "@/shared/ui";
import {
  AccessDeniedState,
  AdminPageHeader,
  AdminSelect,
  DensePanel,
  MutationErrorNote,
  ReasonField,
  TextAreaField,
  isReasonValid,
  useUpdateAdminMaintenance,
} from "@/features/admin";
import {
  BROADCAST_BODY_MAX,
  BROADCAST_TITLE_MAX,
  INCIDENT_TEMPLATE,
  allowedBroadcastTemplates,
  buildBroadcastRequest,
  useAdminBroadcast,
  useGroup3Can,
  validateBroadcastForm,
  type BroadcastForm,
} from "./group3Ops";

export function AdminBroadcastPage() {
  const { t } = useTranslation("admin");
  const can = useGroup3Can();
  const templates = allowedBroadcastTemplates(can);
  if (!can("broadcast") && templates.length === 0) {
    return <AccessDeniedState />;
  }
  return (
    <div className="flex max-w-4xl flex-col gap-4">
      <AdminPageHeader title={t("pages.broadcast.title")} description={t("pages.broadcast.description")} />
      {can("broadcast") ? <MaintenancePanel /> : null}
      <BroadcastPanel templates={templates} />
    </div>
  );
}

function MaintenancePanel() {
  const { t } = useTranslation("admin");
  const [active, setActive] = useState(false);
  const [until, setUntil] = useState("");
  const [reason, setReason] = useState("");
  const update = useUpdateAdminMaintenance();
  const canSave = isReasonValid(reason);
  return (
    <DensePanel title={t("pages.broadcast.maintenanceTitle")} description={t("pages.broadcast.maintenanceDescription")}>
      <label className="flex items-center gap-3 text-caption text-text-primary">
        <input
          type="checkbox"
          checked={active}
          onChange={(event) => {
            setActive(event.target.checked);
          }}
        />
        {t("pages.broadcast.maintenanceActive")}
      </label>
      <Input
        label={t("pages.broadcast.until")}
        value={until}
        onChange={(event) => {
          setUntil(event.target.value);
        }}
        placeholder="2026-10-04T18:00:00+07:00"
      />
      <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
      <div className="flex justify-end">
        <Button
          size="sm"
          disabled={!canSave || update.isPending}
          onClick={() => {
            update.mutate({ active, until: until.trim() || null, reason: reason.trim() });
          }}
        >
          {t("pages.broadcast.saveMaintenance")}
        </Button>
      </div>
      <MutationErrorNote error={update.error} />
    </DensePanel>
  );
}

function BroadcastPanel({ templates }: { templates: readonly string[] }) {
  const { t } = useTranslation("admin");
  const [form, setForm] = useState<BroadcastForm>({
    templateCode: templates[0] ?? "",
    title: "",
    body: "",
    reason: "",
  });
  const [submitted, setSubmitted] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const broadcast = useAdminBroadcast();

  if (templates.length === 0) {
    return null;
  }
  const errors = validateBroadcastForm(form);
  const set = (patch: Partial<BroadcastForm>) => {
    setForm((current) => ({ ...current, ...patch }));
  };

  const runDry = () => {
    setSubmitted(true);
    const request = buildBroadcastRequest(form, "dry", false);
    if (request !== null) broadcast.mutate(request);
  };
  const runSend = () => {
    const request = buildBroadcastRequest(form, "send", confirmed);
    if (request === null) return;
    broadcast.mutate(request, {
      onSettled: () => {
        setConfirmOpen(false);
        setConfirmed(false);
      },
    });
  };
  const openConfirm = () => {
    setSubmitted(true);
    if (errors.length === 0) {
      setConfirmed(false);
      setConfirmOpen(true);
    }
  };

  return (
    <DensePanel title={t("broadcastOps.title")} description={t("broadcastOps.description")}>
      <AdminSelect
        label={t("broadcastOps.template")}
        value={form.templateCode}
        onChange={(event) => {
          set({ templateCode: event.target.value });
        }}
      >
        {templates.map((code) => (
          <option key={code} value={code}>
            {t(`broadcastOps.templates.${code}`, { defaultValue: code })}
          </option>
        ))}
      </AdminSelect>
      {form.templateCode === INCIDENT_TEMPLATE ? (
        <p className="rounded-lg bg-warning-bg px-3 py-2 text-caption text-warning-text">
          {t("broadcastOps.incidentWarning")}
        </p>
      ) : null}
      <Input
        label={t("broadcastOps.titleField")}
        value={form.title}
        maxLength={BROADCAST_TITLE_MAX}
        error={submitted && errors.includes("title") ? t("broadcastOps.titleRequired") : undefined}
        onChange={(event) => {
          set({ title: event.target.value });
        }}
      />
      <TextAreaField
        label={t("broadcastOps.bodyField")}
        value={form.body}
        rows={5}
        maxLength={BROADCAST_BODY_MAX}
        error={submitted && errors.includes("body") ? t("broadcastOps.bodyRequired") : undefined}
        onChange={(value) => {
          set({ body: value });
        }}
      />
      <ReasonField
        value={form.reason}
        onChange={(value) => {
          set({ reason: value });
        }}
        showError={submitted}
      />
      <div className="flex flex-wrap justify-end gap-2">
        <Button size="sm" variant="tertiary" disabled={broadcast.isPending} onClick={runDry}>
          {t("broadcastOps.dryRun")}
        </Button>
        <Button size="sm" disabled={broadcast.isPending} onClick={openConfirm}>
          {t("broadcastOps.send")}
        </Button>
      </div>
      {broadcast.data ? (
        <p role="status" className="rounded-lg bg-info/60 px-3 py-2 text-caption text-text-primary">
          {broadcast.data.dryRun
            ? t("broadcastOps.resultDry", { matched: broadcast.data.recipientsMatched })
            : t("broadcastOps.resultSent", {
                matched: broadcast.data.recipientsMatched,
                queued: broadcast.data.notificationsQueued,
              })}
        </p>
      ) : null}
      <MutationErrorNote error={broadcast.error} />

      <Dialog
        open={confirmOpen}
        onOpenChange={(open) => {
          if (!open && !broadcast.isPending) setConfirmOpen(false);
        }}
      >
        <DialogContent className="max-w-md">
          <DialogTitle>{t("broadcastOps.confirmTitle")}</DialogTitle>
          <DialogDescription>
            {t("broadcastOps.confirmBody", {
              template: t(`broadcastOps.templates.${form.templateCode}`, { defaultValue: form.templateCode }),
            })}
          </DialogDescription>
          <p className="pt-2 text-caption font-semibold text-text-primary">{form.title.trim()}</p>
          <label className="flex items-start gap-2 pt-3 text-caption text-text-primary">
            <input
              type="checkbox"
              className="mt-0.5"
              checked={confirmed}
              onChange={(event) => {
                setConfirmed(event.target.checked);
              }}
            />
            {t("broadcastOps.confirmCheck")}
          </label>
          <div className="flex justify-end gap-2 pt-4">
            <Button
              size="sm"
              variant="tertiary"
              disabled={broadcast.isPending}
              onClick={() => {
                setConfirmOpen(false);
              }}
            >
              {t("actions.cancel")}
            </Button>
            <Button
              size="sm"
              disabled={!confirmed || broadcast.isPending}
              loading={broadcast.isPending}
              onClick={runSend}
            >
              {t("broadcastOps.confirmSend")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </DensePanel>
  );
}
