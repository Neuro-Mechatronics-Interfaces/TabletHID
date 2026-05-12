#!/usr/bin/env python3
"""
Backfill orientationPreference in existing gamepad configs in the community database.

Inference rules (applied only to rows that lack the field):
  - device_screen_width_px > device_screen_height_px  →  LANDSCAPE
  - device_screen_height_px >= device_screen_width_px →  PORTRAIT
  - no dimension data available                        →  LANDSCAPE (gamepad default)

Run from the repo root:
    python scripts/backfill_orientation.py [--db path/to/tablethid.db] [--dry-run]
"""

import argparse
import json
import sqlite3
import sys
from pathlib import Path

DEFAULT_DB = Path(__file__).resolve().parent.parent / "web" / "data" / "tablethid.db"


def infer_orientation(row: dict) -> str:
    w = row["device_screen_width_px"]
    h = row["device_screen_height_px"]
    if w and h and w != h:
        return "LANDSCAPE" if w > h else "PORTRAIT"
    return "LANDSCAPE"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--db", default=str(DEFAULT_DB), help="Path to SQLite database")
    parser.add_argument("--dry-run", action="store_true", help="Print changes without writing them")
    args = parser.parse_args()

    db_path = Path(args.db)
    if not db_path.exists():
        print(f"Database not found: {db_path}", file=sys.stderr)
        sys.exit(1)

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    rows = cur.execute(
        "SELECT id, config_json, device_screen_width_px, device_screen_height_px "
        "FROM configs WHERE mode = 'gamepad'"
    ).fetchall()

    updated = skipped = 0

    for row in rows:
        config = json.loads(row["config_json"])
        if "orientationPreference" in config:
            skipped += 1
            continue

        orientation = infer_orientation(dict(row))
        config["orientationPreference"] = orientation

        if args.dry_run:
            w = row["device_screen_width_px"]
            h = row["device_screen_height_px"]
            dims = f"{w}x{h}" if w and h else "no dims"
            print(f"  {row['id']}  {dims}  ->  {orientation}")
        else:
            cur.execute(
                "UPDATE configs SET config_json = ? WHERE id = ?",
                (json.dumps(config, separators=(",", ":")), row["id"]),
            )
        updated += 1

    if args.dry_run:
        print(f"\nDry run: would update {updated} configs, skip {skipped} (already set)")
        conn.close()
        return

    conn.commit()
    conn.close()
    print(f"Updated {updated} gamepad config(s), skipped {skipped} (already had orientationPreference)")


if __name__ == "__main__":
    main()
