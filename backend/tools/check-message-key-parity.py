#!/usr/bin/env python3
"""Report English/Vietnamese key parity for every messages/<bundle>_{en,vi}.properties pair."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

# Keys that exist in only one locale on purpose. Each entry must say why.
INTENTIONAL_ONE_LOCALE: dict[str, dict[str, set[str]]] = {
    # Approved emergency copy exists only in Vietnamese; AiChatService pins EMERGENCY_LOCALE=vi.
    # Machine-translating vet-reviewed safety copy is not allowed (see ai_en.properties header).
    "ai": {"vi": {"ai.emergency.body", "ai.emergency.lead", "ai.emergency.viewDetail"}},
}


def keys(path: Path) -> set[str]:
    result: set[str] = set()
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        if "=" not in line:
            raise ValueError(f"{path}:{number}: expected KEY=VALUE")
        key = line.split("=", maxsplit=1)[0].strip()
        if not key:
            raise ValueError(f"{path}:{number}: empty key")
        result.add(key)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "messages_dir",
        nargs="?",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "src/main/resources/messages",
    )
    args = parser.parse_args()

    mismatches = 0
    for english in sorted(args.messages_dir.glob("*_en.properties")):
        bundle = english.name.removesuffix("_en.properties")
        vietnamese = args.messages_dir / f"{bundle}_vi.properties"
        if not vietnamese.exists():
            print(f"{bundle}: missing {vietnamese.name}")
            mismatches += 1
            continue
        english_keys = keys(english)
        vietnamese_keys = keys(vietnamese)
        allowed = INTENTIONAL_ONE_LOCALE.get(bundle, {})
        english_only = sorted(english_keys - vietnamese_keys - allowed.get("en", set()))
        vietnamese_only = sorted(vietnamese_keys - english_keys - allowed.get("vi", set()))
        if english_only or vietnamese_only:
            mismatches += 1
            print(f"{bundle}: MISMATCH")
            for key in english_only:
                print(f"  en-only: {key}")
            for key in vietnamese_only:
                print(f"  vi-only: {key}")
        else:
            print(f"{bundle}: OK ({len(english_keys)} keys)")

    print(f"Checked {len(list(args.messages_dir.glob('*_en.properties')))} bundles; {mismatches} mismatch(es).")
    return 1 if mismatches else 0


if __name__ == "__main__":
    sys.exit(main())
