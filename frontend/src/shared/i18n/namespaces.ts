export const NAMESPACES = [
  "common",
  "errors",
  "auth",
  "onboarding",
  "cat",
  "scan",
  "history",
  "trends",
  "reminder",
  "export",
  "credit",
  "notification",
  "settings",
  "admin",
  "seller",
  "legal",
  "shop",
  "map",
  "community",
  "assistant",
] as const;

export type Namespace = (typeof NAMESPACES)[number];
