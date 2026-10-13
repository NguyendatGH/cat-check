import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router";
import { useSessionStore, type Role } from "@/entities/user";
import { RedirectStaff } from "./RedirectStaff";

let mounted: { root: Root; container: HTMLElement } | undefined;

function renderEntry(path: string): HTMLElement {
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<RedirectStaff />}>
            <Route path="/dashboard" element={<div data-testid="buyer" />} />
          </Route>
          <Route
            path="/history"
            element={
              <RedirectStaff>
                <div data-testid="buyer-wrapped" />
              </RedirectStaff>
            }
          />
          <Route path="/admin" element={<div data-testid="admin" />} />
          <Route path="/seller" element={<div data-testid="seller" />} />
          <Route path="/admin/community/reports" element={<div data-testid="moderator" />} />
        </Routes>
      </MemoryRouter>,
    );
  });
  mounted = { root, container };
  return container;
}

function signIn(roles: Role[]) {
  useSessionStore.getState().setSession({
    id: "test-user",
    email: "test@example.com",
    displayName: "Test",
    roles,
    onboarding: { completed: true, nextStep: null },
  });
}

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  useSessionStore.getState().setSession(null);
});

afterEach(() => {
  if (!mounted) return;
  act(() => mounted?.root.unmount());
  mounted.container.remove();
  mounted = undefined;
});

describe("RedirectStaff", () => {
  it.each([
    [["USER", "ADMIN_SUPER"], "admin"],
    [["USER", "DPO"], "admin"],
    [["USER", "ADMIN_CATALOG"], "seller"],
    [["USER", "MODERATOR"], "moderator"],
  ] as [Role[], string][])("sends staff %j away from buyer pages to %s", (roles, target) => {
    signIn(roles);
    expect(renderEntry("/dashboard").querySelector(`[data-testid="${target}"]`)).not.toBeNull();
    act(() => mounted?.root.unmount());
    mounted?.container.remove();
    mounted = undefined;
    expect(renderEntry("/history").querySelector(`[data-testid="${target}"]`)).not.toBeNull();
  });

  it("lets ordinary users into buyer pages", () => {
    signIn(["USER"]);
    expect(renderEntry("/dashboard").querySelector('[data-testid="buyer"]')).not.toBeNull();
  });

  it("lets anonymous visitors through", () => {
    expect(renderEntry("/history").querySelector('[data-testid="buyer-wrapped"]')).not.toBeNull();
  });
});
