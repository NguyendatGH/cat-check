import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AssistantPage } from "./AssistantPage";

const chat = vi.hoisted(() => ({
  chatWithAssistant: vi.fn(),
  fetchAiConversations: vi.fn(),
  fetchAiConversationMessages: vi.fn(),
}));

vi.mock("@/features/assistant", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/assistant")>()),
  chatWithAssistant: chat.chatWithAssistant,
  fetchAiConversations: chat.fetchAiConversations,
  fetchAiConversationMessages: chat.fetchAiConversationMessages,
}));
vi.mock("@/features/cat", () => ({
  useCatList: () => ({
    data: {
      items: [
        { id: "cat-a", name: "Mướp", isPrimary: true, avatarUrl: null },
        { id: "cat-b", name: "Mun", isPrimary: false, avatarUrl: null },
      ],
    },
  }),
}));
const tMock = (key: string) => key;
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: tMock, i18n: { language: "vi" } }),
}));

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  chat.chatWithAssistant.mockReset();
  chat.chatWithAssistant.mockResolvedValue({
    conversationId: "conv-1",
    messageId: "m1",
    answer: "Trả lời",
    provider: "retrieval-fallback",
    citations: [],
    createdAt: "2026-10-10T00:00:00Z",
  });
  chat.fetchAiConversations.mockReset();
  chat.fetchAiConversations.mockResolvedValue([]);
  chat.fetchAiConversationMessages.mockReset();
  chat.fetchAiConversationMessages.mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("AssistantPage — đổi bé trong lúc trò chuyện", () => {
  it("câu hỏi sau khi đổi bé bắt đầu hội thoại mới (không gửi conversationId của bé cũ)", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);
    await act(async () => {
      root.render(<AssistantPage />);
      await Promise.resolve();
    });

    const textarea = container.querySelector("textarea") as HTMLTextAreaElement;
    const catChip = (name: string) =>
      Array.from(container.querySelectorAll<HTMLButtonElement>('[role="radio"]')).find((chip) =>
        chip.textContent.includes(name),
      ) as HTMLButtonElement;
    expect(catChip("Mướp").getAttribute("aria-checked")).toBe("true");

    const type = (value: string) => {
      act(() => {
        Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, "value")?.set?.call(textarea, value);
        textarea.dispatchEvent(new Event("input", { bubbles: true }));
      });
    };
    const send = async () => {
      const button = container.querySelector('button[aria-label="send"]') as HTMLButtonElement;
      await act(async () => {
        button.click();
        await Promise.resolve();
      });
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 5));
      });
    };

    type("Mèo của tôi uống ít nước?");
    await send();
    expect(chat.chatWithAssistant).toHaveBeenNthCalledWith(
      1,
      "Mèo của tôi uống ít nước?",
      undefined,
      "cat-a",
      expect.any(String),
    );

    type("Còn thức ăn?");
    await send();
    expect(chat.chatWithAssistant).toHaveBeenNthCalledWith(2, "Còn thức ăn?", "conv-1", "cat-a", expect.any(String));

    act(() => {
      catChip("Mun").click();
    });
    expect(catChip("Mun").getAttribute("aria-checked")).toBe("true");
    type("Bé này thì sao?");
    await send();
    expect(chat.chatWithAssistant).toHaveBeenNthCalledWith(
      3,
      "Bé này thì sao?",
      undefined,
      "cat-b",
      expect.any(String),
    );

    act(() => {
      root.unmount();
    });
    container.remove();
  });

  it("gửi lại một câu hỏi lỗi dùng lại đúng request key để server trả lại câu trả lời cũ", async () => {
    chat.chatWithAssistant.mockRejectedValueOnce(new Error("network"));
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);
    await act(async () => {
      root.render(<AssistantPage />);
      await Promise.resolve();
    });
    const textarea = container.querySelector("textarea") as HTMLTextAreaElement;
    act(() => {
      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, "value")?.set?.call(textarea, "Uống nước?");
      textarea.dispatchEvent(new Event("input", { bubbles: true }));
    });
    const settle = async (click: () => void) => {
      await act(async () => {
        click();
        await Promise.resolve();
      });
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 5));
      });
    };

    await settle(() => {
      (container.querySelector('button[aria-label="send"]') as HTMLButtonElement).click();
    });
    const retryButton = Array.from(container.querySelectorAll("button")).find((button) =>
      button.textContent.includes("retry"),
    ) as HTMLButtonElement;
    await settle(() => {
      retryButton.click();
    });

    expect(chat.chatWithAssistant).toHaveBeenCalledTimes(2);
    const [firstKey, retryKey] = chat.chatWithAssistant.mock.calls.map((call: unknown[]) => call[3]);
    expect(retryKey).toBe(firstKey);

    act(() => {
      root.unmount();
    });
    container.remove();
  });

  it("khôi phục tin nhắn khi chọn một cuộc trò chuyện từ lịch sử", async () => {
    chat.fetchAiConversations.mockResolvedValue([
      {
        id: "conv-old",
        userId: "u1",
        createdAt: "2026-10-10T00:00:00Z",
        updatedAt: "2026-10-10T00:05:00Z",
        preview: "Mèo bị nôn",
      },
    ]);
    chat.fetchAiConversationMessages.mockResolvedValue([
      {
        id: "msg-1",
        conversationId: "conv-old",
        role: "USER",
        content: "Mèo bị nôn làm sao?",
        provider: "",
        createdAt: "2026-10-10T00:00:00Z",
      },
      {
        id: "msg-2",
        conversationId: "conv-old",
        role: "ASSISTANT",
        content: "Hãy quan sát thêm",
        provider: "retrieval-fallback",
        createdAt: "2026-10-10T00:00:05Z",
      },
    ]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);
    await act(async () => {
      root.render(<AssistantPage />);
      await Promise.resolve();
    });

    const historyBtn = container.querySelector('button[aria-label="historyButton"]') as HTMLButtonElement;
    expect(historyBtn).not.toBeNull();

    await act(async () => {
      historyBtn.click();
      await Promise.resolve();
    });

    expect(chat.fetchAiConversations).toHaveBeenCalled();

    // Settle drawer content
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 10));
    });

    const convButton = Array.from(document.body.querySelectorAll("button")).find((btn) =>
      btn.textContent.includes("Mèo bị nôn"),
    );
    expect(convButton).toBeDefined();

    await act(async () => {
      convButton?.click();
      await Promise.resolve();
    });

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 60));
    });

    expect(chat.fetchAiConversationMessages).toHaveBeenCalledWith("conv-old");
    expect(container.textContent).toContain("Mèo bị nôn làm sao?");
    expect(container.textContent).toContain("Hãy quan sát thêm");

    act(() => {
      root.unmount();
    });
    container.remove();
  });
});
