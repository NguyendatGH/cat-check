import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Spin } from "antd";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import { useSplashStore, type SplashPhase } from "@/shared/lib/motion/splash";
import headSrc from "@/shared/assets/images/splash/head.png";
import earsSrc from "@/shared/assets/images/splash/ears.png";
import checkSrc from "@/shared/assets/images/splash/check.png";
import letterC1Src from "@/shared/assets/images/splash/l-c1.png";
import letterASrc from "@/shared/assets/images/splash/l-a.png";
import letterTSrc from "@/shared/assets/images/splash/l-t.png";
import letterC2Src from "@/shared/assets/images/splash/l-c2.png";
import letterHSrc from "@/shared/assets/images/splash/l-h.png";
import letterESrc from "@/shared/assets/images/splash/l-e.png";
import letterC3Src from "@/shared/assets/images/splash/l-c3.png";
import letterKSrc from "@/shared/assets/images/splash/l-k.png";

gsap.registerPlugin(useGSAP);

// Layers cut from the brand logo (web-auth/logo.png); boxes are px inside its 459x300 trimmed frame.
const FRAME = { w: 459, h: 300 };

interface Layer {
  src: string;
  x: number;
  y: number;
  w: number;
  h: number;
  part: string;
}

const MARK_LAYERS: Layer[] = [
  { src: headSrc, x: 133, y: 0, w: 192, h: 182, part: "head" },
  { src: earsSrc, x: 152, y: 22, w: 154, h: 39, part: "ears" },
  { src: checkSrc, x: 173, y: 82, w: 105, h: 83, part: "check" },
];

const LETTER_LAYERS: Layer[] = [
  { src: letterC1Src, x: 1, y: 201, w: 61, h: 67, part: "letter" },
  { src: letterASrc, x: 62, y: 202, w: 66, h: 66, part: "letter" },
  { src: letterTSrc, x: 128, y: 202, w: 43, h: 66, part: "letter" },
  { src: letterC2Src, x: 171, y: 202, w: 58, h: 67, part: "letter" },
  { src: letterHSrc, x: 229, y: 202, w: 60, h: 66, part: "letter" },
  { src: letterESrc, x: 292, y: 202, w: 47, h: 66, part: "letter" },
  { src: letterC3Src, x: 339, y: 202, w: 60, h: 67, part: "letter" },
  { src: letterKSrc, x: 400, y: 202, w: 59, h: 67, part: "letter" },
];

const pct = (value: number, total: number) => `${String((value / total) * 100)}%`;

function LayerImage({ layer }: { layer: Layer }) {
  return (
    <img
      src={layer.src}
      alt=""
      draggable={false}
      data-splash={layer.part}
      className="absolute max-w-none select-none will-change-transform"
      style={{
        left: pct(layer.x, FRAME.w),
        top: pct(layer.y, FRAME.h),
        width: pct(layer.w, FRAME.w),
        height: pct(layer.h, FRAME.h),
      }}
    />
  );
}
const HANDOFF_TIMEOUT_MS = 8000;
const SPIN_DELAY_MS = 250;

function canAnimate() {
  return (
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    !window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

export function SplashHost() {
  const phase = useSplashStore((s) => s.phase);
  const runId = useSplashStore((s) => s.runId);
  if (phase === "hidden") return null;
  return <SplashOverlay phase={phase} runId={runId} />;
}

function SplashOverlay({ phase, runId }: { phase: Exclude<SplashPhase, "hidden">; runId: number }) {
  const { t } = useTranslation("common");
  const rootRef = useRef<HTMLDivElement>(null);
  const breatheRef = useRef<gsap.core.Tween | null>(null);
  const [showLoading, setShowLoading] = useState(false);

  useGSAP(
    () => {
      const root = rootRef.current;
      if (!root) return;
      const q = gsap.utils.selector(root);
      const content = q("[data-splash='content']");
      const mark = q("[data-splash='mark']");
      const head = q("[data-splash='head']");
      const ears = q("[data-splash='ears']");
      const check = q("[data-splash='check']");
      const letters = q("[data-splash='letter']");
      const tagline = q("[data-splash='tagline']");
      const motion = canAnimate();
      const store = useSplashStore.getState();

      const stopBreathing = () => {
        breatheRef.current?.kill();
        breatheRef.current = null;
      };
      const showStatic = () => {
        gsap.set(content, { autoAlpha: 1, y: 0, scale: 1 });
        gsap.set(mark, { scale: 1, scaleX: 1, scaleY: 1, rotation: 0 });
        gsap.set(head, { autoAlpha: 1, "--sweep": "360deg" });
        gsap.set(ears, { autoAlpha: 1, scale: 1, transformOrigin: "50% 100%" });
        gsap.set(check, { autoAlpha: 1, clipPath: "inset(0% 0% 0% 0%)" });
        gsap.set(letters, { autoAlpha: 1, y: 0, rotation: 0 });
        gsap.set(tagline, { autoAlpha: 1, y: 0 });
      };
      const squash = {
        keyframes: [
          { scaleX: 1.08, scaleY: 0.93, duration: 0.12, ease: "power1.out" },
          { scaleX: 1, scaleY: 1, duration: 0.5, ease: "elastic.out(1, 0.45)" },
        ],
      };

      if (phase === "boot") {
        showStatic();
        if (!motion) {
          gsap.set(root, { autoAlpha: 1 });
          return;
        }
        // Delay so a fast session check never flashes the logo.
        gsap.fromTo(root, { autoAlpha: 0 }, { autoAlpha: 1, duration: 0.2, delay: 0.15, ease: "power1.out" });
        breatheRef.current = gsap.to(mark, {
          scale: 1.035,
          duration: 1,
          delay: 0.35,
          ease: "sine.inOut",
          yoyo: true,
          repeat: -1,
        });
        return stopBreathing;
      }

      if (phase === "exit") {
        stopBreathing();
        if (!motion || Number(gsap.getProperty(root, "opacity")) < 0.05) {
          store.hide();
          return;
        }
        const out = gsap
          .timeline({
            onComplete: () => {
              useSplashStore.getState().hide();
            },
          })
          .to(content, { y: -12, scale: 0.96, autoAlpha: 0, duration: 0.24, ease: "power2.in" })
          .to(root, { autoAlpha: 0, duration: 0.32, ease: "power2.out" }, "-=0.1");
        return () => {
          out.kill();
        };
      }

      stopBreathing();
      let cancelled = false;
      let loadingTimer: number | undefined;
      let handoffTimer: number | undefined;
      const cancelHandOff = () => {
        cancelled = true;
        window.clearTimeout(loadingTimer);
        window.clearTimeout(handoffTimer);
      };
      const handOff = () => {
        const pending = useSplashStore.getState().pending ?? Promise.resolve(() => undefined);
        loadingTimer = window.setTimeout(() => {
          if (!cancelled) setShowLoading(true);
        }, SPIN_DELAY_MS);
        const timeout = new Promise<() => void>((resolve) => {
          handoffTimer = window.setTimeout(() => {
            resolve(() => undefined);
          }, HANDOFF_TIMEOUT_MS);
        });
        void Promise.race([pending, timeout]).then((reveal) => {
          window.clearTimeout(loadingTimer);
          window.clearTimeout(handoffTimer);
          if (cancelled || useSplashStore.getState().runId !== runId) return;
          setShowLoading(false);
          reveal();
          // Let the next route commit under the overlay before fading out.
          requestAnimationFrame(() => {
            requestAnimationFrame(() => {
              if (!cancelled) useSplashStore.getState().exit();
            });
          });
        });
      };

      if (!motion) {
        showStatic();
        gsap.set(root, { autoAlpha: 1 });
        handOff();
        return cancelHandOff;
      }

      const fromVisible = Number(gsap.getProperty(root, "opacity")) > 0.5;
      const tl = gsap.timeline({ defaults: { ease: "power3.out" }, onComplete: handOff });

      if (fromVisible) {
        showStatic();
        tl.fromTo(check, { clipPath: "inset(0% 100% 0% 0%)" }, { clipPath: "inset(0% 0% 0% 0%)", duration: 0.24, ease: "power2.out" })
          .to(mark, squash, ">-0.05")
          .to(
            letters,
            {
              keyframes: [
                { y: -8, duration: 0.16, ease: "power2.out" },
                { y: 0, duration: 0.34, ease: "bounce.out" },
              ],
              stagger: 0.035,
            },
            "<",
          )
          .fromTo(tagline, { autoAlpha: 0, y: 6 }, { autoAlpha: 1, y: 0, duration: 0.3, ease: "power1.out" }, "<0.2");
      } else {
        gsap.set(content, { autoAlpha: 1, y: 0, scale: 1 });
        gsap.set(mark, { scale: 1, rotation: 0 });
        tl.fromTo(root, { autoAlpha: 0 }, { autoAlpha: 1, duration: 0.24, ease: "power1.out" })
          // Head outline draws itself around the face, starting from the open bottom-left tip.
          .fromTo(
            head,
            { autoAlpha: 1, "--sweep": "0deg" },
            { "--sweep": "360deg", duration: 0.7, ease: "power2.inOut" },
            0.08,
          )
          .fromTo(
            ears,
            { autoAlpha: 0, scale: 0, transformOrigin: "50% 100%" },
            { autoAlpha: 1, scale: 1, duration: 0.36, ease: "back.out(2.6)" },
            0.5,
          )
          .fromTo(
            check,
            { autoAlpha: 1, clipPath: "inset(0% 100% 0% 0%)" },
            { clipPath: "inset(0% 0% 0% 0%)", duration: 0.32, ease: "power2.out" },
            0.66,
          )
          .to(mark, squash, 0.94)
          .fromTo(
            letters,
            { autoAlpha: 0, y: 24, rotation: 6 },
            { autoAlpha: 1, y: 0, rotation: 0, duration: 0.42, ease: "back.out(2)", stagger: 0.045 },
            0.86,
          )
          .fromTo(tagline, { autoAlpha: 0, y: 6 }, { autoAlpha: 1, y: 0, duration: 0.3, ease: "power1.out" }, 1.2)
          .to({}, { duration: 0.45 });
      }

      return () => {
        cancelHandOff();
        tl.kill();
      };
    },
    { scope: rootRef, dependencies: [phase, runId] },
  );

  return (
    <div
      ref={rootRef}
      role="status"
      aria-live="polite"
      className="invisible fixed inset-0 z-[var(--z-modal)] flex items-center justify-center bg-surface opacity-0"
    >
      <span className="sr-only">{t("actions.loading")}</span>
      <div
        data-splash="content"
        className="relative w-[min(78vw,300px)] md:w-[340px]"
        style={{ aspectRatio: `${String(FRAME.w)} / ${String(FRAME.h)}` }}
        aria-hidden="true"
      >
        <div
          data-splash="mark"
          className="absolute inset-x-0 top-0 will-change-transform"
          style={{ height: pct(182, FRAME.h), transformOrigin: "50% 100%" }}
        >
          <div className="absolute inset-x-0 top-0" style={{ height: pct(FRAME.h, 182) }}>
            {MARK_LAYERS.map((layer) => (
              <LayerImage key={layer.src} layer={layer} />
            ))}
          </div>
        </div>
        {LETTER_LAYERS.map((layer) => (
          <LayerImage key={layer.src} layer={layer} />
        ))}
        <span
          data-splash="tagline"
          className="absolute inset-x-0 block whitespace-nowrap text-center text-[clamp(9px,2.6vw,12px)] font-bold leading-none tracking-[0.12em] text-text-tertiary will-change-transform"
          style={{ top: pct(283, FRAME.h) }}
        >
          {t("welcome.slogan")}
        </span>
        {showLoading && phase === "intro" && (
          <span className="absolute inset-x-0 top-full mt-8 flex justify-center" aria-hidden="true">
            <Spin size="default" />
          </span>
        )}
      </div>
    </div>
  );
}
