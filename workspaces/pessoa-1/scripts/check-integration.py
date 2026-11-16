#!/usr/bin/env python3
"""Inventory expected P2/P3 source areas; this is not a readiness test."""
import argparse
from pathlib import Path

MODULES = {"pessoa-2": ("partners", "fleet"),
           "pessoa-3": ("deliveries", "notifications", "documents")}
SOURCE_SUFFIXES = {".java", ".kt"}
FRONTEND_SUFFIXES = {".vue", ".ts", ".js", ".tsx", ".jsx"}


def has_files(path, suffixes):
    return path.is_dir() and any(p.is_file() and p.suffix.lower() in suffixes
                                 for p in path.rglob("*"))


def inventory(root):
    results = []
    for workspace, modules in MODULES.items():
        base = root / "workspaces" / workspace
        java = base / "backend" / "src" / "main" / "java"
        migrations = base / "backend" / "src" / "main" / "resources" / "db" / "migration"
        for module in modules:
            candidates = (p for p in java.rglob(module) if p.is_dir()) if java.is_dir() else ()
            results.append((f"{workspace}: backend source {module}",
                            any(has_files(p, SOURCE_SUFFIXES) for p in candidates)))
            results.append((f"{workspace}: migrations {module}",
                            has_files(migrations / module, {".sql"})))
        results.append((f"{workspace}: frontend source", has_files(base / "frontend" / "src", FRONTEND_SUFFIXES)))
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3],
                        help="repository root (default: inferred from script location)")
    args = parser.parse_args()
    results = inventory(args.root)
    for name, present in results:
        print(f"{'PRESENT' if present else 'MISSING'} {name}")
    print("Inventory only: contracts, builds and E2E with real adapters remain required.")
    return 0 if all(present for _, present in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
