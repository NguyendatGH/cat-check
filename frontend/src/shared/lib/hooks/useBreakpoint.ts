import { useMediaQuery } from "./useMediaQuery";

const BREAKPOINTS = {
  sm: 375,
  md: 768,
  lg: 1024,
  xl: 1280,
} as const;

export type BreakpointName = keyof typeof BREAKPOINTS;

export function useBreakpoint(name: BreakpointName): boolean {
  return useMediaQuery(`(min-width: ${String(BREAKPOINTS[name])}px)`);
}
