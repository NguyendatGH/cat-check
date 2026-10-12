import { useState } from "react";
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { Lock, LockOpen } from "lucide-react";
import { Button, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminQueryError,
  AdminSelect,
  DensePanel,
  DenseTableFrame,
  KpiGrid,
  KpiTile,
  MutationErrorNote,
  Pager,
  ReasonField,
  TableSkeleton,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  formatAdminNumber,
  isReasonValid,
  useAdminUsers,
  useLockAdminUser,
  useUnlockAdminUser,
} from "@/features/admin";
import type { AdminUserStatus } from "@/features/admin";
import { useGroup1Can } from "./group1Access";

const STATUSES: AdminUserStatus[] = [
  "PENDING_VERIFICATION",
  "ACTIVE",
  "LOCKED",
  "RESTRICTED",
  "DELETION_REQUESTED",
  "ANONYMIZED",
];

export function AdminUsersPage() {
  const { t } = useTranslation("admin");
  const can = useGroup1Can();
  const [status, setStatus] = useState<AdminUserStatus | "">("");
  const [email, setEmail] = useState("");
  const [packageCode, setPackageCode] = useState("");
  const [page, setPage] = useState(0);
  const [reason, setReason] = useState("");
  const users = useAdminUsers({ status, email, packageCode, page, size: 20 });
  const lock = useLockAdminUser();
  const unlock = useUnlockAdminUser();
  const canLock = can("userLock");
  const canWrite = canLock && isReasonValid(reason);
  const items = users.data?.items ?? [];
  const countOf = (value: AdminUserStatus) => items.filter((user) => user.status === value).length;

  return (
    <div className="flex flex-col gap-4">
      <AdminPageHeader
        title={t("pages.users.title")}
        description={t("pages.users.description")}
        specRef="L1 · GET /api/v1/admin/users"
      />
      <DensePanel title={t("users.filtersTitle")}>
        <div className="grid gap-3 md:grid-cols-3">
          <Input
            label={t("users.email")}
            value={email}
            className="h-10"
            onChange={(event) => {
              setEmail(event.target.value);
              setPage(0);
            }}
          />
          <Input
            label={t("users.packageCode")}
            value={packageCode}
            className="h-10"
            onChange={(event) => {
              setPackageCode(event.target.value);
              setPage(0);
            }}
          />
          <AdminSelect
            label={t("users.status")}
            value={status}
            className="h-10"
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
        {canLock ? (
          <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
        ) : (
          <p className="text-caption text-text-secondary">{t("users.readOnlyNote")}</p>
        )}
      </DensePanel>

      {users.data ? (
        <KpiGrid label={t("dense.kpiLabel")}>
          <KpiTile label={t("users.kpiTotal")} value={formatAdminNumber(users.data.totalElements)} tone="info" />
          <KpiTile
            label={t("users.statuses.ACTIVE")}
            value={formatAdminNumber(countOf("ACTIVE"))}
            hint={t("dense.onPageHint")}
            tone="success"
          />
          <KpiTile
            label={t("users.statuses.LOCKED")}
            value={formatAdminNumber(countOf("LOCKED"))}
            hint={t("dense.onPageHint")}
            tone={countOf("LOCKED") > 0 ? "warning" : "neutral"}
          />
          <KpiTile
            label={t("users.statuses.DELETION_REQUESTED")}
            value={formatAdminNumber(countOf("DELETION_REQUESTED"))}
            hint={t("dense.onPageHint")}
            tone={countOf("DELETION_REQUESTED") > 0 ? "danger" : "neutral"}
          />
        </KpiGrid>
      ) : null}

      <DensePanel title={t("users.listTitle")}>
        {users.isPending ? <TableSkeleton /> : null}
        {users.isError ? (
          <AdminQueryError
            error={users.error}
            onRetry={() => {
              void users.refetch();
            }}
          />
        ) : null}
        {users.data && items.length === 0 ? (
          <p className="py-6 text-center text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {users.data && items.length > 0 ? (
          <>
            <DenseTableFrame label={t("users.listTitle")}>
              <table className={denseTableClass}>
                <thead>
                  <tr>
                    <th scope="col" className={denseThClass}>
                      {t("users.colUser")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("users.colStatus")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("users.colOnboarding")}
                    </th>
                    <th scope="col" className={`${denseThClass} text-right`}>
                      {t("users.colCats")}
                    </th>
                    <th scope="col" className={`${denseThClass} text-right`}>
                      {t("users.colScans")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("users.colLastLogin")}
                    </th>
                    {canLock ? (
                      <th scope="col" className={denseThClass}>
                        {t("users.colAction")}
                      </th>
                    ) : null}
                  </tr>
                </thead>
                <tbody>
                  {items.map((user) => (
                    <tr key={user.userId} className={denseRowClass}>
                      <td className={denseTdClass}>
                        <Link
                          className="font-semibold text-primary-dark hover:underline"
                          to={`/admin/users/${user.userId}`}
                        >
                          {user.fullName}
                        </Link>
                        <span className="block text-small text-text-secondary">{user.emailMasked}</span>
                      </td>
                      <td className={`${denseTdClass} whitespace-nowrap`}>{t(`users.statuses.${user.status}`)}</td>
                      <td className={`${denseTdClass} font-mono text-small`}>{user.onboardingStatus}</td>
                      <td className={`${denseTdClass} text-right font-mono tabular-nums`}>{user.catCount}</td>
                      <td className={`${denseTdClass} text-right font-mono tabular-nums`}>{user.scanCount}</td>
                      <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                        {formatAdminDateTime(user.lastLoginAt)}
                      </td>
                      {canLock ? (
                        <td className={denseTdClass}>
                          {user.status === "LOCKED" ? (
                            <Button
                              variant="tertiary"
                              size="sm"
                              leftIcon={<LockOpen size={14} />}
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
                              leftIcon={<Lock size={14} />}
                              disabled={!canWrite || lock.isPending || user.status === "ANONYMIZED"}
                              onClick={() => {
                                lock.mutate({ userId: user.userId, reason: reason.trim() });
                              }}
                            >
                              {t("users.lock")}
                            </Button>
                          )}
                        </td>
                      ) : null}
                    </tr>
                  ))}
                </tbody>
              </table>
            </DenseTableFrame>
            <Pager
              page={users.data.number}
              totalPages={users.data.totalPages}
              totalElements={users.data.totalElements}
              onPageChange={setPage}
            />
          </>
        ) : null}
        <MutationErrorNote error={lock.error ?? unlock.error} />
      </DensePanel>
    </div>
  );
}
