import { describe, expect, it } from "vitest";
import { weekdaysOf, weeklyRule } from "./reminderUi";

describe("weekday rule helpers", () => {
  it("builds a weekly rule in Monday-first order", () => {
    expect(weeklyRule(["SU", "WE"])).toBe("FREQ=WEEKLY;BYDAY=WE,SU");
    expect(weeklyRule([])).toBe("");
  });

  it("reads days back from a simple weekly rule", () => {
    expect(weekdaysOf("FREQ=WEEKLY;BYDAY=WE,SU")).toEqual(["WE", "SU"]);
  });

  it("returns null for rules the day picker cannot show", () => {
    expect(weekdaysOf("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO")).toBeNull();
    expect(weekdaysOf("FREQ=DAILY")).toBeNull();
    expect(weekdaysOf("FREQ=WEEKLY;BYDAY=XX")).toBeNull();
    expect(weekdaysOf("")).toBeNull();
  });
});
