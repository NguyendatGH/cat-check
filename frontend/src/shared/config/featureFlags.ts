export type FeatureFlagKey = "community" | "map" | "shop";

export const featureFlags: Record<FeatureFlagKey, boolean> = {
  community: true,
  map: true,
  shop: true,
};
