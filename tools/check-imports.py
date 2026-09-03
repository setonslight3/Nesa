#!/usr/bin/env python3
"""
A poor substitute for a compiler, for the half of this project that cannot be
compiled where it is written.

The Android modules need the Android SDK, which the authoring environment cannot
reach, so a whole class of trivial error — a symbol used without its import —
survives until someone else builds the project. That has cost a full
build-and-report round trip at least once, when imports were stripped from
AlarmRingerService while slimming it down and nothing noticed.

This checks the two cases that actually happen in practice:

  1. A project type (com.nesa.*) used in a file that neither imports it nor
     declares it nor shares its package.
  2. A well-known coroutines/flow function used without its import.

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

    for finding in sorted(set(findings)):
        print("  " + finding)
    print(f"\n{len(set(findings))} probable missing import(s).")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
