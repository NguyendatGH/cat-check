import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { RichText } from "./components";

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

afterEach(() => {
  document.body.innerHTML = "";
});

describe("RichText component", () => {
  it("loại bỏ ký tự ### và render heading đậm", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(<RichText text="### Tóm tắt hành động ngay hôm nay:" />);
    });

    expect(container.textContent).toContain("Tóm tắt hành động ngay hôm nay:");
    expect(container.textContent).not.toContain("###");

    act(() => {
      root.unmount();
    });
  });

  it("chuyển đổi --- thành thẻ hr thay vì text thô", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(<RichText text={"Đoạn 1\n---\nĐoạn 2"} />);
    });

    expect(container.querySelector("hr")).not.toBeNull();
    expect(container.textContent).not.toContain("---");

    act(() => {
      root.unmount();
    });
  });

  it("chuyển đổi markdown table thành list text sạch sẽ", () => {
    const tableText = `
| Tình trạng | Xử lý |
|---|---|
| Mèo mệt | Theo dõi 24h |
| Tiểu buốt | Đi khám ngay |
    `.trim();

    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(<RichText text={tableText} />);
    });

    expect(container.textContent).not.toContain("|---|---|");
    expect(container.textContent).toContain("Tình trạng — Xử lý");
    expect(container.textContent).toContain("Mèo mệt — Theo dõi 24h");
    expect(container.textContent).toContain("Tiểu buốt — Đi khám ngay");

    act(() => {
      root.unmount();
    });
  });
});
