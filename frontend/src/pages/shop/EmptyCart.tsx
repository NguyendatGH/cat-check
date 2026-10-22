import { useRef } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";

gsap.registerPlugin(useGSAP);

function CartIllustration() {
  return (
    <svg viewBox="0 0 220 160" className="h-auto w-[220px] max-w-full overflow-visible" aria-hidden="true">
      <ellipse className="ec-shadow fill-chip-bg" cx="116" cy="146" rx="68" ry="7" />
      <g className="stroke-chip-bg" strokeWidth="5" strokeLinecap="round">
        <line className="ec-speed" x1="14" y1="70" x2="34" y2="70" />
        <line className="ec-speed" x1="6" y1="86" x2="34" y2="86" />
        <line className="ec-speed" x1="18" y1="102" x2="38" y2="102" />
      </g>
      <g className="ec-cart">
        <g className="ec-cat fill-secondary">
          <path d="M97 48 L95 26 L112 40 Z" />
          <path d="M139 48 L141 26 L124 40 Z" />
          <ellipse cx="118" cy="60" rx="26" ry="21" />
          <ellipse className="ec-eye fill-primary-darker" cx="109" cy="54" rx="2.6" ry="3.4" />
          <ellipse className="ec-eye fill-primary-darker" cx="127" cy="54" rx="2.6" ry="3.4" />
        </g>
        <path
          className="fill-surface stroke-primary"
          d="M62 60 H174 L162 102 H73 Z"
          strokeWidth="5"
          strokeLinejoin="round"
        />
        <g className="stroke-chip-bg" strokeWidth="3" strokeLinecap="round">
          <line x1="96" y1="66" x2="99" y2="96" />
          <line x1="118" y1="66" x2="118" y2="96" />
          <line x1="140" y1="66" x2="137" y2="96" />
          <line x1="70" y1="81" x2="166" y2="81" />
        </g>
        <rect className="ec-paw fill-secondary" x="100" y="55" width="11" height="8" rx="4" />
        <rect className="ec-paw fill-secondary" x="125" y="55" width="11" height="8" rx="4" />
        <path
          className="fill-none stroke-primary"
          d="M40 44 H56 L77 116 H160"
          strokeWidth="5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <g className="ec-wheel">
          <circle className="fill-surface stroke-primary" cx="88" cy="128" r="9" strokeWidth="5" />
          <line className="stroke-primary" x1="88" y1="124" x2="88" y2="132" strokeWidth="2.5" strokeLinecap="round" />
        </g>
        <g className="ec-wheel">
          <circle className="fill-surface stroke-primary" cx="150" cy="128" r="9" strokeWidth="5" />
          <line
            className="stroke-primary"
            x1="150"
            y1="124"
            x2="150"
            y2="132"
            strokeWidth="2.5"
            strokeLinecap="round"
          />
        </g>
      </g>
      <g className="fill-secondary">
        <path className="ec-spark" d="M186 30 l3 7 l7 3 l-7 3 l-3 7 l-3 -7 l-7 -3 l7 -3 Z" />
        <path className="ec-spark" d="M200 66 l2 4.5 l4.5 2 l-4.5 2 l-2 4.5 l-2 -4.5 l-4.5 -2 l4.5 -2 Z" />
      </g>
      <path className="ec-spark fill-primary/40" d="M58 18 l2 4.5 l4.5 2 l-4.5 2 l-2 4.5 l-2 -4.5 l-4.5 -2 l4.5 -2 Z" />
    </svg>
  );
}

export function EmptyCart() {
  const { t } = useTranslation("shop");
  const scope = useRef<HTMLDivElement>(null);

  useGSAP(
    () => {
      if (typeof window.matchMedia !== "function") return;
      gsap.matchMedia().add("(prefers-reduced-motion: no-preference)", () => {
        const intro = gsap.timeline({ defaults: { ease: "power2.out" } });
        intro
          .from(".ec-cart", { x: -80, duration: 0.9, ease: "back.out(1.3)" })
          .from(".ec-wheel", { rotation: -300, transformOrigin: "50% 50%", duration: 0.9 }, 0)
          .from(".ec-speed", { autoAlpha: 0, x: 12, stagger: 0.06, duration: 0.3 }, 0.1)
          .to(".ec-speed", { autoAlpha: 0, x: -16, duration: 0.4 }, 0.75)
          .from(".ec-cat", { y: 26, duration: 0.55, ease: "back.out(2)" }, 0.8)
          .from(".ec-paw", { y: 8, autoAlpha: 0, stagger: 0.08, duration: 0.3 }, 1)
          .from(".ec-text > *", { autoAlpha: 0, y: 10, stagger: 0.08, duration: 0.4 }, 0.35);

        // Nhịp nghỉ nhẹ sau phần mở đầu: xe nhún, bóng co giãn theo, mèo chớp mắt, sao lấp lánh.
        intro.add(() => {
          gsap.to(".ec-cart", { y: -4, duration: 1.6, ease: "sine.inOut", yoyo: true, repeat: -1 });
          gsap.to(".ec-shadow", {
            scaleX: 0.9,
            transformOrigin: "50% 50%",
            duration: 1.6,
            ease: "sine.inOut",
            yoyo: true,
            repeat: -1,
          });
          gsap
            .timeline({ repeat: -1, repeatDelay: 2.6 })
            .to(".ec-eye", { scaleY: 0.1, transformOrigin: "50% 50%", duration: 0.08, yoyo: true, repeat: 1 });
          gsap.fromTo(
            ".ec-spark",
            { scale: 0.5, autoAlpha: 0.2, transformOrigin: "50% 50%" },
            {
              scale: 1,
              autoAlpha: 1,
              duration: 0.9,
              ease: "sine.inOut",
              stagger: { each: 0.45, repeat: -1, yoyo: true },
            },
          );
        });
      });
    },
    { scope },
  );

  return (
    <div
      ref={scope}
      className="flex flex-col items-center gap-5 rounded-2xl bg-surface px-6 pb-10 pt-8 text-center shadow-brand-md"
    >
      <CartIllustration />
      <div className="ec-text flex flex-col items-center gap-2">
        <p className="text-h3 font-bold text-text-primary">{t("cartPage.empty")}</p>
        <p className="max-w-sm text-caption text-pretty text-text-secondary">{t("cartPage.emptyBody")}</p>
        <Link
          to="/shop"
          className="mt-3 inline-flex min-h-11 items-center rounded-xl bg-primary-dark px-5 text-[13px] font-bold text-white transition-[background-color,transform] duration-200 hover:bg-primary active:scale-[0.98] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          {t("cartPage.emptyCta")}
        </Link>
      </div>
    </div>
  );
}
