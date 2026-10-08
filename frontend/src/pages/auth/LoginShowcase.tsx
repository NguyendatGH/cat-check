import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Camera, ChartLine, ShieldCheck } from "lucide-react";
import { phTokenStyle, usePhBands, type PhBand } from "@/entities/ph-bands";
import { cn } from "@/shared/lib/cn";
import { formatNumber } from "@/shared/lib/format/formatNumber";
import brandPaw from "@/shared/assets/icons/web-auth/brand-paw.svg";
import phFlask from "@/shared/assets/icons/web-auth/ph-flask.svg";

function formatPhBound(value: number): string {
  return formatNumber(value, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

/**
 * Khoảng pH của MỘT dải, dựng từ `phMin`/`phMax` của `GET /reference/ph-bands`. Dải mở hai
 * đầu (server bỏ hẳn key) hiện dạng `< x` / `> x`; dải không có biên nào (INCONCLUSIVE) là
 * một trạng thái chứ không phải khoảng nên trả `null`.
 */
function formatBandRange(band: PhBand): string | null {
  const min = typeof band.phMin === "number" ? band.phMin : null;
  const max = typeof band.phMax === "number" ? band.phMax : null;
  if (min !== null && max !== null) return `${formatPhBound(min)}–${formatPhBound(max)}`;
  if (max !== null) return `${band.maxInclusive ? "≤" : "<"} ${formatPhBound(max)}`;
  if (min !== null) return `${band.minInclusive ? "≥" : ">"} ${formatPhBound(min)}`;
  return null;
}

/** Dải có ít nhất một biên — loại INCONCLUSIVE (không phải khoảng pH) khỏi thanh màu. */
function boundedBands(bands: PhBand[]): PhBand[] {
  return bands
    .filter((b) => typeof b.phMin === "number" || typeof b.phMax === "number")
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder);
}

/**
 * Thẻ "Dải pH tham chiếu" của panel giới thiệu — thay thẻ mèo mẫu + thang pH cứng của Figma
 * (16:7573). Toàn bộ ngưỡng, nhãn và màu đến từ `GET /reference/ph-bands` (công khai), nên
 * trang trước đăng nhập không còn khoảng "6.5–7.5 (Lý tưởng)" tự đặt.
 */
function PhReferenceCard() {
  const { t } = useTranslation("auth");
  const { data, isPending, isError } = usePhBands();
  const bands = boundedBands(data ?? []);
  const normal = bands.find((b) => b.severity === "NORMAL");
  const first = bands.at(0);
  const last = bands.at(-1);
  const legend = [first, normal, last].filter((b): b is PhBand => Boolean(b));

  return (
    <div className="col-span-7 flex flex-col gap-4 rounded-2xl bg-[rgba(255,255,255,0.15)] p-4 shadow-[0px_10px_15px_-3px_rgba(0,0,0,0.1),0px_4px_6px_-4px_rgba(0,0,0,0.1)] backdrop-blur-[12px]">
      <div className="flex items-center justify-between gap-2">
        <span className="flex min-w-0 items-center gap-1.5">
          <img src={phFlask} alt="" className="h-[13.333px] w-[10.667px] shrink-0" />
          <span className="truncate text-[12px] font-bold tracking-[0.3px] text-background">
            {t("web.signin.phLabel")}
          </span>
        </span>
        {normal ? (
          <span className="shrink-0 rounded-full bg-verified-bright px-2 py-0.5 text-[12px] font-bold tracking-[0.3px] text-verified-deep">
            {formatBandRange(normal)}
          </span>
        ) : null}
      </div>

      {isPending ? (
        <div className="flex flex-col gap-2" aria-hidden="true">
          <div className="h-3 animate-pulse rounded-full bg-[rgba(255,255,255,0.25)]" />
          <div className="h-8 animate-pulse rounded-lg bg-[rgba(255,255,255,0.15)]" />
        </div>
      ) : isError || bands.length === 0 ? (
        <p className="text-[12px] text-on-primary-subtle">{t("web.signin.phError")}</p>
      ) : (
        <div className="flex flex-col gap-2.5 rounded-xl bg-[rgba(255,255,255,0.1)] p-3">
          <div className="flex h-3 gap-0.5 overflow-hidden rounded-full" aria-hidden="true">
            {bands.map((band) => (
              <span key={band.code} className={cn("h-full flex-1", phTokenStyle(band.colorToken).solid)} />
            ))}
          </div>
          <ul className="grid grid-cols-3 gap-2 text-[11px] font-semibold leading-4 tracking-[0.2px]">
            {legend.map((band, index) => (
              <li
                key={band.code}
                className={cn(
                  "flex flex-col gap-0.5",
                  index === 1 && "items-center text-center",
                  index === 2 && "items-end text-right",
                )}
              >
                <span className={band === normal ? "text-background" : "text-[rgba(222,225,249,0.9)]"}>
                  {formatBandRange(band)}
                </span>
                <span className={band === normal ? "text-background" : "text-[rgba(222,225,249,0.9)]"}>
                  {band.label}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <p className="text-[11px] font-semibold tracking-[0.3px] text-on-primary-subtle">{t("web.signin.phNote")}</p>
    </div>
  );
}

function FeatureTile({ icon, title, body }: { icon: ReactNode; title: string; body: string }) {
  return (
    <div className="flex flex-1 items-start gap-3 rounded-2xl bg-[rgba(255,255,255,0.15)] p-4 shadow-[0px_10px_15px_-3px_rgba(0,0,0,0.1)] backdrop-blur-[12px]">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-secondary-light text-secondary-text-on">
        {icon}
      </span>
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="text-[13px] font-bold leading-5 tracking-[0.2px] text-background">{title}</span>
        <span className="text-[12px] leading-[18px] text-on-primary-subtle">{body}</span>
      </span>
    </div>
  );
}

/**
 * Panel thương hiệu cột trái bản web của `/auth/login` (Figma 16:7573) — giữ bố cục, nền
 * gradient, phân cấp chữ và vị trí các thẻ của thiết kế, nhưng BỎ mọi nội dung tự đặt của
 * mockup (chứng nhận ISFM/WVA, "15.000+ Sen", "4.9/5 từ 85+ phòng khám", lời chứng thực về
 * sỏi bàng quang, thẻ mèo mẫu, thang pH cứng). Thay bằng: dải pH tham chiếu từ API, hai thẻ
 * mô tả đúng chức năng sản phẩm, và câu miễn trừ y tế của `legal.json`.
 */
export function LoginShowcase() {
  const { t } = useTranslation(["auth", "legal"]);

  return (
    <aside
      className="relative hidden overflow-hidden lg:col-span-7 lg:flex lg:flex-col lg:justify-between lg:gap-8 lg:p-14"
      style={{
        backgroundImage:
          "linear-gradient(134.43deg, var(--color-primary-dark) 0%, var(--color-primary) 50%, rgb(26,62,161) 100%)",
      }}
    >
      <div
        className="pointer-events-none absolute -right-20 -top-20 size-96 rounded-full bg-[rgba(255,223,147,0.1)] blur-[32px]"
        aria-hidden="true"
      />
      <div
        className="pointer-events-none absolute bottom-0 left-1/4 right-[29.29%] h-80 rounded-full bg-[rgba(220,225,255,0.15)] blur-[20px]"
        aria-hidden="true"
      />

      <div className="relative flex flex-col gap-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className="flex items-center gap-2 rounded-full bg-[rgba(255,255,255,0.15)] px-3.5 py-1.5 backdrop-blur-[6px]">
            <span className="size-2 rounded-full bg-secondary" aria-hidden="true" />
            <span className="text-[12px] font-bold tracking-[0.3px] text-background">
              {t("web.signin.innovationBadge")}
            </span>
          </span>
          <span className="flex items-center gap-1.5 rounded-full bg-[rgba(255,255,255,0.1)] px-3 py-1">
            <ShieldCheck size={14} aria-hidden="true" className="text-secondary-light" />
            <span className="text-[11px] font-semibold tracking-[0.4px] text-white">
              {t("web.signin.disclaimerBadge")}
            </span>
          </span>
        </div>

        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-2">
            <span className="flex size-12 items-center justify-center rounded-2xl bg-surface shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.1),0px_2px_4px_-2px_rgba(0,0,0,0.1)]">
              <img src={brandPaw} alt="" className="h-[23.75px] w-[25px]" />
            </span>
            <span className="flex flex-col">
              <span className="text-[32px] font-extrabold leading-10 tracking-[-0.8px] text-background">CATCHECK</span>
              <span className="text-[11px] font-semibold uppercase tracking-[0.55px] text-on-primary-subtle">
                {t("web.signin.brandTagline")}
              </span>
            </span>
          </div>
          <h2 className="max-w-[560px] pt-2 text-[24px] font-extrabold leading-[33px] text-background">
            {t("web.signin.headline")}
          </h2>
          <p className="max-w-[576px] text-[16px] leading-6 text-on-primary-subtle opacity-90">
            {t("web.signin.subheadline")}
          </p>
        </div>
      </div>

      <div className="relative grid grid-cols-12 gap-4">
        <PhReferenceCard />
        <div className="col-span-5 flex flex-col gap-4">
          <FeatureTile
            icon={<Camera size={18} aria-hidden="true" />}
            title={t("web.signin.feature1Title")}
            body={t("web.signin.feature1Body")}
          />
          <FeatureTile
            icon={<ChartLine size={18} aria-hidden="true" />}
            title={t("web.signin.feature2Title")}
            body={t("web.signin.feature2Body")}
          />
        </div>
      </div>

      <div className="relative flex items-center gap-3 rounded-2xl bg-[rgba(255,255,255,0.08)] p-4">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-[rgba(255,255,255,0.15)] text-secondary-light">
          <ShieldCheck size={20} aria-hidden="true" />
        </span>
        <div className="flex min-w-0 flex-col gap-0.5">
          <p className="text-[14px] font-semibold leading-5 text-background">
            {t("disclaimer.footerLine.text", { ns: "legal" })}
          </p>
          <Link
            to="/legal/medical-disclaimer"
            className="w-fit text-[12px] font-semibold tracking-[0.3px] text-secondary-light hover:underline"
          >
            {t("web.signin.disclaimerLink")}
          </Link>
        </div>
      </div>
    </aside>
  );
}
