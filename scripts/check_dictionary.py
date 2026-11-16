#!/usr/bin/env python3
"""Checks a word dictionary of the word games (app/src/main/assets/crossword/<code>.txt).

Usage: scripts/check_dictionary.py <code> [<code>...]   (codes: en pt es fr it de ru ja zh ar)

Each line is ANSWER|clue|level (level 1 easy, 2 medium, 3 hard); lines starting with # are
comments. The answer is normalized the way the app does it (see Crossword.normalize) and must be
2 to 12 squares long (2 to 7 in Japanese and Chinese). Answers must not repeat once normalized,
and a clue must not contain its own answer as a word. Prints the counts per level and every problem found;
exits with 1 when there is a problem.
"""
import sys, unicodedata, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/crossword"
SMALL, FULL = "ァィゥェォッャュョヮヵヶ", "アイウエオツヤユヨワカケ"


def normalize(word, code):
    if code == "ja":
        out = []
        for ch in word:
            k = chr(ord(ch) + 0x60) if "ぁ" <= ch <= "ゖ" else ch
            if k in SMALL:
                out.append(FULL[SMALL.index(k)])
            elif "ア" <= k <= "ヺ" or k == "ー":
                out.append(k)
        return out
    if code == "ar":
        out = []
        for ch in word:
            if ch in "أإآٱ":
                out.append("ا")
            elif "ء" <= ch <= "ي" and ch != "ـ":
                out.append(ch)
        return out
    if code == "zh":
        return [c for c in word if not c.isspace()]
    up = word.upper().replace("Œ", "OE").replace("Æ", "AE")
    if code == "de":
        for a, b in (("Ä", "AE"), ("Ö", "OE"), ("Ü", "UE"), ("ẞ", "SS"), ("ß", "SS")):
            up = up.replace(a, b)
    out = []
    for ch in up:
        if ch == "Ñ" and code == "es":
            out.append("Ñ"); continue
        if code == "ru":
            if ch == "Ё": out.append("Е")
            elif "А" <= ch <= "Я": out.append(ch)
            continue
        base = "".join(c for c in unicodedata.normalize("NFD", ch) if not unicodedata.combining(c))
        out.extend(c for c in base if "A" <= c <= "Z")
    return out


def check(code):
    path = ROOT / f"{code}.txt"
    problems, seen, levels = [], {}, {1: 0, 2: 0, 3: 0}
    syllabic = code in ("ja", "zh")
    longest = 7 if syllabic else 12
    for n, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        if len(parts) != 3:
            problems.append(f"{n}: needs ANSWER|clue|level: {line}"); continue
        answer, clue, level = (p.strip() for p in parts)
        if level not in ("1", "2", "3"):
            problems.append(f"{n}: level must be 1, 2 or 3: {line}"); continue
        letters = normalize(answer, code)
        if not 2 <= len(letters) <= longest:
            problems.append(f"{n}: answer has {len(letters)} squares (2..{longest}): {answer}")
        key = "".join(letters)
        if key in seen:
            problems.append(f"{n}: repeats line {seen[key]}: {answer}")
        seen.setdefault(key, n)
        if not clue or len(clue) > 90:
            problems.append(f"{n}: clue empty or longer than 90 characters: {clue}")
        # A clue may not give its answer away: the same word in it (any part of it in Japanese and Chinese).
        words = [clue] if syllabic else clue.replace("'", " ").replace("’", " ").replace("-", " ").split()
        if any(key in "".join(normalize(w, code)) if syllabic else key == "".join(normalize(w, code)) for w in words) and len(key) >= 2:
            problems.append(f"{n}: clue contains the answer: {line}")
        if "1000" in clue.replace(".", "").replace(" ", ""):
            problems.append(f"{n}: clue mentions 1000: {line}")
        levels[int(level)] += 1
    total = sum(levels.values())
    print(f"{code}: {total} words (easy {levels[1]}, medium {levels[2]}, hard {levels[3]}), {len(problems)} problems")
    for p in problems[:60]:
        print("  " + p)
    return not problems


if __name__ == "__main__":
    ok = all([check(c) for c in sys.argv[1:]])
    sys.exit(0 if ok else 1)
