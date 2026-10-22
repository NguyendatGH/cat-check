import { describe, expect, it } from "vitest";
import type { PlaceApi } from "@/features/place";
import { toViewPlace } from "./placeView";

const BASE: PlaceApi = {
  id: "p1",
  name: "Phòng khám A",
  kind: "CLINIC",
  address: "1 Trần Phú",
  area: "Tam Kỳ",
  latitude: 15.57,
  longitude: 108.47,
  phone: null,
  specialties: [],
  badges: [],
  rating: 0,
  reviewCount: 0,
};

describe("toViewPlace source", () => {
  it.each([
    ["OSM", "osm"],
    ["GOONG", "goong"],
    ["CATCHECK", "catcheck"],
    [undefined, "catcheck"],
  ] as const)("maps API source %s to %s", (apiSource, expected) => {
    expect(toViewPlace({ ...BASE, source: apiSource }).source).toBe(expected);
  });
});
