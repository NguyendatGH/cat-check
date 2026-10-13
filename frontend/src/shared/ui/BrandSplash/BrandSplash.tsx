import { useRef } from "react";
import { useTranslation } from "react-i18next";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import { useSplashStore, type SplashPhase } from "@/shared/lib/motion/splash";

gsap.registerPlugin(useGSAP);

const WORDMARK = Array.from("CATCHECK");
const CAT_LETTERS = 3;
const HANDOFF_TIMEOUT_MS = 8000;

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

  useGSAP(
    () => {
      const root = rootRef.current;
      if (!root) return;
      const q = gsap.utils.selector(root);
      const content = q("[data-splash='content']");
      const mark = q("[data-splash='mark']");
      const ring = q("[data-splash='ring']");
      const check = q("[data-splash='check']");
      const ears = q("[data-splash='ear']");
      const letters = q("[data-splash='letter']");
      const motion = canAnimate();
      const store = useSplashStore.getState();

      const stopBreathing = () => {
        breatheRef.current?.kill();
        breatheRef.current = null;
      };
      const showStatic = () => {
        gsap.set(content, { autoAlpha: 1, y: 0, scale: 1 });
        gsap.set(mark, { autoAlpha: 1, scale: 1, scaleX: 1, scaleY: 1, rotation: 0 });
        gsap.set([ring, check], { strokeDashoffset: 0 });
        gsap.set(ears, { scale: 1, transformOrigin: "50% 100%" });
        gsap.set(letters, { autoAlpha: 1, y: 0, rotation: 0 });
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
      const handOff = () => {
        const pending = useSplashStore.getState().pending ?? Promise.resolve(() => undefined);
        const timeout = new Promise<() => void>((resolve) => {
          window.setTimeout(() => {
            resolve(() => undefined);
          }, HANDOFF_TIMEOUT_MS);
        });
        void Promise.race([pending, timeout]).then((reveal) => {
          if (cancelled || useSplashStore.getState().runId !== runId) return;
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
        return () => {
          cancelled = true;
        };
      }

      const fromVisible = Number(gsap.getProperty(root, "opacity")) > 0.5;
      const tl = gsap.timeline({ defaults: { ease: "power3.out" }, onComplete: handOff });

      if (fromVisible) {
        showStatic();
        tl.fromTo(check, { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: 0.2, ease: "power2.out" })
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
          );
      } else {
        gsap.set(content, { autoAlpha: 1, y: 0, scale: 1 });
        tl.fromTo(root, { autoAlpha: 0 }, { autoAlpha: 1, duration: 0.28, ease: "power1.out" })
          .fromTo(
            mark,
            { autoAlpha: 0, scale: 0.6, rotation: -12 },
            { autoAlpha: 1, scale: 1, rotation: 0, duration: 0.6, ease: "back.out(1.7)" },
            0.1,
          )
          .fromTo(ring, { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: 0.55, ease: "power2.inOut" }, 0.1)
          .fromTo(
            ears,
            { scale: 0, transformOrigin: "50% 100%" },
            { scale: 1, duration: 0.4, ease: "back.out(2.6)", stagger: 0.06 },
            0.38,
          )
          .fromTo(check, { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: 0.2, ease: "power2.out" }, 0.55)
          .fromTo(
            letters,
            { autoAlpha: 0, y: 20, rotation: 8 },
            { autoAlpha: 1, y: 0, rotation: 0, duration: 0.45, ease: "back.out(2.2)", stagger: 0.04 },
            0.62,
          )
          .to(mark, squash, 0.85)
          .to({}, { duration: 0.08 });
      }

      return () => {
        cancelled = true;
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
      <div data-splash="content" className="flex flex-col items-center gap-5" aria-hidden="true">
        <svg data-splash="mark" viewBox="0 0 120 120" className="size-24 will-change-transform md:size-28" fill="none">
          <path
            data-splash="ear"
            d="M28 46 L32 12 L58 30 Z"
            fill="var(--color-primary)"
            stroke="var(--color-primary)"
            strokeWidth="6"
            strokeLinejoin="round"
          />
          <path
            data-splash="ear"
            d="M92 46 L88 12 L62 30 Z"
            fill="var(--color-primary)"
            stroke="var(--color-primary)"
            strokeWidth="6"
            strokeLinejoin="round"
          />
          <path data-splash="ear" d="M35 36 L37 21 L49 30 Z" fill="var(--color-secondary)" />
          <path data-splash="ear" d="M85 36 L83 21 L71 30 Z" fill="var(--color-secondary)" />
          <path
            data-splash="ring"
            d="M41 98.9 A38 38 0 1 1 79 98.9"
            pathLength={1}
            strokeDasharray="1"
            stroke="var(--color-primary)"
            strokeWidth="10"
            strokeLinecap="round"
          />
          <path
            data-splash="check"
            d="M44 66 L56 78 L78 54"
            pathLength={1}
            strokeDasharray="1"
            stroke="var(--color-secondary)"
            strokeWidth="11"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        <div className="flex text-[1.75rem] font-extrabold leading-none tracking-[0.04em] md:text-[2rem]">
          {WORDMARK.map((char, index) => (
            <span
              key={String(index)}
              data-splash="letter"
              className={`inline-block will-change-transform ${index < CAT_LETTERS ? "text-primary" : "text-secondary"}`}
            >
              {char}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}
