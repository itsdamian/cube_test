#!/usr/bin/env python3
"""Check relative links (and #anchors) in the repository's Markdown files.

Usage:  python3 scripts/check_md_links.py [--allow-missing-images]

- Every relative link target must exist (files or directories).
- A `#fragment` must match a heading in the target file, using GitHub's anchor rules
  (lower-case, punctuation removed, spaces -> "-").
- External links (http/https/mailto) are not checked.
- --allow-missing-images: report missing image files as pending instead of failing
  (used while README screenshots are still to be added).
Exit code 0 = all links valid.
"""
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKIP_DIRS = {"node_modules", "target", "dist", ".git", ".claude"}
LINK = re.compile(r"(!?)\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
FENCE = re.compile(r"^(```|~~~)")


def github_anchor(heading: str) -> str:
    text = heading.strip().lower()
    text = re.sub(r"`", "", text)
    kept = []
    for ch in text:
        cat = unicodedata.category(ch)
        if ch in " -_" or cat[0] in ("L", "N", "M"):
            kept.append(ch)
    return "".join(kept).replace(" ", "-")


def anchors(path: Path) -> set[str]:
    result, counts, in_code = set(), {}, False
    for line in path.read_text(encoding="utf-8").splitlines():
        if FENCE.match(line.strip()):
            in_code = not in_code
            continue
        if in_code:
            continue
        m = re.match(r"^#{1,6}\s+(.*)$", line)
        if m:
            base = github_anchor(m.group(1))
            n = counts.get(base, 0)
            counts[base] = n + 1
            result.add(base if n == 0 else f"{base}-{n}")
    return result


def links(path: Path):
    in_code = False
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if FENCE.match(line.strip()):
            in_code = not in_code
            continue
        if in_code:
            continue
        for m in LINK.finditer(line):
            yield number, m.group(1) == "!", m.group(2)


def main() -> int:
    allow_missing_images = "--allow-missing-images" in sys.argv
    files = [p for p in ROOT.rglob("*.md") if not SKIP_DIRS & set(p.relative_to(ROOT).parts)]
    errors, pending, checked = [], [], 0
    for md in sorted(files):
        for number, is_image, target in links(md):
            if re.match(r"^(https?:|mailto:)", target):
                continue
            checked += 1
            where = f"{md.relative_to(ROOT)}:{number}"
            file_part, _, fragment = target.partition("#")
            dest = md if not file_part else (md.parent / file_part).resolve()
            if not dest.exists():
                (pending if is_image and allow_missing_images else errors).append(f"{where}: missing {target}")
                continue
            if fragment and dest.is_file() and dest.suffix == ".md" and fragment not in anchors(dest):
                errors.append(f"{where}: no heading for #{fragment} in {dest.relative_to(ROOT)}")
    print(f"checked {checked} relative links in {len(files)} Markdown files")
    for p in pending:
        print(f"PENDING {p}")
    for e in errors:
        print(f"ERROR   {e}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
