import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AssistantHistoryDrawer } from "./AssistantHistoryDrawer";

const api = vi.hoisted(() => ({
  fetchAiConversations: vi.fn(),
}));

vi.mock("@/features/assistant", () => ({
  fetchAiConversations: api.fetchAiConversations,
}));

const tMock = (key: string) => key;
vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: tMock,
    i18n: { language: "vi" },
  }),
}));

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  api.fetchAiConversations.mockReset();
});

afterEach(() => {
  vi.clearAllMocks();
  document.body.innerHTML = "";
});

describe("AssistantHistoryDrawer", () => {
  it("không gọi API khi drawer đóng", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(
        <AssistantHistoryDrawer
          open={false}
          onOpenChange={vi.fn()}
          onSelectConversation={vi.fn()}
          onNewChat={vi.fn()}
        />,
      );
    });

    expect(api.fetchAiConversations).not.toHaveBeenCalled();

    act(() => {
      root.unmount();
    });
  });

  it("gọi fetchAiConversations khi mở drawer và hiển thị danh sách", async () => {
    api.fetchAiConversations.mockResolvedValue([
      {
        id: "c1",
        userId: "u1",
        createdAt: "2026-10-10T10:00:00Z",
        updatedAt: "2026-10-10T10:05:00Z",
        preview: "Hỏi về cát vệ sinh",
      },
    ]);

    const onSelect = vi.fn();
    const onOpenChange = vi.fn();
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(
        <AssistantHistoryDrawer
          open={true}
          onOpenChange={onOpenChange}
          onSelectConversation={onSelect}
          onNewChat={vi.fn()}
        />,
      );
    });

    expect(api.fetchAiConversations).toHaveBeenCalledWith(30);

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 20));
    });

    const btn = Array.from(document.body.querySelectorAll("button")).find((b) =>
      b.textContent.includes("Hỏi về cát vệ sinh"),
    );
    expect(btn).toBeDefined();

    act(() => {
      btn?.click();
    });

    expect(onSelect).toHaveBeenCalledWith("c1");
    expect(onOpenChange).toHaveBeenCalledWith(false);

    act(() => {
      root.unmount();
    });
  });

  it("gọi onNewChat khi nhấn nút cuộc trò chuyện mới", async () => {
    api.fetchAiConversations.mockResolvedValue([]);
    const onNewChat = vi.fn();
    const onOpenChange = vi.fn();
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(
        <AssistantHistoryDrawer
          open={true}
          onOpenChange={onOpenChange}
          onSelectConversation={vi.fn()}
          onNewChat={onNewChat}
        />,
      );
    });

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 10));
    });

    const newChatBtn = Array.from(document.body.querySelectorAll("button")).find((b) =>
      b.textContent.includes("newChat"),
    );
    expect(newChatBtn).toBeDefined();

    act(() => {
      newChatBtn?.click();
    });

    expect(onNewChat).toHaveBeenCalled();
    expect(onOpenChange).toHaveBeenCalledWith(false);

    act(() => {
      root.unmount();
    });
  });
});
