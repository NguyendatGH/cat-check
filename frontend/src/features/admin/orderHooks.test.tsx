import { act } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useChangeAdminOrderStatus } from "./hooks";

afterEach(() => {
  vi.unstubAllGlobals();
  document.body.innerHTML = "";
});

describe("useChangeAdminOrderStatus", () => {
  it("refreshes the order list even when the change is rejected (409: the order moved under us)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          new Response(JSON.stringify({ errorCode: "ORDER_TRANSITION_INVALID" }), {
            status: 409,
            headers: { "Content-Type": "application/problem+json" },
          }),
        ),
      ),
    );
    const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const box: { mutation?: ReturnType<typeof useChangeAdminOrderStatus> } = {};
    function Probe() {
      box.mutation = useChangeAdminOrderStatus();
      return null;
    }
    const root = createRoot(document.body.appendChild(document.createElement("div")));
    (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    act(() => {
      root.render(
        <QueryClientProvider client={client}>
          <Probe />
        </QueryClientProvider>,
      );
    });

    await act(async () => {
      await box.mutation?.mutateAsync({ id: "o1", status: "PACKING" }).catch(() => undefined);
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["admin", "shop-orders"] });
    act(() => {
      root.unmount();
    });
  });
});
