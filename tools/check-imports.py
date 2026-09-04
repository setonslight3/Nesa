#!/usr/bin/env python3
"""
A poor substitute for a compiler, for the half of this project that cannot be
compiled where it is written.

The Android modules need the Android SDK, which the authoring environment cannot
reach, so a whole class of trivial error — a symbol used without its import —
survives until someone else builds the project. That has cost a full
build-and-report round trip at least once, when imports were stripped from
AlarmRingerService while slimming it down and nothing noticed.

This checks the three cases that actually happen in practice:

  1. A project type (com.nesa.*) used in a file that neither imports it nor
     declares it nor shares its package.
  2. A well-known coroutines/flow function used without its import.
  3. A nullable property tested for null and then used, without being bound to
     a local first. Kotlin refuses to smart-cast a property declared in another
     module, and this codebase is all modules — so the pattern compiles fine in
     the module that declares the type and fails in the one that reads it. It
     cost a build round trip on FitnessScreen.kt.

It cannot type-check, resolve overloads, or understand the Android SDK, and it
will never replace a build. It catches the mechanical mistakes, cheaply, before
they cost a round trip.

Usage:  python3 tools/check-imports.py       # exits non-zero on a finding
"""

import glob
import os
import re
import sys

# Functions that are imported rather than resolved automatically, and that this
# codebase uses often enough to be worth checking by name.
KNOWN_FUNCTION_IMPORTS = {
    "delay": "kotlinx.coroutines.delay",
    "launch": "kotlinx.coroutines.launch",
    "withContext": "kotlinx.coroutines.withContext",
    "combine": "kotlinx.coroutines.flow.combine",
    "stateIn": "kotlinx.coroutines.flow.stateIn",
    "flatMapLatest": "kotlinx.coroutines.flow.flatMapLatest",
    "asStateFlow": "kotlinx.coroutines.flow.asStateFlow",
    "update": "kotlinx.coroutines.flow.update",
    "getSystemService": "androidx.core.content.getSystemService",
    "stringResource": "androidx.compose.ui.res.stringResource",
    "hiltViewModel": "androidx.hilt.navigation.compose.hiltViewModel",
    "collectAsStateWithLifecycle": "androidx.lifecycle.compose.collectAsStateWithLifecycle",
}

DECLARATION = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public |internal |private |abstract |open |sealed |data |value |enum |annotation |inline |fun )*"
    r"(?:class|interface|object)\s+(\w+)",
    re.M,
)


def project_symbols():
    """package -> {top-level type names declared in it}"""
    index = {}
    for path in glob.glob("*/src/*/kotlin/**/*.kt", recursive=True):
        source = open(path, encoding="utf-8").read()
        package = re.search(r"^package\s+([\w.]+)", source, re.M)
        if not package:
            continue
        index.setdefault(package.group(1), set()).update(DECLARATION.findall(source))
    return index


# A property access that a null test cannot smart-cast: at least one dot, and a
# receiver that is not a bare local. `summary.daysSinceLast` matches; `days` does
# not, because a local val smart-casts perfectly well.
SMART_CAST_SUBJECT = re.compile(r"\b([a-z]\w*(?:\.[a-z]\w*)+)\b")


def nullable_property_modules():
    """
    Nullable `val` properties, and which Gradle module declares each.

    The module is what makes this precise rather than noisy. Kotlin smart-casts
    a property perfectly well inside the module that declares it, so flagging
    every null test would cry wolf on code that compiles — and a checker that
    cries wolf gets ignored, which is worse than not having one.
    """
    owner = {}
    for path in glob.glob("*/src/*/kotlin/**/*.kt", recursive=True):
        module = path.split(os.sep, 1)[0]
        for name in re.findall(r"^\s*(?:override |open |private |internal )*va[lr]\s+(\w+)\s*:\s*[\w.<>, ]+\?",
                               open(path, encoding="utf-8").read(), re.M):
            owner.setdefault(name, set()).add(module)
    return owner


def smart_cast_findings(path, body, nullable_owner):
    """
    Nullable properties tested and then re-read without a local binding.

    Two shapes, both of which Kotlin rejects across a module boundary:

        when (a.b) { null -> ...  else -> f(a.b) }
        if (a.b != null) { f(a.b) }

    The advice is the same either way and is never wrong even when the smart
    cast would have been allowed: bind it to a local val and use that. So this
    stays deliberately blunt rather than trying to work out which module
    declared the property, which it has no way to know.
    """
    findings = []
    nullable_owner = nullable_property_modules()

    module = path.split(os.sep, 1)[0]

    def is_cross_module(subject):
        """True only when some *other* module declares this nullable property."""
        declaring = nullable_owner.get(subject.rsplit(".", 1)[-1], set())
        return bool(declaring - {module})

    for match in re.finditer(r"\bwhen\s*\(\s*([^()\n]+?)\s*\)\s*\{", body):
        subject = match.group(1).strip()
        if not SMART_CAST_SUBJECT.fullmatch(subject) or not is_cross_module(subject):
            continue
        block = balanced_block(body, match.end() - 1)
        if "null" in block and re.search(rf"(?<![\w.]){re.escape(subject)}\b", block):
            findings.append(
                f"{path}: `when ({subject})` has a null branch and reads "
                f"`{subject}` again — bind it to a local val first, or Kotlin "
                f"cannot smart-cast it across a module boundary"
            )

    for match in re.finditer(r"\bif\s*\(\s*([^()\n]+?)\s*!=\s*null\s*\)\s*\{", body):
        subject = match.group(1).strip()
        if not SMART_CAST_SUBJECT.fullmatch(subject) or not is_cross_module(subject):
            continue
        block = balanced_block(body, match.end() - 1)
        if re.search(rf"(?<![\w.]){re.escape(subject)}\b(?!\s*[!=]=)", block):
            findings.append(
                f"{path}: `if ({subject} != null)` then reads `{subject}` — "
                f"bind it to a local val first, or Kotlin cannot smart-cast it "
                f"across a module boundary"
            )

    return findings


def balanced_block(text, open_brace_index):
    """The text between a `{` and its matching `}`. Empty if it never closes."""
    depth = 0
    for index in range(open_brace_index, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace_index + 1:index]
    return ""


def strip_noise(source):
    """Remove comments and string literals so their contents are not read as code."""
    source = re.sub(r"/\*.*?\*/", " ", source, flags=re.S)
    source = re.sub(r"//[^\n]*", " ", source)
    source = re.sub(r'"""(?:.|\n)*?"""', '""', source)
    source = re.sub(r'"(?:\\.|[^"\\])*"', '""', source)
    return source


def main():
    index = project_symbols()
    owner = {}
    for package, names in index.items():
        for name in names:
            owner.setdefault(name, set()).add(package)

    nullable_owner = nullable_property_modules()

    findings = []
    for path in sorted(glob.glob("*/src/*/kotlin/**/*.kt", recursive=True)):
        raw = open(path, encoding="utf-8").read()
        package = re.search(r"^package\s+([\w.]+)", raw, re.M)
        if not package:
            continue
        package = package.group(1)

        imports = set(re.findall(r"^import\s+([\w.]+)", raw, re.M))
        imported_names = {i.rsplit(".", 1)[-1] for i in imports}
        wildcards = {i[:-2] for i in imports if i.endswith(".*")}
        local = set(DECLARATION.findall(raw)) | set(
            re.findall(r"^\s*(?:private |internal )?(?:enum class|data class|value class)\s+(\w+)", raw, re.M)
        )
        body = strip_noise(raw)

        for name, packages in owner.items():
            if name in imported_names or name in local:
                continue
            if package in packages or packages & wildcards:
                continue
            # Used as a type, a constructor, or a qualified reference.
            if re.search(rf"(?<![\w.]){re.escape(name)}\s*[(.<]|:\s*{re.escape(name)}\b", body):
                findings.append(
                    f"{path}: uses project symbol '{name}' "
                    f"(declared in {', '.join(sorted(packages))}) without importing it"
                )

        for name, full in KNOWN_FUNCTION_IMPORTS.items():
            if name in imported_names or name in local:
                continue
            if full.rsplit(".", 1)[0] in wildcards:
                continue
            if re.search(rf"(?<![\w.]){re.escape(name)}\s*\(", body):
                findings.append(f"{path}: calls '{name}' without importing {full}")

        findings.extend(smart_cast_findings(path, body, nullable_owner))

    for finding in sorted(set(findings)):
        print("  " + finding)
    print(f"\n{len(set(findings))} probable problem(s).")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
