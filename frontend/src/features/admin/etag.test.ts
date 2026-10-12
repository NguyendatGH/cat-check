import { afterEach, describe, expect, it, vi } from "vitest";
import { isApiError } from "@/shared/api/errors";
import { adminRequest } from "./api";
import { classifyAdminError } from "./adminAccess";

vi.mock("@/shared/api/acceptLanguage", () => ({ currentAcceptLanguage: () => "vi" }));

const ETAG = 'W/"1727740800000-REPEATED_OUT_OF_RANGE"';

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ETag / If-Match cho L39", () => {
  it("gửi If-Match nguyên văn và trả ETag mới từ header", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { code: "R", etag: "body" }, { ETag: 'W/"2-R"' }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await adminRequest<{ code: string }>("/admin/monitoring-rules/R", {
      method: "PATCH",
      json: { enabled: false, reason: "Tắt tạm thời để rà soát" },
      ifMatch: ETAG,
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/admin/monitoring-rules/R");
    expect(new Headers(init.headers).get("If-Match")).toBe(ETAG);
    expect(init.method).toBe("PATCH");
    expect(result.etag).toBe('W/"2-R"');
  });

  it("412 RESOURCE_MODIFIED ⇒ ApiError được phân loại 'stale' (trang hiện nút Tải lại)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse(412, { detail: "Đã bị sửa", errorCode: "RESOURCE_MODIFIED" })),
    );

    const error: unknown = await adminRequest("/admin/monitoring-rules/R", {
      method: "PATCH",
      json: {},
      ifMatch: ETAG,
    }).catch((caught: unknown) => caught);

    expect(isApiError(error)).toBe(true);
    expect(isApiError(error) ? [error.status, error.code] : null).toEqual([412, "RESOURCE_MODIFIED"]);
    expect(classifyAdminError(error)).toBe("stale");
  });

  it("không có ETag thì không gửi If-Match", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {}));
    vi.stubGlobal("fetch", fetchMock);
    await adminRequest("/admin/monitoring-rules", { ifMatch: null });
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(new Headers(init.headers).has("If-Match")).toBe(false);
  });

  it("mất mạng ⇒ TypeError được phân loại 'network'", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    const error: unknown = await adminRequest("/admin/monitoring-rules").catch((caught: unknown) => caught);
    expect(classifyAdminError(error)).toBe("network");
  });
});
