import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useSessionStore, type SessionUser } from "@/entities/user";
import { AdminAccessProvider, computeCapabilities } from "@/features/admin";
import { ROLES } from "@/shared/auth/roles";
import { SellerContentPage } from "./SellerContentPage";

const m = vi.hoisted(() => ({
  tips: [] as Record<string, unknown>[],
  updateAsync: vi.fn(),
  submitMutate: vi.fn(),
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));
vi.mock("@/features/admin", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/admin")>();
  const idle = {
    isPending: false,
    isError: false,
    isSuccess: false,
    isLoading: false,
    data: undefined,
    error: null,
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    reset: vi.fn(),
  };
  return {
    ...actual,
    useAdminCareTips: () => ({ ...idle, data: { items: m.tips, page: 0, size: 20, total: m.tips.length } }),
    useCreateCareTip: () => idle,
    useUpdateCareTip: () => ({ ...idle, mutate: vi.fn(), mutateAsync: m.updateAsync }),
    useSubmitCareTipReview: () => ({ ...idle, mutate: m.submitMutate }),
    usePublishCareTip: () => idle,
    useArchiveCareTip: () => idle,
  };
});

const baseTip = {
  id: "t1",
  slug: "uong-nuoc",
  translationGroupId: "g1",
  locale: "vi",
  kind: "ARTICLE",
  category: null,
  title: "Uống nước",
  summary: null,
  bodyMd: "Nội dung",
  coverImageUrl: null,
  tags: null,
  claimType: "NONE",
  sourceReference: null,
  sortWeight: 100,
  reviewedBy: null,
  reviewedAt: null,
  publishedAt: null,
  createdAt: "2026-10-01T00:00:00Z",
  updatedAt: "2026-10-01T00:00:00Z",
};

let mounted: Root | undefined;

function render(page: React.ReactElement): void {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  useSessionStore.getState().setSession({
    id: "u1",
    email: "admin@example.com",
    displayName: "Admin",
    roles: ["ADMIN_SUPER"],
  } as unknown as SessionUser);
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AdminAccessProvider capabilities={computeCapabilities(ROLES, () => true)}>{page}</AdminAccessProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );
  });
  mounted = root;
}

function button(label: string): HTMLButtonElement | undefined {
  return Array.from(document.body.querySelectorAll("button")).find((el) => el.textContent.trim() === label);
}

function openEditor(): void {
  const open = button("actions.open");
  expect(open).toBeDefined();
  act(() => {
    open?.click();
  });
}

function typeBody(value: string): void {
  const textarea = document.body.querySelector("textarea");
  expect(textarea).not.toBeNull();
  act(() => {
    // Gọi setter gốc để React nhận thay đổi của controlled input.
    Reflect.set(HTMLTextAreaElement.prototype, "value", value, textarea);
    textarea?.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

async function clickAsync(el: HTMLButtonElement | undefined): Promise<void> {
  expect(el).toBeDefined();
  await act(async () => {
    el?.click();
    await Promise.resolve();
  });
}

const page = () => <SellerContentPage />;

describe("SellerContentPage CareTipEditor", () => {
  beforeEach(() => {
    m.updateAsync.mockReset();
    m.submitMutate.mockReset();
  });

  afterEach(() => {
    act(() => {
      mounted?.unmount();
    });
    mounted = undefined;
    document.body.innerHTML = "";
    useSessionStore.getState().setSession(null);
  });

  it("Gửi duyệt khi còn sửa chưa lưu: lưu trước rồi mới gửi duyệt", async () => {
    m.tips = [{ ...baseTip, status: "DRAFT", authorId: "u1" }];
    m.updateAsync.mockResolvedValue({});
    render(page());
    openEditor();
    typeBody("Nội dung mới");
    await clickAsync(button("content.submitReview"));
    expect(m.updateAsync).toHaveBeenCalledWith({
      tipId: "t1",
      payload: expect.objectContaining({ bodyMd: "Nội dung mới" }) as unknown,
    });
    expect(m.submitMutate).toHaveBeenCalledWith("t1");
  });

  it("Lưu thất bại thì không gửi duyệt", async () => {
    m.tips = [{ ...baseTip, status: "DRAFT", authorId: "u1" }];
    m.updateAsync.mockRejectedValue(new Error("conflict"));
    render(page());
    openEditor();
    typeBody("Nội dung mới");
    await clickAsync(button("content.submitReview"));
    expect(m.updateAsync).toHaveBeenCalled();
    expect(m.submitMutate).not.toHaveBeenCalled();
  });

  it("Không có thay đổi thì gửi duyệt thẳng, không lưu", async () => {
    m.tips = [{ ...baseTip, status: "DRAFT", authorId: "u1" }];
    render(page());
    openEditor();
    await clickAsync(button("content.submitReview"));
    expect(m.updateAsync).not.toHaveBeenCalled();
    expect(m.submitMutate).toHaveBeenCalledWith("t1");
  });

  it("Ẩn nút Xuất bản với bài do chính người đang đăng nhập viết", () => {
    m.tips = [{ ...baseTip, status: "IN_REVIEW", authorId: "u1" }];
    render(page());
    openEditor();
    expect(button("content.publishCta")).toBeUndefined();
  });

  it("Hiện nút Xuất bản với bài của người khác", () => {
    m.tips = [{ ...baseTip, status: "IN_REVIEW", authorId: "u2" }];
    render(page());
    openEditor();
    expect(button("content.publishCta")).toBeDefined();
  });
});
