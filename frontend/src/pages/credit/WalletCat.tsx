import { useRef } from "react";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import { cn } from "@/shared/lib/cn";

// Brand cat (logo head + ears) with a face; sways, blinks and twitches an ear on a slow loop.
export function WalletCat({ className }: { className?: string }) {
  const ref = useRef<SVGSVGElement>(null);

  useGSAP(
    () => {
      const media = gsap.matchMedia();
      media.add("(prefers-reduced-motion: no-preference)", () => {
        gsap.set("[data-cat-eye]", { transformOrigin: "50% 50%" });
        gsap.set("[data-cat-ear='right']", { transformOrigin: "30% 100%" });
        gsap.to("[data-cat-body]", {
          rotation: 3,
          transformOrigin: "50% 90%",
          duration: 2.4,
          yoyo: true,
          repeat: -1,
          ease: "sine.inOut",
        });
        const blink = { scaleY: 0.08, duration: 0.07, yoyo: true, repeat: 1, ease: "power1.in" };
        gsap
          .timeline({ repeat: -1, delay: 1.2 })
          .to("[data-cat-eye]", blink, 0)
          .to("[data-cat-ear='right']", { rotation: 9, duration: 0.12, yoyo: true, repeat: 1, ease: "sine.inOut" }, 2.1)
          .to("[data-cat-eye]", blink, 3.4)
          .to("[data-cat-eye]", blink, 6.2)
          .to("[data-cat-eye]", blink, 6.48)
          .to({}, { duration: 1.6 });
      });
      return () => {
        media.revert();
      };
    },
    { scope: ref },
  );

  return (
    <svg ref={ref} viewBox="0 6 120 112" className={cn("overflow-visible", className)} aria-hidden="true">
      <g data-cat-body>
        <path
          data-cat-ear="left"
          d="M25.6 49.9 C20 34 18 18 26 15 C32 13 40 22 47 30.3 Z"
          className="fill-secondary stroke-primary"
          strokeWidth={8}
          strokeLinejoin="round"
        />
        <path
          data-cat-ear="right"
          d="M94.4 49.9 C100 34 102 18 94 15 C88 13 80 22 73 30.3 Z"
          className="fill-secondary stroke-primary"
          strokeWidth={8}
          strokeLinejoin="round"
        />
        <circle cx="60" cy="66" r="35" className="fill-surface" />
        <path
          d="M38.2 97.1 A38 38 0 1 1 81.8 97.1"
          fill="none"
          className="stroke-primary"
          strokeWidth={8}
          strokeLinecap="round"
        />

        <ellipse cx="36" cy="80" rx="6.5" ry="4" className="cat-blush" />
        <ellipse cx="84" cy="80" rx="6.5" ry="4" className="cat-blush" />
        {[45, 75].map((cx) => (
          <g key={cx} data-cat-eye>
            <ellipse cx={cx} cy="70" rx="6" ry="7" className="fill-primary-darker" />
            <circle cx={cx + 2} cy="67.4" r="2.2" className="fill-surface" />
            <circle cx={cx - 1.8} cy="72.6" r="1" className="fill-surface" />
          </g>
        ))}
        <ellipse cx="60" cy="77" rx="2.4" ry="1.7" className="fill-primary-darker" />
        <path
          d="M55.5 79.5 q2.25 2.8 4.5 0 q2.25 2.8 4.5 0"
          fill="none"
          className="stroke-primary-darker"
          strokeWidth={1.8}
          strokeLinecap="round"
          strokeLinejoin="round"
        />

        <circle cx="60" cy="104" r="8.5" className="fill-secondary stroke-surface" strokeWidth={2.5} />
        <path
          d="M56 104 l2.8 2.8 l5.2 -5.6"
          fill="none"
          className="stroke-surface"
          strokeWidth={2.6}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </g>
    </svg>
  );
}
