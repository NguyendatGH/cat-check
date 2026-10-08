import type { HTMLAttributes } from "react";
import { cn } from "../cn";
import { skeletonVariants, type SkeletonVariantProps } from "./SkeletonLoader.variants";

export interface SkeletonLoaderProps extends HTMLAttributes<HTMLDivElement>, SkeletonVariantProps {}

/** SkeletonLoader — pattern loading giữ chỗ, shape text/card/circle, aria-hidden vì chỉ trang trí. */
export function SkeletonLoader({ className, shape, ...props }: SkeletonLoaderProps) {
  return <div aria-hidden="true" className={cn(skeletonVariants({ shape }), className)} {...props} />;
}
