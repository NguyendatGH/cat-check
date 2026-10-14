import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AssistantPage } from "./AssistantPage";

const chat = vi.hoisted(() => ({ chatWithAssistant: vi.fn() }));

vi.mock("@/features/assistant", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/assistant")>()),
  chatWithAssistant: chat.chatWithAssistant,
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
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: "vi" } }),
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
    expect(chat.chatWithAssistant).toHaveBeenNthCalledWith(1, "Mèo của tôi uống ít nước?", undefined, "cat-a");

    type("Còn thức ăn?");
    await send();
    expect(chat.chatWithAssistant).toHaveBeenNthCalledWith(2, "Còn thức ăn?", "conv-1", "cat-a");

    act(() => {
      catChip("Mun").click();
    });
    expect(catChip("Mun").getAttribute("aria-checked")).toBe("true");
    type("Bé này thì sao?");
    await send();
    expect(chat.chatWithAssistant).toHaveBeenNthCalledWith(3, "Bé này thì sao?", undefined, "cat-b");

    act(() => {
      root.unmount();
    });
    container.remove();
  });
});
