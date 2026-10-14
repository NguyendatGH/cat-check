import { useState } from "react";
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { Badge, Button, Input } from "@/shared/ui";
import { AdminQueryError, useAdminUsers } from "@/features/admin";
import type { AdminDsarRequest, AdminUserSummary } from "@/features/admin";
import {
  FULFIL_DAYS,
  REQUEST_TYPES,
  STATUSES,
  countdownOf,
  statusTone,
  typeTone,
} from "./privacyRequestsModel";

const P = "pages.privacyRequests";

export function TypeTag({ value }: { value: string }) {
  const { t } = useTranslation("admin");
  return <Badge tone={typeTone(value)}>{t(`${P}.types.${value}`, { defaultValue: value })}</Badge>;
}

export function StatusTag({ value }: { value: string }) {
  const { t } = useTranslation("admin");
  return (
    <Badge tone={statusTone(value)} className="whitespace-nowrap">
      {t(`${P}.statuses.${value}`, { defaultValue: value })}
    </Badge>
  );
}

export function CountdownTag({ item, now }: { item: AdminDsarRequest; now: number }) {
  const { t } = useTranslation("admin");
  const countdown = countdownOf(item, now);
  if (countdown.kind === "none") return null;
  const overdue = countdown.kind === "daysOverdue" || countdown.kind === "hoursOverdue";
  const soon = countdown.kind === "hoursLeft" || (countdown.kind === "daysLeft" && countdown.count < 3);
  return (
    <Badge tone={overdue ? "danger" : soon ? "warning" : "neutral"} className="whitespace-nowrap">
      {t(`${P}.countdown.${countdown.kind}`, { n: countdown.count })}
    </Badge>
  );
}

/** Giải thích ngắn, thu gọn mặc định: yêu cầu là gì, thời hạn, trạng thái, vai trò. */
export function PrivacyRequestsExplainer() {
  const { t } = useTranslation("admin");
  return (
    <details className="group rounded-xl border border-border bg-surface px-4 py-3 shadow-sm">
      <summary className="cursor-pointer text-small font-semibold text-primary-dark marker:text-text-tertiary">
        {t(`${P}.explainer.summary`)}
      </summary>
      <div className="mt-3 flex flex-col gap-4 text-small text-text-secondary">
        <p>
          {t(`${P}.explainer.intro`)}{" "}
          <span className="text-text-tertiary">{t(`${P}.explainer.acronym`)}</span>
        </p>

        <section className="flex flex-col gap-2">
          <h3 className="text-small font-bold text-text-primary">{t(`${P}.explainer.typesTitle`)}</h3>
          <ul className="flex flex-col gap-2">
            {REQUEST_TYPES.map((type) => (
              <li key={type} className="flex flex-col gap-1 sm:flex-row sm:items-start sm:gap-3">
                <span className="flex shrink-0 items-center gap-2 sm:w-64">
                  <TypeTag value={type} />
                  <span className="whitespace-nowrap text-text-tertiary">
                    {t(`${P}.explainer.typeDays`, { n: FULFIL_DAYS[type] })}
                  </span>
                </span>
                <span>{t(`${P}.typeHints.${type}`)}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="flex flex-col gap-2">
          <h3 className="text-small font-bold text-text-primary">{t(`${P}.explainer.deadlinesTitle`)}</h3>
          <ul className="list-disc space-y-1 pl-5">
            <li>{t(`${P}.explainer.deadlineAck`)}</li>
            <li>{t(`${P}.explainer.deadlineFulfil`)}</li>
            <li>{t(`${P}.explainer.deadlineExtend`)}</li>
            <li>{t(`${P}.explainer.deadlineOverdue`)}</li>
          </ul>
        </section>

        <section className="flex flex-col gap-2">
          <h3 className="text-small font-bold text-text-primary">{t(`${P}.explainer.statusesTitle`)}</h3>
          <ul className="flex flex-col gap-2">
            {STATUSES.map((status) => (
              <li key={status} className="flex flex-col gap-1 sm:flex-row sm:items-start sm:gap-3">
                <span className="shrink-0 sm:w-64">
                  <StatusTag value={status} />
                </span>
                <span>{t(`${P}.statusHints.${status}`)}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="flex flex-col gap-2">
          <h3 className="text-small font-bold text-text-primary">{t(`${P}.explainer.rolesTitle`)}</h3>
          <ul className="list-disc space-y-1 pl-5">
            <li>{t(`${P}.explainer.roleSupport`)}</li>
            <li>{t(`${P}.explainer.roleSuper`)}</li>
            <li>{t(`${P}.explainer.roleDpo`)}</li>
          </ul>
        </section>
      </div>
    </details>
  );
}

function LookupResults({ email, onSelect }: { email: string; onSelect: (user: AdminUserSummary) => void }) {
  const { t } = useTranslation("admin");
  const users = useAdminUsers({ status: "", email, packageCode: "", page: 0, size: 5 });
  if (users.isPending) return <p className="text-caption text-text-secondary">{t("feedback.loading")}</p>;
  if (users.isError) {
    return (
      <AdminQueryError
        error={users.error}
        onRetry={() => {
          void users.refetch();
        }}
      />
    );
  }
  if (users.data.items.length === 0) {
    return <p className="text-caption text-text-secondary">{t(`${P}.userLookup.noMatch`)}</p>;
  }
  return (
    <ul className="flex flex-col gap-2">
      {users.data.items.map((user) => (
        <li
          key={user.userId}
          className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2"
        >
          <span className="min-w-0">
            <span className="block truncate font-semibold text-text-primary">{user.fullName}</span>
            <span className="block truncate text-small text-text-secondary">{user.emailMasked}</span>
          </span>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              onSelect(user);
            }}
          >
            {t(`${P}.userLookup.select`)}
          </Button>
        </li>
      ))}
    </ul>
  );
}

export interface SelectedUser {
  userId: string;
  label?: string;
  detail?: string;
}

/** Chọn người dùng theo email (khớp chính xác ở /admin/users); vẫn cho nhập mã người dùng trực tiếp. */
export function UserPicker({
  value,
  onChange,
}: {
  value: SelectedUser | undefined;
  onChange: (value: SelectedUser | undefined) => void;
}) {
  const { t } = useTranslation("admin");
  const [emailInput, setEmailInput] = useState("");
  const [submittedEmail, setSubmittedEmail] = useState("");
  const [manualId, setManualId] = useState("");

  if (value) {
    return (
      <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-chip-bg/40 px-3 py-2">
        <span className="min-w-0">
          <span className="block text-caption text-text-tertiary">{t(`${P}.userLookup.selected`)}</span>
          <span className="block truncate font-semibold text-text-primary">{value.label ?? value.userId}</span>
          {value.detail ? <span className="block truncate text-small text-text-secondary">{value.detail}</span> : null}
        </span>
        <span className="flex flex-wrap gap-2">
          <Link
            to={`/admin/users/${encodeURIComponent(value.userId)}`}
            className="rounded-lg px-2 py-1.5 text-caption font-semibold text-primary-dark hover:underline"
          >
            {t(`${P}.viewUser`)}
          </Link>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              onChange(undefined);
            }}
          >
            {t(`${P}.userLookup.change`)}
          </Button>
        </span>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <form
        className="flex flex-col gap-2 sm:flex-row sm:items-end"
        onSubmit={(event) => {
          event.preventDefault();
          setSubmittedEmail(emailInput.trim().toLowerCase());
        }}
      >
        <div className="flex-1">
          <Input
            label={t(`${P}.userLookup.emailLabel`)}
            helperText={t(`${P}.userLookup.emailHelp`)}
            type="email"
            value={emailInput}
            autoComplete="off"
            className="h-10"
            onChange={(event) => {
              setEmailInput(event.target.value);
            }}
          />
        </div>
        <Button type="submit" variant="secondary" size="sm" className="h-10 sm:mb-6" disabled={!emailInput.trim()}>
          {t(`${P}.userLookup.search`)}
        </Button>
      </form>
      {submittedEmail ? (
        <LookupResults
          email={submittedEmail}
          onSelect={(user) => {
            onChange({ userId: user.userId, label: user.fullName, detail: user.emailMasked });
          }}
        />
      ) : null}
      <details className="text-small">
        <summary className="cursor-pointer text-text-secondary">{t(`${P}.userLookup.manualToggle`)}</summary>
        <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-end">
          <div className="flex-1">
            <Input
              label={t(`${P}.userId`)}
              helperText={t(`${P}.userLookup.manualHelp`)}
              value={manualId}
              autoComplete="off"
              spellCheck={false}
              className="h-10 font-mono"
              onChange={(event) => {
                setManualId(event.target.value);
              }}
            />
          </div>
          <Button
            variant="secondary"
            size="sm"
            className="h-10 sm:mb-6"
            disabled={!manualId.trim()}
            onClick={() => {
              onChange({ userId: manualId.trim() });
            }}
          >
            {t(`${P}.userLookup.select`)}
          </Button>
        </div>
        <Link to="/admin/users" className="text-caption font-semibold text-primary-dark hover:underline">
          {t(`${P}.userLookup.openUsers`)}
        </Link>
      </details>
    </div>
  );
}
