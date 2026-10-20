#!/usr/bin/env python3
"""Resolves merge conflicts in the lists every new game adds one line to.

After `git merge origin/stag` on a pull request branch, call it with the conflicted files.
Each conflict in those lists is two sets of added lines that both belong (the games already
on stag and the ones the pull request adds), so both are kept: stag's lines first, then the
pull request's lines that stag doesn't already have.

Exits with 1, changing nothing, when a conflicted file is not one of the lists: those
conflicts need a person.
"""
import re
import sys

LISTS = {
    "CHANGELOG.md",
    "app/src/main/java/io/github/fershibli/megagames/core/Model.kt",
    "app/src/main/java/io/github/fershibli/megagames/games/GameContent.kt",
    "app/src/main/java/io/github/fershibli/megagames/ui/art/Thumbnails.kt",
    "app/src/test/java/io/github/fershibli/megagames/ScreenshotTest.kt",
}

# <<<<<<< ours (the pull request) / ||||||| base (diff3 only) / ======= / theirs (stag) >>>>>>>
CONFLICT = re.compile(
    r"^<<<<<<< [^\n]*\n(.*?)(?:^\|\|\|\|\|\|\| [^\n]*\n.*?)?^=======\n(.*?)^>>>>>>> [^\n]*\n",
    re.S | re.M,
)


def keep_both(match: re.Match) -> str:
    ours, theirs = match.group(1), match.group(2)
    known = set(theirs.splitlines())
    added = [line for line in ours.splitlines(keepends=True) if line.rstrip("\n") not in known]
    return theirs + "".join(added)


def main(paths: list[str]) -> int:
    others = [p for p in paths if p not in LISTS]
    if others:
        print("Conflicts outside the game lists: " + ", ".join(others))
        return 1
    resolved = {}
    for path in paths:
        with open(path, encoding="utf-8") as f:
            text = CONFLICT.sub(keep_both, f.read())
        if re.search(r"^(<<<<<<< |=======$|>>>>>>> )", text, re.M):
            print(f"Could not resolve every conflict in {path}")
            return 1
        resolved[path] = text
    for path, text in resolved.items():
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
        print(f"Kept both sides in {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
