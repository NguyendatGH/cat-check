import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PlaceApi } from "@/features/place";
import { CatCareMapPage } from "./CatCareMapPage";

const state = vi.hoisted(() => ({ nearby: [] as PlaceApi[], radiusKm: [] as (number | undefined)[] }));
const mounted: Root[] = [];

vi.mock("@/features/place", () => ({
  listPlaces: () => Promise.resolve([]),
  listNearbyPlaces: (params: { radiusKm?: number }) => {
    state.radiusKm.push(params.radiusKm);
    return Promise.resolve(state.nearby);
  },
}));
vi.mock("@/shared/lib/hooks/useBreakpoint", () => ({ useBreakpoint: () => true }));
vi.mock("./PlaceMap", () => ({ PlaceMap: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));
vi.mock("./location", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./location")>()),
  acquireLocation: (_geo: Geolocation, onSuccess: (location: unknown) => void) => {
    onSuccess({ latitude: 15.57, longitude: 108.47, accuracy: 20 });
    return () => undefined;
  },
}));
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));

function externalPlace(source: "OSM" | "GOONG"): PlaceApi {
  return {
    id: `ext-${source}`,
    name: `Phòng khám ${source}`,
    kind: "CLINIC",
    address: "1 Trần Phú",
    area: "Tam Kỳ",
    latitude: 15.57,
    longitude: 108.47,
    phone: "0901234567",
    specialties: [],
    badges: [],
    rating: 0,
    reviewCount: 0,
    source,
  };
}

async function render(place: PlaceApi): Promise<HTMLDivElement> {
  state.nearby = [place];
  Object.defineProperty(navigator, "geolocation", { value: {}, configurable: true });
  Element.prototype.scrollIntoView = vi.fn(); // jsdom lacks it; the active card scrolls itself into view
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const root = createRoot(container);
  mounted.push(root);
  act(() => {
    root.render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[`/map?place=${place.id}`]}>
          <CatCareMapPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );
  });
  await vi.waitFor(async () => {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(container.textContent).toContain(place.name);
  });
  return container;
}

afterEach(() => {
  act(() => {
    for (const root of mounted.splice(0)) root.unmount();
  });
  vi.clearAllMocks();
});

describe("CatCareMapPage external places", () => {
  it.each([
    ["OSM", "osm"],
    ["GOONG", "goong"],
  ] as const)("%s place: provider chip and external note, no detail or booking link", async (source, provider) => {
    const place = externalPlace(source);
    const container = await render(place);
    const text = container.textContent;
    expect(text).toContain(`source.${provider}`);
    expect(text).toContain(`source.externalNote{"provider":"source.${provider}"}`);
    expect(container.querySelector(`a[href^="/map/clinics/"]`)).toBeNull();
    expect(text).not.toContain("actions.detail");
    expect(text).not.toContain("actions.book");
    expect(container.querySelector('a[href^="tel:"]')).not.toBeNull();
    expect(container.querySelector('a[href^="https://www.google.com/maps/dir/"]')).not.toBeNull();
  });

  it("asks for nearby places within the backend's 25 km cap", async () => {
    state.radiusKm = [];
    await render(externalPlace("GOONG"));
    expect(state.radiusKm.length).toBeGreaterThan(0);
    for (const radius of state.radiusKm) expect(radius).toBeLessThanOrEqual(25);
  });
});
