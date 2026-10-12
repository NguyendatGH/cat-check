import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router";
import { useSessionStore, type Role } from "@/entities/user";
import { RedirectIfAuthenticated } from "./RedirectIfAuthenticated";

let mounted: { root: Root; container: HTMLElement } | undefined;

function renderEntry(path: string): HTMLElement {
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<RedirectIfAuthenticated />}>
            <Route path="/" element={<div data-testid="public" />} />
            <Route path="/auth/login" element={<div data-testid="login" />} />
          </Route>
          <Route path="/dashboard" element={<div data-testid="buyer" />} />
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

describe("RedirectIfAuthenticated", () => {
  it.each(["/", "/auth/login"])("opens admin UI for ADMIN_SUPER from %s", (path) => {
    signIn(["USER", "ADMIN_SUPER"]);
    expect(renderEntry(path).querySelector('[data-testid="admin"]')).not.toBeNull();
  });

  it("prefers admin UI when a super admin also has seller permission", () => {
    signIn(["USER", "ADMIN_SUPER", "ADMIN_CATALOG"]);
    expect(renderEntry("/").querySelector('[data-testid="admin"]')).not.toBeNull();
  });

  it("opens seller UI for catalog staff", () => {
    signIn(["USER", "ADMIN_CATALOG"]);
    expect(renderEntry("/").querySelector('[data-testid="seller"]')).not.toBeNull();
  });

  it("opens an authorized admin page for moderator", () => {
    signIn(["USER", "MODERATOR"]);
    expect(renderEntry("/").querySelector('[data-testid="moderator"]')).not.toBeNull();
  });

  it("keeps the buyer dashboard for ordinary users", () => {
    signIn(["USER"]);
    expect(renderEntry("/").querySelector('[data-testid="buyer"]')).not.toBeNull();
  });

  it("does not redirect anonymous visitors", () => {
    expect(renderEntry("/auth/login").querySelector('[data-testid="login"]')).not.toBeNull();
  });
});
