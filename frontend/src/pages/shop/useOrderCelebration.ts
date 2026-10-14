import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import type { RefObject } from "react";

gsap.registerPlugin(useGSAP);

export const CONFETTI_CLASSES = ["bg-primary", "bg-secondary", "bg-success", "bg-warning", "bg-primary-dark"] as const;
export const CONFETTI_COUNT = 26;

export function useOrderCelebration(scope: RefObject<HTMLElement | null>, enabled: boolean) {
  useGSAP(
    () => {
      const root = scope.current;
      if (!root || !enabled) return;
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

      const q = gsap.utils.selector(root);
      const icon = q("[data-celebrate='icon']");
      const ring = q("[data-celebrate='ring']");
      const confetti = q("[data-celebrate='confetti']");
      const text = q("[data-celebrate='text']");

      const tl = gsap.timeline({ defaults: { ease: "power3.out" } });
      tl.fromTo(icon, { scale: 0, rotation: -25 }, { scale: 1, rotation: 0, duration: 0.55, ease: "back.out(2.2)" })
        .fromTo(ring, { scale: 0.8, autoAlpha: 0.7 }, { scale: 2.1, autoAlpha: 0, duration: 0.8, ease: "power2.out" }, 0.18)
        .fromTo(text, { autoAlpha: 0, y: 12 }, { autoAlpha: 1, y: 0, duration: 0.42, stagger: 0.06 }, 0.25);

      confetti.forEach((piece) => {
        const angle = gsap.utils.random(-170, -10) * (Math.PI / 180);
        const distance = gsap.utils.random(70, 150);
        const x = Math.cos(angle) * distance;
        const y = Math.sin(angle) * distance;
        tl.fromTo(
          piece,
          { x: 0, y: 0, scale: 0, rotation: 0, autoAlpha: 1 },
          {
            keyframes: [
              { x, y, scale: 1, rotation: gsap.utils.random(-220, 220), duration: 0.55, ease: "power3.out" },
              { y: y + gsap.utils.random(60, 110), autoAlpha: 0, duration: 0.75, ease: "power1.in" },
            ],
          },
          0.15 + gsap.utils.random(0, 0.08),
        );
      });

      return () => {
        tl.progress(1).kill();
      };
    },
    { scope, dependencies: [enabled] },
  );
}
