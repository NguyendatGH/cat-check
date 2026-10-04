import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { useSessionStore } from "@/entities/user";
import { Button } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { cn } from "@/shared/lib/cn";
import { useCreateDsarRequest } from "./hooks";
import { LEGAL_ASIDE, LEGAL_ASIDE_CARD, LEGAL_COLUMN, LEGAL_SHELL, LEGAL_SPLIT } from "./shell";

/**
 * `/legal/complaints` (p9 §9.4.3 #14). p15 §15.4.1 (H11.5) xếp quy trình khiếu nại
 * THƯƠNG MẠI (Điều 11.1.d Luật TMĐT) vào Phase 3, nhưng route này đã được đăng ký và
 * đang HOẠT ĐỘNG THẬT trong `router.tsx` (không phải `ComingSoonPage`) — theo brief A2-FE
 * "tự kiểm route thực tế, đừng đoán", trang được dựng đầy đủ chức năng thay vì để trống.
 *
 * `POST /privacy/requests` (C13, `CreateDsarRequest`) chỉ nhận `{requestType, channel}` —
 * KHÔNG có trường nội dung khiếu nại. Vì vậy trang chỉ mở được một hồ sơ khiếu nại có mã
 * tra cứu (để bắt đầu đồng hồ SLA theo p15 §15.4.1), còn NỘI DUNG chi tiết phải gửi qua
 * kênh email DPO ở `/legal/contact` — không bịa thêm field mà backend chưa hỗ trợ.
 *
 * DESKTOP (>= lg): quy trình + nút mở hồ sơ ở cột chính 560px (trang ngắn, không cần mục
 * lục), kênh liên hệ thay thế tách sang thẻ ở cột phụ 360px.
 */
export function ComplaintsPage() {
  const { t } = useTranslation(["legal", "common"]);
  const navigate = useNavigate();
  const status = useSessionStore((s) => s.status);
  const createRequest = useCreateDsarRequest();
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    setError(null);
    try {
      await createRequest.mutateAsync({ requestType: "COMPLAINT", channel: "WEB_FORM" });
    } catch (err) {
      if (isApiError(err) && err.code === "RATE_LIMITED") {
        setError(t("complaints.errors.rateLimited"));
      } else {
        setError(t("complaints.errors.generic"));
      }
    }
  };

  const alternativeChannel = (
    <>
      {t("complaints.alternative")}{" "}
      <Link to="/legal/contact" className="font-semibold text-primary hover:underline">
        {t("complaints.contactLink")}
      </Link>
    </>
  );

  return (
    <div className={LEGAL_SHELL}>
      <div className={LEGAL_SPLIT}>
        <div className={cn(LEGAL_COLUMN, "gap-6")}>
          <header className="flex flex-col gap-2">
            <h1 className="text-h1 font-bold text-text-primary">{t("pages.complaints.title")}</h1>
            <p className="text-body text-text-secondary">{t("complaints.intro")}</p>
          </header>

          <ol className="list-decimal space-y-2 pl-5 text-body text-text-secondary">
            <li>{t("complaints.steps.openTicket")}</li>
            <li>{t("complaints.steps.emailDetails")}</li>
            <li>{t("complaints.steps.wait")}</li>
          </ol>

          {status === "authenticated" ? (
            createRequest.isSuccess ? (
              <div className="rounded-lg border border-border bg-chip-bg p-4">
                <p className="text-body font-semibold text-text-primary">{t("complaints.successTitle")}</p>
                <p className="text-caption text-text-secondary">
                  {t("complaints.successDescription", { publicRef: createRequest.data.publicRef })}
                </p>
                <Link
                  to="/legal/contact"
                  className="mt-2 inline-block text-caption font-semibold text-primary hover:underline"
                >
                  {t("complaints.emailDpoLink")}
                </Link>
              </div>
            ) : (
              <div className="flex flex-col gap-2">
                {error ? (
                  <p role="alert" className="text-small font-medium text-danger-text">
                    {error}
                  </p>
                ) : null}
                <Button
                  type="button"
                  className="lg:self-start"
                  loading={createRequest.isPending}
                  onClick={() => {
                    void handleSubmit();
                  }}
                >
                  {t("complaints.openTicketCta")}
                </Button>
              </div>
            )
          ) : (
            <Button
              type="button"
              className="lg:self-start"
              onClick={() => {
                void navigate("/auth/login?next=%2Flegal%2Fcomplaints");
              }}
            >
              {t("complaints.loginCta")}
            </Button>
          )}

          <p className="text-caption text-text-tertiary lg:hidden">{alternativeChannel}</p>
        </div>

        <aside className={LEGAL_ASIDE}>
          <div className={LEGAL_ASIDE_CARD}>
            <h2 className="text-h3 font-bold text-text-primary">{t("complaints.sideHelpTitle")}</h2>
            <p className="text-caption text-text-secondary">{alternativeChannel}</p>
          </div>
        </aside>
      </div>
    </div>
  );
}
