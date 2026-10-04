import { useState } from "react";
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { Button, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminSection,
  AdminSelect,
  AdminTableScroll,
  ApiErrorNote,
  Pager,
  ReasonField,
  adminTableClass,
  adminTdClass,
  adminThClass,
  isReasonValid,
  useAdminUsers,
  useLockAdminUser,
  useUnlockAdminUser,
} from "@/features/admin";
import type { AdminUserStatus } from "@/features/admin";

const STATUSES: AdminUserStatus[] = [
  "PENDING_VERIFICATION",
  "ACTIVE",
  "LOCKED",
  "RESTRICTED",
  "DELETION_REQUESTED",
  "ANONYMIZED",
];

function formatDate(value: string | null): string {
  return value === null
    ? "—"
    : new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AdminUsersPage() {
  const { t } = useTranslation("admin");
  const [status, setStatus] = useState<AdminUserStatus | "">("");
  const [email, setEmail] = useState("");
  const [packageCode, setPackageCode] = useState("");
  const [page, setPage] = useState(0);
  const [reason, setReason] = useState("");
  const users = useAdminUsers({ status, email, packageCode, page, size: 20 });
  const lock = useLockAdminUser();
  const unlock = useUnlockAdminUser();
  const canWrite = isReasonValid(reason);

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.users.title")}
        description={t("pages.users.description")}
        specRef="L1 · GET /api/v1/admin/users"
      />
      <AdminSection title={t("users.filtersTitle")}>
        <div className="grid gap-4 md:grid-cols-3">
          <Input
            label={t("users.email")}
            value={email}
            onChange={(event) => {
              setEmail(event.target.value);
              setPage(0);
            }}
          />
          <Input
            label={t("users.packageCode")}
            value={packageCode}
            onChange={(event) => {
              setPackageCode(event.target.value);
              setPage(0);
            }}
          />
          <AdminSelect
            label={t("users.status")}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as AdminUserStatus | "");
              setPage(0);
            }}
          >
            <option value="">{t("users.allStatuses")}</option>
            {STATUSES.map((value) => (
              <option key={value} value={value}>
                {t(`users.statuses.${value}`)}
              </option>
            ))}
          </AdminSelect>
        </div>
        <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
      </AdminSection>
      <AdminSection title={t("users.listTitle")}>
        {users.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {users.isError ? <ApiErrorNote error={users.error} /> : null}
        {users.data && users.data.items.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {users.data && users.data.items.length > 0 ? (
          <>
            <AdminTableScroll>
              <table className={adminTableClass}>
                <thead>
                  <tr>
                    <th className={adminThClass}>{t("users.colUser")}</th>
                    <th className={adminThClass}>{t("users.colStatus")}</th>
                    <th className={adminThClass}>{t("users.colOnboarding")}</th>
                    <th className={adminThClass}>{t("users.colCats")}</th>
                    <th className={adminThClass}>{t("users.colScans")}</th>
                    <th className={adminThClass}>{t("users.colLastLogin")}</th>
                    <th className={adminThClass}>{t("users.colAction")}</th>
                  </tr>
                </thead>
                <tbody>
                  {users.data.items.map((user) => (
                    <tr key={user.userId}>
                      <td className={adminTdClass}>
                        <Link
                          className="font-semibold text-primary-dark hover:underline"
                          to={`/admin/users/${user.userId}`}
                        >
                          {user.fullName}
                        </Link>
                        <span className="block text-small text-text-secondary">{user.emailMasked}</span>
                      </td>
                      <td className={adminTdClass}>{t(`users.statuses.${user.status}`)}</td>
                      <td className={adminTdClass}>{user.onboardingStatus}</td>
                      <td className={adminTdClass}>{user.catCount}</td>
                      <td className={adminTdClass}>{user.scanCount}</td>
                      <td className={adminTdClass}>{formatDate(user.lastLoginAt)}</td>
                      <td className={adminTdClass}>
                        {user.status === "LOCKED" ? (
                          <Button
                            variant="tertiary"
                            size="sm"
                            disabled={!canWrite || unlock.isPending}
                            onClick={() => {
                              unlock.mutate({ userId: user.userId, reason: reason.trim() });
                            }}
                          >
                            {t("users.unlock")}
                          </Button>
                        ) : (
                          <Button
                            variant="tertiary"
                            size="sm"
                            disabled={!canWrite || lock.isPending || user.status === "ANONYMIZED"}
                            onClick={() => {
                              lock.mutate({ userId: user.userId, reason: reason.trim() });
                            }}
                          >
                            {t("users.lock")}
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableScroll>
            <Pager
              page={users.data.number}
              totalPages={users.data.totalPages}
              totalElements={users.data.totalElements}
              onPageChange={setPage}
            />
          </>
        ) : null}
        <ApiErrorNote error={lock.error ?? unlock.error} />
      </AdminSection>
    </div>
  );
}
