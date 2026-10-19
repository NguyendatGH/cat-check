import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import type { RefObject } from "react";

gsap.registerPlugin(useGSAP);
gsap.defaults({ ease: "sine.out", duration: 0.32 });

const INTERACTIVE =
  [
    "button:not(:disabled)",
    "a[href]",
    "[role='button']:not([aria-disabled='true'])",
    "[role='link']:not([aria-disabled='true'])",
    "[role='tab']",
    "[role='switch']",
    "[role='checkbox']",
    "[role='radio']",
    ".motion-card",
  ].join(", ");

const TWEEN = { duration: 0.2, ease: "sine.out", overwrite: "auto" as const };
const PRESS_TWEEN = { duration: 0.1, ease: "power1.out", overwrite: "auto" as const };

type MotionKind = "card" | "row" | "control";

function prefersReducedMotion() {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return false;
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

const kindCache = new WeakMap<HTMLElement, MotionKind>();
const skipCache = new WeakMap<HTMLElement, boolean>();

function motionKind(element: HTMLElement): MotionKind {
  const cached = kindCache.get(element);
  if (cached) return cached;
  const attr = element.getAttribute("data-motion");
  let kind: MotionKind;
  if (attr === "card" || attr === "row" || attr === "control") kind = attr;
  else if (element.classList.contains("motion-card")) kind = "card";
  else kind = element.offsetWidth >= 240 ? "row" : "control";
  kindCache.set(element, kind);
  return kind;
}

// GSAP writes inline transforms every frame; a CSS transition on the same property would chase
// each frame and make the motion lag, so elements that already animate transforms in CSS are left alone.
function skipMotion(element: HTMLElement) {
  const cached = skipCache.get(element);
  if (cached !== undefined) return cached;
  let skip = Boolean(element.closest("[data-motion='none']"));
  if (!skip && /(?:^|\s)-translate-(?:x|y)-1\/2(?:\s|$)/.test(element.className)) skip = true;
  if (!skip) {
    const props = getComputedStyle(element).transitionProperty;
    skip = /\b(all|transform|translate|scale)\b/.test(props);
  }
  skipCache.set(element, skip);
  return skip;
}

function hoverVars(element: HTMLElement) {
  return motionKind(element) === "card" ? { y: -3, scale: 1 } : { y: 0, scale: 1 };
}

function restVars(element: HTMLElement, hovered: boolean) {
  return hovered ? hoverVars(element) : { y: 0, scale: 1 };
}

function pressVars(element: HTMLElement) {
  const kind = motionKind(element);
  if (kind === "card") return { y: -1, scale: 0.995 };
  if (kind === "row") return { y: 0, scale: 0.997 };
  return { y: 0, scale: 0.97 };
}

export function usePageReveal(scope: RefObject<HTMLElement | null>) {
  useGSAP(
    () => {
      const page = scope.current;
      if (!page) return;
      if (page.hasAttribute("data-motion-scene") || page.querySelector("[data-motion-scene]")) return;

      const marked = gsap.utils.toArray<HTMLElement>("[data-reveal]", page);
      const items = (marked.length > 0 ? marked : [page]).slice(0, 12);

      if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
        gsap.set(items, { autoAlpha: 1, clearProps: "transform,opacity,visibility" });
        return;
      }

      const media = gsap.matchMedia();
      media.add("(prefers-reduced-motion: reduce)", () => {
        gsap.set(items, { autoAlpha: 1, clearProps: "transform,opacity,visibility" });
      });
      media.add("(prefers-reduced-motion: no-preference)", () => {
        const visible = items.filter((el) => el.getClientRects().length > 0);
        if (visible.length === 0) return;
        const intro = gsap.fromTo(
          visible,
          { autoAlpha: 0, y: 10 },
          {
            autoAlpha: 1,
            y: 0,
            duration: 0.26,
            ease: "power2.out",
            stagger: Math.min(0.035, 0.18 / visible.length),
            clearProps: "transform,opacity,visibility",
          },
        );
        return () => {
          intro.progress(1).kill();
        };
      });

      return () => {
        media.revert();
      };
    },
    { scope },
  );
}

export function useWelcomeScene(scope: RefObject<HTMLElement | null>) {
  useGSAP(
    () => {
      const root = scope.current;
      if (!root) return;

      if (prefersReducedMotion()) {
        gsap.set(root.querySelectorAll("*"), { autoAlpha: 1, clearProps: "transform,opacity,visibility" });
        return;
      }

      const glows = root.querySelectorAll("[data-scene='glow']");
      const copyBlocks = root.querySelectorAll("[data-scene='copy']");
      const badges = root.querySelectorAll(".relative > span.absolute");

      const tl = gsap.timeline({ defaults: { ease: "sine.out" } });

      if (glows.length > 0) {
        tl.fromTo(
          glows,
          { autoAlpha: 0, scale: 0.92 },
          { autoAlpha: 1, scale: 1, duration: 0.8, stagger: 0.12, ease: "sine.out" },
          0,
        );
      }

      if (copyBlocks.length > 0) {
        tl.fromTo(
          copyBlocks,
          { autoAlpha: 0, y: 8 },
          { autoAlpha: 1, y: 0, duration: 0.44, stagger: 0.08, ease: "sine.out" },
          0.08,
        );
      }

      if (badges.length > 0) {
        tl.fromTo(
          badges,
          { autoAlpha: 0, scale: 0.75 },
          { autoAlpha: 1, scale: 1, duration: 0.36, stagger: 0.06, ease: "back.out(1.4)" },
          0.16,
        );
      }

      return () => {
        tl.kill();
      };
    },
    { scope },
  );
}

export function useMicroInteractions(scope: RefObject<HTMLElement | null>) {
  useGSAP(
    (_context, contextSafe) => {
      const root = scope.current;
      if (!root || !contextSafe) return;

      if (typeof window === "undefined" || typeof window.matchMedia !== "function") return;

      const media = gsap.matchMedia();
      media.add("(prefers-reduced-motion: no-preference)", () => {
        const findInteractive = (target: EventTarget | null) => {
          if (!(target instanceof Element)) return null;
          const element = target.closest<HTMLElement>(INTERACTIVE);
          return element && root.contains(element) && !skipMotion(element) ? element : null;
        };
        const animateIn = contextSafe((event: Event) => {
          const element = findInteractive(event.target);
          if (!element || (event instanceof PointerEvent && element.contains(event.relatedTarget as Node | null))) return;
          gsap.to(element, { ...hoverVars(element), ...TWEEN });
        });
        const animateOut = contextSafe((event: Event) => {
          const element = findInteractive(event.target);
          if (!element || (event instanceof PointerEvent && element.contains(event.relatedTarget as Node | null))) return;
          gsap.to(element, { y: 0, scale: 1, ...TWEEN });
        });
        const press = contextSafe((event: Event) => {
          const element = findInteractive(event.target);
          if (element) gsap.to(element, { ...pressVars(element), ...PRESS_TWEEN });
        });
        const release = contextSafe((event: Event) => {
          const element = findInteractive(event.target);
          if (!element) return;
          const mouseHovered = event instanceof PointerEvent && event.pointerType === "mouse" && element.matches(":hover");
          const hovered = mouseHovered || element === document.activeElement;
          gsap.to(element, { ...restVars(element, hovered), ...TWEEN });
        });

        root.addEventListener("pointerover", animateIn);
        root.addEventListener("pointerout", animateOut);
        root.addEventListener("pointerdown", press);
        root.addEventListener("pointerup", release);
        root.addEventListener("pointercancel", release);
        root.addEventListener("focusin", animateIn);
        root.addEventListener("focusout", animateOut);

        return () => {
          root.removeEventListener("pointerover", animateIn);
          root.removeEventListener("pointerout", animateOut);
          root.removeEventListener("pointerdown", press);
          root.removeEventListener("pointerup", release);
          root.removeEventListener("pointercancel", release);
          root.removeEventListener("focusin", animateIn);
          root.removeEventListener("focusout", animateOut);
        };
      });

      return () => {
        media.revert();
      };
    },
    { scope },
  );
}

export function useListReveal(scope: RefObject<HTMLElement | null>, dependency: string | number | boolean) {
  useGSAP(
    () => {
      const container = scope.current;
      if (!container) return;

      if (prefersReducedMotion()) {
        gsap.set(container.querySelectorAll("[data-reveal-item], :scope > *"), {
          autoAlpha: 1,
          clearProps: "transform,opacity,visibility",
        });
        return;
      }

      const marked = gsap.utils.toArray<HTMLElement>("[data-reveal-item]", container);
      const all = marked.length > 0 ? marked : (Array.from(container.children) as HTMLElement[]);
      const items = all.filter((el) => !el.hasAttribute("data-revealed") && el.getClientRects().length > 0);
      if (items.length === 0) return;
      items.forEach((el) => {
        el.setAttribute("data-revealed", "");
      });

      const tween = gsap.fromTo(
        items,
        { autoAlpha: 0, y: 8 },
        {
          autoAlpha: 1,
          y: 0,
          duration: 0.24,
          ease: "power2.out",
          stagger: Math.min(0.03, 0.18 / items.length),
          clearProps: "transform,opacity,visibility",
        },
      );

      return () => {
        tween.progress(1).kill();
      };
    },
    { scope, dependencies: [dependency] },
  );
}
