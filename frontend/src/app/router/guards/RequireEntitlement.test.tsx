import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RequireEntitlement } from "./RequireEntitlement";

interface EntitlementState {
  data: unknown;
  isPending: boolean;
  isError: boolean;
}

const entitlementState = vi.hoisted(() => {
  const value: EntitlementState = { data: undefined, isPending: true, isError: false };
  return { value };
});

vi.mock("@/features/credit", () => ({
  useEntitlement: () => entitlementState.value,
}));

vi.mock("@/pages/system/UpsellPage", () => ({
  UpsellPage: () => <div data-testid="upsell" />,
}));

let mounted: { root: Root; container: HTMLElement } | undefined;

function render(): HTMLElement {
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <RequireEntitlement entitlement="hasExport">
        <div data-testid="gated" />
      </RequireEntitlement>,
    );
  });
  mounted = { root, container };
  return container;
}

afterEach(() => {
  if (mounted) {
    act(() => {
      mounted?.root.unmount();
    });
    mounted.container.remove();
    mounted = undefined;
  }
});

describe("RequireEntitlement", () => {
  it("renders nothing while the entitlement loads", () => {
    entitlementState.value = { data: undefined, isPending: true, isError: false };
    const el = render();
    expect(el.querySelector("[data-testid=gated]")).toBeNull();
    expect(el.querySelector("[data-testid=upsell]")).toBeNull();
  });

  it("shows the upsell when the feature is not entitled", () => {
    entitlementState.value = { data: { hasExport: false }, isPending: false, isError: false };
    const el = render();
    expect(el.querySelector("[data-testid=upsell]")).not.toBeNull();
    expect(el.querySelector("[data-testid=gated]")).toBeNull();
  });

  it("renders children when entitled", () => {
    entitlementState.value = { data: { hasExport: true }, isPending: false, isError: false };
    expect(render().querySelector("[data-testid=gated]")).not.toBeNull();
  });

  it("falls back to children when the entitlement cannot be loaded", () => {
    entitlementState.value = { data: undefined, isPending: false, isError: true };
    expect(render().querySelector("[data-testid=gated]")).not.toBeNull();
  });
});
