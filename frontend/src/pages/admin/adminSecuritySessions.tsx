import { useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, LogOut, ShieldAlert } from "lucide-react";
import { Button, Dialog, DialogContent, DialogDescription, DialogTitle, Input } from "@/shared/ui";
import {
  AdminSelect,
  DensePanel,
  KpiGrid,
  KpiTile,
  MutationErrorNote,
  ReasonField,
  formatAdminNumber,
  isConfirmPhraseMatch,
  isReasonValid,
  isUuid,
  useAdminSecurityIncidents,
  usePurgeAllSessions,
  type PurgeAllSessionsResult,
} from "@/features/admin";

const MANUAL = "__manual__";

export function SecuritySessionsTab({ canReadIncidents }: { canReadIncidents: boolean }) {
  const { t } = useTranslation("admin");
  const purge = usePurgeAllSessions();
  const openIncidents = useAdminSecurityIncidents(
    { severity: "", unresolved: true, page: 0, size: 100 },
    canReadIncidents,
  );
  const [incidentChoice, setIncidentChoice] = useState(canReadIncidents ? "" : MANUAL);
  const [manualId, setManualId] = useState("");
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [phrase, setPhrase] = useState("");
  const [result, setResult] = useState<PurgeAllSessionsResult | null>(null);

  const confirmPhrase = t("security.purge.confirmPhrase");
  const incidentId = incidentChoice === MANUAL ? manualId.trim() : incidentChoice;
  const incidentValid = isUuid(incidentId);
  const incidentRef = openIncidents.data?.items.find((incident) => incident.id === incidentId)?.publicRef ?? incidentId;

  const consequences = ["users", "admins", "devices", "otp", "dsar"] as const;

  return (
    <div className="flex flex-col gap-4">
      <DensePanel title={t("security.purge.title")} description={t("security.purge.description")}>
        <div className="flex gap-3 rounded-lg border border-danger/40 bg-danger-bg px-3 py-3 text-danger-text">
          <AlertTriangle size={18} className="mt-0.5 shrink-0" aria-hidden="true" />
          <div className="flex flex-col gap-1">
            <p className="text-caption font-bold">{t("security.purge.warningTitle")}</p>
            <ul className="list-disc pl-4 text-caption">
              {consequences.map((key) => (
                <li key={key}>{t(`security.purge.consequences.${key}`)}</li>
              ))}
            </ul>
          </div>
        </div>

        {result !== null ? (
          <div className="flex flex-col gap-3" aria-live="polite">
            <p className="text-caption font-semibold text-success-text">
              {t("security.purge.done", { ref: result.incidentRef ?? incidentRef })}
            </p>
            <KpiGrid label={t("security.purge.resultLabel")}>
              <KpiTile
                label={t("security.purge.revoked")}
                value={formatAdminNumber(result.revokedCount)}
                tone="danger"
              />
              <KpiTile label={t("security.purge.devices")} value={formatAdminNumber(result.deviceSessionsRevoked)} />
              <KpiTile label={t("security.purge.otp")} value={formatAdminNumber(result.otpInvalidated)} />
              <KpiTile
                label={t("security.purge.elapsed")}
                value={t("security.purge.ms", { value: formatAdminNumber(result.elapsedMillis) })}
                hint={t("security.purge.dsarLinks", { count: result.dsarDownloadLinksRevoked ?? 0 })}
              />
            </KpiGrid>
            <p className="text-caption text-text-secondary">{t("security.purge.reloginNote")}</p>
          </div>
        ) : (
          <form
            className="flex flex-col gap-3"
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              setSubmitted(true);
              if (!incidentValid || !isReasonValid(reason)) return;
              setPhrase("");
              purge.reset();
              setConfirming(true);
            }}
          >
            <div className="grid gap-3 lg:grid-cols-2">
              {canReadIncidents ? (
                <AdminSelect
                  label={t("security.purge.incident")}
                  value={incidentChoice}
                  aria-invalid={submitted && !incidentValid ? true : undefined}
                  onChange={(event) => {
                    setIncidentChoice(event.target.value);
                  }}
                >
                  <option value="">
                    {openIncidents.isPending ? t("feedback.loading") : t("security.purge.chooseIncident")}
                  </option>
                  {(openIncidents.data?.items ?? []).map((incident) => (
                    <option key={incident.id} value={incident.id}>
                      {`${incident.publicRef} · ${t(`security.severities.${incident.severity}`)} · ${incident.summary.slice(0, 60)}`}
                    </option>
                  ))}
                  <option value={MANUAL}>{t("security.purge.manualOption")}</option>
                </AdminSelect>
              ) : null}
              {incidentChoice === MANUAL ? (
                <Input
                  label={t("security.purge.incidentId")}
                  value={manualId}
                  autoComplete="off"
                  spellCheck={false}
                  className="h-10 font-mono"
                  error={submitted && !incidentValid ? t("security.purge.incidentInvalid") : undefined}
                  helperText={t("security.purge.incidentHelp")}
                  onChange={(event) => {
                    setManualId(event.target.value);
                  }}
                />
              ) : null}
            </div>
            {submitted && !incidentValid && incidentChoice !== MANUAL ? (
              <p role="alert" className="text-small text-danger-text">
                {t("security.purge.incidentRequired")}
              </p>
            ) : null}
            <ReasonField value={reason} onChange={setReason} showError={submitted} />
            <div className="flex justify-end">
              <Button type="submit" size="sm" className="bg-danger shadow-none hover:bg-danger/90">
                <ShieldAlert size={14} aria-hidden="true" />
                {t("security.purge.continue")}
              </Button>
            </div>
          </form>
        )}
      </DensePanel>

      <Dialog
        open={confirming}
        onOpenChange={(open) => {
          if (!open && !purge.isPending) setConfirming(false);
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogTitle className="flex items-center gap-2 text-danger-text">
            <AlertTriangle size={18} aria-hidden="true" />
            {t("security.purge.confirmTitle")}
          </DialogTitle>
          <DialogDescription>{t("security.purge.confirmBody", { ref: incidentRef })}</DialogDescription>
          <form
            className="flex flex-col gap-3 pt-3"
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              if (!isConfirmPhraseMatch(phrase, confirmPhrase)) return;
              purge.mutate(
                { incidentId, reason: reason.trim() },
                {
                  onSuccess: (data) => {
                    setResult(data);
                    setConfirming(false);
                  },
                },
              );
            }}
          >
            <Input
              label={t("security.purge.typePhrase", { phrase: confirmPhrase })}
              value={phrase}
              autoComplete="off"
              spellCheck={false}
              className="h-10 font-mono"
              onChange={(event) => {
                setPhrase(event.target.value);
              }}
            />
            <MutationErrorNote error={purge.error} />
            <div className="flex justify-end gap-2 border-t border-border pt-3">
              <Button
                type="button"
                variant="tertiary"
                size="sm"
                disabled={purge.isPending}
                onClick={() => {
                  setConfirming(false);
                }}
              >
                {t("actions.cancel")}
              </Button>
              <Button
                type="submit"
                size="sm"
                className="bg-danger shadow-none hover:bg-danger/90"
                disabled={!isConfirmPhraseMatch(phrase, confirmPhrase) || purge.isPending}
                loading={purge.isPending}
              >
                <LogOut size={14} aria-hidden="true" />
                {t("security.purge.submit")}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
