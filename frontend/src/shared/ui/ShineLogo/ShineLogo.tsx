import type { CSSProperties } from "react";

export interface ShineLogoProps {
  src: string;
  alt: string;
  /** Sizing + object-fit classes for the image; the shine mask follows them. */
  className?: string;
  align?: "left" | "center";
}

/** Brand logo with a glossy sweep every 5s, masked to the logo's opaque pixels. */
export function ShineLogo({ src, alt, className, align = "center" }: ShineLogoProps) {
  const maskStyle = {
    "--logo-mask": `url("${src}")`,
    "--logo-mask-pos": align === "left" ? "left center" : "center",
  } as CSSProperties;

  return (
    <span className="relative inline-flex">
      <img src={src} alt={alt} className={className} />
      <span aria-hidden="true" className="logo-shine" style={maskStyle} />
    </span>
  );
}
