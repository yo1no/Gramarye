#!/usr/bin/env python3
"""P9-S4 WC1 compiler-warning attribution admission.

This program never launches Gradle.  It admits either the direct main/test
compilation producer log (``compile``) or a later unit log (``unit``) that may
reuse the producer's outputs.  All evidence is rooted in one project checkout
and written once to a caller-owned result path.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import shlex
import stat
import subprocess
import sys
import tempfile
from collections import Counter
from pathlib import Path
from typing import Any, Callable, Iterable


SCHEMA = "gramarye.p9.s4.warning-attribution/v1"
CAMPAIGN = "P9-S4-WC1-WARNING-ATTRIBUTION-FRESH-QUALIFICATION"
PASS_MARKER = "P9_S4_WARNING_ATTRIBUTION_PASS"
SELF_TEST_MARKER = "P9_S4_WARNING_ATTRIBUTION_SELF_TEST_PASS matrix=9"
SCRIPT_RELATIVE_PATH = "scripts/verify-p9-s4-warning-attribution.py"

MAX_RAW_BYTES = 64 * 1024 * 1024
MAX_JSON_BYTES = 32 * 1024 * 1024
MAX_SOURCE_FILE_BYTES = 16 * 1024 * 1024
MAX_OUTPUT_FILE_BYTES = 128 * 1024 * 1024
MAX_SOURCE_ENTRIES = 20_000
MAX_CONFIG_ENTRIES = 5_000
MAX_OUTPUT_ENTRIES = 200_000
MAX_SOURCE_TOTAL_BYTES = 1024 * 1024 * 1024
MAX_CONFIG_TOTAL_BYTES = 512 * 1024 * 1024
MAX_OUTPUT_TOTAL_BYTES = 8 * 1024 * 1024 * 1024
MAX_LOG_LINES = 2_000_000
MAX_LINE_CHARS = 2 * 1024 * 1024

HEX40_RE = re.compile(r"^[0-9a-f]{40}$")
HEX64_RE = re.compile(r"^[0-9a-f]{64}$")
OPAQUE_SOURCE_RE = re.compile(r"^(?:freeze:[0-9a-f]{64}|commit:[0-9a-f]{40})$")
TOKEN_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:@/+\-]{0,255}$")
TASK_PATH_RE = re.compile(r"^:[A-Za-z0-9_.:\-]+$")
JAVA_COMPILE_TASK_RE = re.compile(
    r"^:(?:[A-Za-z0-9_.\-]+:)*compile[A-Za-z0-9_.\-]*Java$"
)
ANSI_RE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
DIAGNOSTIC_RE = re.compile(
    r"^(?P<path>/[^\r\n]+\.java):(?P<line>[1-9][0-9]*): warning: "
    r"\[(?P<category>[^\]\r\n]+)\] (?P<message>[^\r\n]+)$"
)
SUMMARY_RE = re.compile(r"^(?P<count>[0-9]+) warnings?$")
WORKER_LAUNCH_RE = re.compile(
    r"^Starting process 'Gradle Test Executor (?P<worker>[1-9][0-9]*)'\. Working directory: "
    r"(?P<working>.+) Command: (?P<command>.+)$"
)
WORKER_STARTED_EXECUTING_RE = re.compile(
    r"^Gradle Test Executor (?P<worker>[1-9][0-9]*) started executing tests\.$"
)
WORKER_STARTED_RE = re.compile(
    r"^Gradle Test Run :test > Gradle Test Executor "
    r"(?P<worker>[1-9][0-9]*) STARTED$"
)
WORKER_PASSED_RE = re.compile(
    r"^Gradle Test Run :test > Gradle Test Executor "
    r"(?P<worker>[1-9][0-9]*) PASSED$"
)
WORKER_FINISHED_RE = re.compile(
    r"^Gradle Test Executor (?P<worker>[1-9][0-9]*) finished executing tests\.$"
)

KNOWN_TASK_STATES = {
    "UP-TO-DATE",
    "FROM-CACHE",
    "SKIPPED",
    "NO-SOURCE",
    "FAILED",
}

MAIN_SOURCE = (
    "src/main/java/com/yo1no/gramarye/magic/api/registry/"
    "P8BuiltInClientProfileFactories.java"
)
TEST_SOURCE = (
    "src/test/java/com/yo1no/gramarye/magic/api/registry/"
    "P8BuiltInClientProfileFactoriesTest.java"
)

ROLE_SPECS: dict[str, dict[str, Any]] = {
    "MAIN_COMPILE": {
        "task": ":compileJava",
        "source_set": "main",
        "source": MAIN_SOURCE,
        "line": 17,
        "source_line": "bus = EventBusSubscriber.Bus.MOD)",
        "output": "build/classes/java/main",
        "symbols": (
            "bus() in EventBusSubscriber",
            "Bus in EventBusSubscriber",
        ),
    },
    "TEST_COMPILE": {
        "task": ":compileTestJava",
        "source_set": "test",
        "source": TEST_SOURCE,
        "line": 45,
        "source_line": (
            "assertEquals(EventBusSubscriber.Bus.MOD, subscriber.bus());"
        ),
        "output": "build/classes/java/test",
        "symbols": (
            "Bus in EventBusSubscriber",
            "bus() in EventBusSubscriber",
        ),
    },
}

MOD_FOLDER_LABEL = "p4A3ProbeTestSupport"
MOD_FOLDER_RELATIVE_PATHS = (
    "build/classes/java/main",
    "build/resources/main",
    "build/classes/java/p4A3Probe",
    "build/resources/p4A3Probe",
    "build/classes/java/p4B2Probe",
    "build/resources/p4B2Probe",
    "build/classes/java/p4C2Probe",
    "build/resources/p4C2Probe",
    "build/classes/java/p4D3Probe",
    "build/resources/p4D3Probe",
    "build/classes/java/p4E0Research",
    "build/resources/p4E0Research",
    "build/classes/java/test",
    "build/resources/test",
)


class InputFailure(Exception):
    """A malformed, missing, unsafe, or unreadable caller input."""


class PolicyRejection(Exception):
    """A well-formed execution that does not satisfy the WC1 policy."""


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _canonical_json_bytes(value: Any) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def _canonical_digest(value: Any) -> str:
    return _sha256(_canonical_json_bytes(value))


def _validate_json_shape(value: Any, label: str) -> None:
    entries = 0

    def visit(node: Any, depth: int) -> None:
        nonlocal entries
        if depth > 32:
            raise InputFailure(f"{label} exceeds JSON nesting bound")
        entries += 1
        if entries > 1_000_000:
            raise InputFailure(f"{label} exceeds JSON value bound")
        if node is None or isinstance(node, (bool, int, float)):
            return
        if isinstance(node, str):
            if len(node) > 2 * 1024 * 1024:
                raise InputFailure(f"{label} contains an oversized string")
            return
        if isinstance(node, list):
            for item in node:
                visit(item, depth + 1)
            return
        if isinstance(node, dict):
            for key, item in node.items():
                if not isinstance(key, str):
                    raise InputFailure(f"{label} contains a non-string key")
                visit(item, depth + 1)
            return
        raise InputFailure(f"{label} contains unsupported JSON value")

    visit(value, 0)


def _json_no_duplicates(data: bytes, label: str) -> Any:
    def pairs(values: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in values:
            if key in result:
                raise InputFailure(f"{label} contains duplicate key: {key}")
            result[key] = value
        return result

    try:
        value = json.loads(data.decode("utf-8"), object_pairs_hook=pairs)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise InputFailure(f"{label} is not strict UTF-8 JSON: {error}") from error
    _validate_json_shape(value, label)
    return value


def _canonical_existing_dir(value: str | Path, label: str) -> Path:
    path = Path(value)
    if not path.is_absolute():
        raise InputFailure(f"{label} must be absolute: {path}")
    absolute = Path(os.path.abspath(os.fspath(path)))
    try:
        resolved = path.resolve(strict=True)
        info = path.lstat()
    except OSError as error:
        raise InputFailure(f"{label} is unavailable: {path}: {error}") from error
    if absolute != resolved:
        raise InputFailure(f"{label} is non-canonical or traverses a symlink: {path}")
    if stat.S_ISLNK(info.st_mode) or not stat.S_ISDIR(info.st_mode):
        raise InputFailure(f"{label} is not a real directory: {path}")
    return resolved


def _read_regular(
    value: str | Path,
    label: str,
    maximum: int,
    *,
    allow_empty: bool = False,
) -> tuple[Path, bytes, os.stat_result]:
    path = Path(value)
    if not path.is_absolute():
        raise InputFailure(f"{label} must be absolute: {path}")
    absolute = Path(os.path.abspath(os.fspath(path)))
    try:
        resolved = path.resolve(strict=True)
        link_info = path.lstat()
    except OSError as error:
        raise InputFailure(f"{label} is unavailable: {path}: {error}") from error
    if absolute != resolved:
        raise InputFailure(f"{label} is non-canonical or traverses a symlink: {path}")
    if stat.S_ISLNK(link_info.st_mode) or not stat.S_ISREG(link_info.st_mode):
        raise InputFailure(f"{label} is not a real regular file: {path}")
    flags = os.O_RDONLY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(resolved, flags)
        try:
            before = os.fstat(descriptor)
            if not stat.S_ISREG(before.st_mode):
                raise InputFailure(f"{label} changed away from a regular file")
            if before.st_size > maximum:
                raise InputFailure(
                    f"{label} exceeds byte bound: {before.st_size} > {maximum}"
                )
            chunks: list[bytes] = []
            remaining = maximum + 1
            while remaining > 0:
                chunk = os.read(descriptor, min(1024 * 1024, remaining))
                if not chunk:
                    break
                chunks.append(chunk)
                remaining -= len(chunk)
            data = b"".join(chunks)
            after = os.fstat(descriptor)
        finally:
            os.close(descriptor)
    except InputFailure:
        raise
    except OSError as error:
        raise InputFailure(f"could not read {label}: {resolved}: {error}") from error
    if len(data) > maximum:
        raise InputFailure(f"{label} exceeds byte bound while being read")
    if not allow_empty and not data:
        raise InputFailure(f"{label} is empty")
    stable_fields = (
        before.st_dev,
        before.st_ino,
        before.st_mode,
        before.st_size,
        before.st_mtime_ns,
    )
    if stable_fields != (
        after.st_dev,
        after.st_ino,
        after.st_mode,
        after.st_size,
        after.st_mtime_ns,
    ):
        raise InputFailure(f"{label} changed while being read")
    if len(data) != before.st_size:
        raise InputFailure(f"{label} was not read completely")
    return resolved, data, before


def _path_within(root: Path, path: Path, label: str) -> None:
    try:
        common = Path(os.path.commonpath((os.fspath(root), os.fspath(path))))
    except ValueError as error:
        raise InputFailure(f"{label} is outside the project root: {path}") from error
    if common != root:
        raise InputFailure(f"{label} escapes the project root: {path}")


def _relative_record(
    project_root: Path,
    path: Path,
    label: str,
    maximum: int,
) -> dict[str, Any]:
    _path_within(project_root, path, label)
    resolved, data, info = _read_regular(path, label, maximum, allow_empty=True)
    return {
        "path": resolved.relative_to(project_root).as_posix(),
        "mode": f"{stat.S_IMODE(info.st_mode):04o}",
        "bytes": len(data),
        "sha256": _sha256(data),
    }


def _external_file_record(
    path: str | Path,
    label: str,
    maximum: int,
    *,
    allow_empty: bool = False,
) -> dict[str, Any]:
    resolved, data, info = _read_regular(
        path, label, maximum, allow_empty=allow_empty
    )
    return {
        "path": os.fspath(resolved),
        "mode": f"{stat.S_IMODE(info.st_mode):04o}",
        "bytes": len(data),
        "sha256": _sha256(data),
    }


def _walk_files(
    root: Path,
    project_root: Path,
    label: str,
    *,
    maximum_entries: int,
    maximum_total: int,
    maximum_file: int,
) -> list[dict[str, Any]]:
    directory = _canonical_existing_dir(root, label)
    _path_within(project_root, directory, label)
    records: list[dict[str, Any]] = []
    total = 0

    def walk(current: Path) -> None:
        nonlocal total
        try:
            entries = sorted(os.scandir(current), key=lambda item: item.name)
        except OSError as error:
            raise InputFailure(f"cannot enumerate {label}: {current}: {error}") from error
        try:
            for entry in entries:
                entry_path = Path(entry.path)
                try:
                    info = entry.stat(follow_symlinks=False)
                except OSError as error:
                    raise InputFailure(
                        f"cannot inspect {label} entry: {entry_path}: {error}"
                    ) from error
                if stat.S_ISLNK(info.st_mode):
                    raise InputFailure(f"{label} contains a symlink: {entry_path}")
                if stat.S_ISDIR(info.st_mode):
                    walk(entry_path)
                elif stat.S_ISREG(info.st_mode):
                    if len(records) >= maximum_entries:
                        raise InputFailure(f"{label} exceeds entry bound")
                    record = _relative_record(
                        project_root, entry_path, label, maximum_file
                    )
                    total += int(record["bytes"])
                    if total > maximum_total:
                        raise InputFailure(f"{label} exceeds total byte bound")
                    records.append(record)
                else:
                    raise InputFailure(
                        f"{label} contains a special filesystem entry: {entry_path}"
                    )
        finally:
            for entry in entries:
                entry.close() if hasattr(entry, "close") else None

    walk(directory)
    return records


def _manifest(records: Iterable[dict[str, Any]]) -> dict[str, Any]:
    ordered = sorted(records, key=lambda record: str(record["path"]))
    if len({str(record["path"]) for record in ordered}) != len(ordered):
        raise InputFailure("manifest contains duplicate paths")
    total = sum(int(record["bytes"]) for record in ordered)
    return {
        "entry_count": len(ordered),
        "total_bytes": total,
        "entries_sha256": _canonical_digest(ordered),
        "entries": ordered,
    }


def _collect_source_snapshot(project_root: Path) -> dict[str, Any]:
    records: list[dict[str, Any]] = []
    role_counts: dict[str, int] = {}
    for source_set, relative in (
        ("main", "src/main/java"),
        ("test", "src/test/java"),
    ):
        found = _walk_files(
            project_root / relative,
            project_root,
            f"{source_set} source snapshot",
            maximum_entries=MAX_SOURCE_ENTRIES,
            maximum_total=MAX_SOURCE_TOTAL_BYTES,
            maximum_file=MAX_SOURCE_FILE_BYTES,
        )
        java_count = sum(record["path"].endswith(".java") for record in found)
        if java_count == 0:
            raise PolicyRejection(f"required {source_set} Java source is empty")
        role_counts[source_set] = java_count
        records.extend(found)
    result = _manifest(records)
    result["java_file_counts"] = role_counts
    return result


def _collect_config_snapshot(project_root: Path) -> dict[str, Any]:
    required_files = (
        "build.gradle",
        "settings.gradle",
        "gradle.properties",
        "gradlew",
        "gradlew.bat",
        ".github/workflows/build.yml",
        "scripts/run-p9-s3-rd1-unit-test-diagnostics.sh",
    )
    records: dict[str, dict[str, Any]] = {}
    for relative in required_files:
        record = _relative_record(
            project_root,
            project_root / relative,
            f"required configuration {relative}",
            MAX_SOURCE_FILE_BYTES,
        )
        records[str(record["path"])] = record
    for relative in ("gradle", "buildSrc"):
        candidate = project_root / relative
        if not candidate.exists():
            if relative == "gradle":
                raise InputFailure("required gradle directory is missing")
            continue
        for record in _walk_files(
            candidate,
            project_root,
            f"{relative} configuration snapshot",
            maximum_entries=MAX_CONFIG_ENTRIES,
            maximum_total=MAX_CONFIG_TOTAL_BYTES,
            maximum_file=MAX_OUTPUT_FILE_BYTES,
        ):
            records[str(record["path"])] = record
    for relative in ("build.gradle.kts", "settings.gradle.kts", "gradle.lockfile"):
        candidate = project_root / relative
        if candidate.exists():
            record = _relative_record(
                project_root,
                candidate,
                f"optional configuration {relative}",
                MAX_SOURCE_FILE_BYTES,
            )
            records[str(record["path"])] = record
    if len(records) > MAX_CONFIG_ENTRIES:
        raise InputFailure("configuration snapshot exceeds entry bound")
    result = _manifest(records.values())
    if int(result["total_bytes"]) > MAX_CONFIG_TOTAL_BYTES:
        raise InputFailure("configuration snapshot exceeds total byte bound")
    return result


def _collect_output_manifest(
    project_root: Path,
    supplied: str | Path,
    role: str,
) -> dict[str, Any]:
    spec = ROLE_SPECS[role]
    expected = project_root / str(spec["output"])
    directory = _canonical_existing_dir(supplied, f"{role} output")
    if directory != expected:
        raise PolicyRejection(
            f"{role} output is not the project-owned path: "
            f"expected={expected} actual={directory}"
        )
    records = _walk_files(
        directory,
        project_root,
        f"{role} output",
        maximum_entries=MAX_OUTPUT_ENTRIES,
        maximum_total=MAX_OUTPUT_TOTAL_BYTES,
        maximum_file=MAX_OUTPUT_FILE_BYTES,
    )
    class_count = sum(record["path"].endswith(".class") for record in records)
    if class_count == 0:
        raise PolicyRejection(f"{role} output has no class files")
    result = _manifest(records)
    result["root"] = str(spec["output"])
    result["class_file_count"] = class_count
    return result


def _parse_properties(data: bytes, label: str) -> dict[str, str]:
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise InputFailure(f"{label} is not UTF-8: {error}") from error
    values: dict[str, str] = {}
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" not in line:
            raise InputFailure(f"{label} has unsupported line {number}")
        key, value = line.split("=", 1)
        key = key.strip()
        if not key or key in values:
            raise InputFailure(f"{label} has empty or duplicate key at line {number}")
        values[key] = value.strip()
    return values


def _collect_gradle_metadata(project_root: Path) -> dict[str, Any]:
    properties_path = project_root / "gradle/wrapper/gradle-wrapper.properties"
    resolved, data, _ = _read_regular(
        properties_path,
        "Gradle wrapper properties",
        MAX_SOURCE_FILE_BYTES,
    )
    values = _parse_properties(data, "Gradle wrapper properties")
    raw_url = values.get("distributionUrl")
    distribution_sha = values.get("distributionSha256Sum")
    if raw_url is None or distribution_sha is None:
        raise InputFailure("Gradle wrapper distribution identity is incomplete")
    url = raw_url.replace("\\:", ":")
    match = re.fullmatch(
        r"https://services\.gradle\.org/distributions/gradle-([0-9.]+)-bin\.zip",
        url,
    )
    if match is None:
        raise PolicyRejection(f"unexpected Gradle wrapper distribution: {url}")
    if match.group(1) != "9.2.1":
        raise PolicyRejection(
            f"Gradle version drift: expected=9.2.1 actual={match.group(1)}"
        )
    if not HEX64_RE.fullmatch(distribution_sha):
        raise InputFailure("Gradle wrapper distribution SHA-256 is invalid")
    wrapper_jar = _relative_record(
        project_root,
        project_root / "gradle/wrapper/gradle-wrapper.jar",
        "Gradle wrapper JAR",
        MAX_OUTPUT_FILE_BYTES,
    )
    return {
        "version": match.group(1),
        "distribution_url": url,
        "distribution_sha256": distribution_sha,
        "wrapper_properties": _relative_record(
            project_root,
            resolved,
            "Gradle wrapper properties",
            MAX_SOURCE_FILE_BYTES,
        ),
        "wrapper_jar": wrapper_jar,
    }


def _parse_java_release(data: bytes) -> dict[str, str]:
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise InputFailure(f"JDK release metadata is not UTF-8: {error}") from error
    values: dict[str, str] = {}
    for number, raw in enumerate(text.splitlines(), 1):
        if not raw:
            continue
        if "=" not in raw:
            raise InputFailure(f"JDK release metadata has malformed line {number}")
        key, value = raw.split("=", 1)
        if not key or key in values or len(value) < 2:
            raise InputFailure(f"JDK release metadata has invalid line {number}")
        if not (value.startswith('"') and value.endswith('"')):
            raise InputFailure(f"JDK release metadata has unquoted line {number}")
        values[key] = value[1:-1]
    return values


def _collect_java_metadata(java_home_value: str | Path) -> dict[str, Any]:
    java_home = _canonical_existing_dir(java_home_value, "Java home")
    records: dict[str, dict[str, Any]] = {}
    raw_by_name: dict[str, bytes] = {}
    for relative, maximum in (
        ("bin/java", MAX_OUTPUT_FILE_BYTES),
        ("bin/javac", MAX_OUTPUT_FILE_BYTES),
        ("release", MAX_SOURCE_FILE_BYTES),
    ):
        resolved, data, info = _read_regular(
            java_home / relative,
            f"Java home {relative}",
            maximum,
        )
        if relative.startswith("bin/") and stat.S_IMODE(info.st_mode) & 0o111 == 0:
            raise InputFailure(f"Java executable is not executable: {resolved}")
        records[relative] = {
            "path": relative,
            "mode": f"{stat.S_IMODE(info.st_mode):04o}",
            "bytes": len(data),
            "sha256": _sha256(data),
        }
        raw_by_name[relative] = data
    release = _parse_java_release(raw_by_name["release"])
    version = release.get("JAVA_VERSION")
    if version is None:
        raise InputFailure("JDK release metadata lacks JAVA_VERSION")
    if version.split(".", 1)[0] != "21":
        raise PolicyRejection(f"Java version drift: expected major 21 actual={version}")
    return {
        "home": os.fspath(java_home),
        "java_version": version,
        "implementor": release.get("IMPLEMENTOR"),
        "runtime_version": release.get("JAVA_RUNTIME_VERSION"),
        "os_arch": release.get("OS_ARCH"),
        "files": records,
    }


def _git_value(project_root: Path, *arguments: str) -> str:
    try:
        completed = subprocess.run(
            ["git", "-C", os.fspath(project_root), *arguments],
            check=False,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=15,
            text=True,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise InputFailure(f"read-only git inspection failed: {error}") from error
    if completed.returncode != 0:
        detail = completed.stderr.strip().replace("\n", " ")[:1000]
        raise InputFailure(f"read-only git inspection rejected: {detail}")
    lines = completed.stdout.splitlines()
    if len(lines) != 1 or not lines[0]:
        raise InputFailure("read-only git inspection returned an ambiguous value")
    return lines[0]


def _collect_git_identity(
    project_root: Path,
    expected_commit: str,
    expected_tree: str,
) -> dict[str, str]:
    top = Path(_git_value(project_root, "rev-parse", "--show-toplevel"))
    try:
        top = top.resolve(strict=True)
    except OSError as error:
        raise InputFailure(f"git top-level path cannot be resolved: {error}") from error
    if top != project_root:
        raise PolicyRejection(
            f"project root is not the git top level: expected={project_root} actual={top}"
        )
    actual_commit = _git_value(project_root, "rev-parse", "HEAD")
    actual_tree = _git_value(project_root, "rev-parse", "HEAD^{tree}")
    if actual_commit != expected_commit:
        raise PolicyRejection(
            f"commit identity differs: expected={expected_commit} actual={actual_commit}"
        )
    if actual_tree != expected_tree:
        raise PolicyRejection(
            f"tree identity differs: expected={expected_tree} actual={actual_tree}"
        )
    return {
        "root": os.fspath(project_root),
        "commit": actual_commit,
        "tree": actual_tree,
    }


def _collect_tool_identity(project_root: Path, *, enforce_location: bool) -> dict[str, Any]:
    script = Path(__file__).resolve(strict=True)
    if enforce_location and script != project_root / SCRIPT_RELATIVE_PATH:
        raise PolicyRejection(
            f"warning helper is not project-owned: expected="
            f"{project_root / SCRIPT_RELATIVE_PATH} actual={script}"
        )
    resolved, data, info = _read_regular(
        script,
        "warning attribution helper",
        MAX_SOURCE_FILE_BYTES,
    )
    return {
        "path": (
            SCRIPT_RELATIVE_PATH if enforce_location else os.fspath(resolved)
        ),
        "mode": f"{stat.S_IMODE(info.st_mode):04o}",
        "bytes": len(data),
        "sha256": _sha256(data),
    }


def _validate_common_arguments(arguments: argparse.Namespace) -> None:
    if arguments.campaign != CAMPAIGN:
        raise PolicyRejection(
            f"campaign differs: expected={CAMPAIGN} actual={arguments.campaign}"
        )
    if not OPAQUE_SOURCE_RE.fullmatch(arguments.source_identity):
        raise InputFailure(
            "source identity must be freeze:<sha256> or commit:<sha1>"
        )
    if not HEX40_RE.fullmatch(arguments.commit):
        raise InputFailure("commit must be a lowercase 40-hex identity")
    if not HEX40_RE.fullmatch(arguments.tree):
        raise InputFailure("tree must be a lowercase 40-hex identity")
    if arguments.attempt != 1:
        raise PolicyRejection(
            f"WC1 permits exact attempt 1: actual={arguments.attempt}"
        )
    for name in ("run_id", "job"):
        value = getattr(arguments, name)
        if not TOKEN_RE.fullmatch(value):
            raise InputFailure(f"{name.replace('_', '-')} is not a bounded token")


def _build_context(
    arguments: argparse.Namespace,
    *,
    verify_git: bool = True,
    enforce_tool_location: bool = True,
) -> tuple[dict[str, Any], Path]:
    _validate_common_arguments(arguments)
    project_root = _canonical_existing_dir(arguments.project_root, "project root")
    if verify_git:
        repository = _collect_git_identity(
            project_root, arguments.commit, arguments.tree
        )
    else:
        repository = {
            "root": os.fspath(project_root),
            "commit": arguments.commit,
            "tree": arguments.tree,
        }
    context = {
        "campaign": arguments.campaign,
        "source_identity": arguments.source_identity,
        "repository": repository,
        "execution": {
            "run_id": arguments.run_id,
            "attempt": arguments.attempt,
            "job": arguments.job,
        },
        "source_snapshot": _collect_source_snapshot(project_root),
        "configuration_snapshot": _collect_config_snapshot(project_root),
        "java": _collect_java_metadata(arguments.java_home),
        "gradle": _collect_gradle_metadata(project_root),
        "tool": _collect_tool_identity(
            project_root, enforce_location=enforce_tool_location
        ),
    }
    context["context_sha256"] = _canonical_digest(context)
    return context, project_root


def _verify_authorized_source_lines(project_root: Path) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for role, spec in ROLE_SPECS.items():
        path = project_root / str(spec["source"])
        record = _relative_record(
            project_root,
            path,
            f"{role} warning source",
            MAX_SOURCE_FILE_BYTES,
        )
        _, data, _ = _read_regular(
            path,
            f"{role} warning source",
            MAX_SOURCE_FILE_BYTES,
        )
        try:
            lines = data.decode("utf-8").splitlines()
        except UnicodeDecodeError as error:
            raise InputFailure(f"{role} warning source is not UTF-8: {error}") from error
        number = int(spec["line"])
        if len(lines) < number or lines[number - 1].strip() != spec["source_line"]:
            actual = None if len(lines) < number else lines[number - 1].strip()
            raise PolicyRejection(
                f"{role} authorized warning callsite differs at "
                f"{spec['source']}:{number}: actual={actual!r}"
            )
        result[role] = record
    return result


def _raw_record(path: str | Path, label: str) -> tuple[dict[str, Any], str]:
    resolved, data, info = _read_regular(path, label, MAX_RAW_BYTES)
    if b"\x00" in data:
        raise InputFailure(f"{label} contains a NUL byte")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise InputFailure(f"{label} is not strict UTF-8: {error}") from error
    return {
        "path": os.fspath(resolved),
        "mode": f"{stat.S_IMODE(info.st_mode):04o}",
        "bytes": len(data),
        "sha256": _sha256(data),
    }, text


def _expected_diagnostics(role: str) -> list[dict[str, Any]]:
    spec = ROLE_SPECS[role]
    return sorted(
        [
            {
                "task": spec["task"],
                "source": spec["source"],
                "file": Path(str(spec["source"])).name,
                "line": spec["line"],
                "category": "removal",
                "symbol": symbol,
                "category_symbol": f"removal:{symbol}",
                "message": (
                    f"{symbol} has been deprecated and marked for removal"
                ),
            }
            for symbol in spec["symbols"]
        ],
        key=lambda value: (
            str(value["source"]),
            int(value["line"]),
            str(value["category_symbol"]),
        ),
    )


def _diagnostic_signature(value: dict[str, Any]) -> tuple[str, int, str]:
    return (
        str(value["source"]),
        int(value["line"]),
        str(value["category_symbol"]),
    )


def _task_role(task: str | None) -> str | None:
    for role, spec in ROLE_SPECS.items():
        if task == spec["task"]:
            return role
    return None


def _normalise_diagnostic(
    match: re.Match[str],
    role: str,
    project_root: Path,
    line_number: int,
) -> dict[str, Any]:
    spec = ROLE_SPECS[role]
    raw_path = Path(match.group("path"))
    expected_path = project_root / str(spec["source"])
    if raw_path != expected_path:
        raise PolicyRejection(
            f"{role} has warning at an unauthorized source path on log line "
            f"{line_number}: {raw_path}"
        )
    _path_within(project_root, raw_path, f"{role} diagnostic source")
    try:
        resolved = raw_path.resolve(strict=True)
    except OSError as error:
        raise PolicyRejection(
            f"{role} diagnostic source is unavailable: {raw_path}: {error}"
        ) from error
    if resolved != raw_path:
        raise PolicyRejection(f"{role} diagnostic path traverses a symlink")
    actual_line = int(match.group("line"))
    category = match.group("category")
    message = match.group("message")
    if actual_line != int(spec["line"]):
        raise PolicyRejection(
            f"{role} warning line differs: expected={spec['line']} actual={actual_line}"
        )
    if category != "removal":
        raise PolicyRejection(
            f"{role} has unknown compiler warning category: {category}"
        )
    suffix = " has been deprecated and marked for removal"
    if not message.endswith(suffix):
        raise PolicyRejection(f"{role} has unknown compiler warning message: {message}")
    symbol = message[: -len(suffix)]
    if symbol not in spec["symbols"]:
        raise PolicyRejection(
            f"{role} has unknown compiler warning symbol/callsite: {symbol}"
        )
    return {
        "task": spec["task"],
        "source": spec["source"],
        "file": raw_path.name,
        "line": actual_line,
        "category": category,
        "symbol": symbol,
        "category_symbol": f"{category}:{symbol}",
        "message": message,
        "raw_line": line_number,
    }


def _parse_log(text: str, project_root: Path) -> dict[str, Any]:
    raw_lines = text.splitlines()
    if len(raw_lines) > MAX_LOG_LINES:
        raise InputFailure(f"raw log exceeds line bound: {len(raw_lines)}")
    task_states: dict[str, list[dict[str, Any]]] = {}
    diagnostics = {role: [] for role in ROLE_SPECS}
    summaries = {role: [] for role in ROLE_SPECS}
    compiler_issues: list[str] = []
    noncompiler_warning_observations: list[dict[str, Any]] = []
    build_tool_diagnostics: list[dict[str, Any]] = []
    worker_launches: list[dict[str, Any]] = []
    current_task: str | None = None
    clean_lines: list[str] = []

    for number, raw in enumerate(raw_lines, 1):
        if len(raw) > MAX_LINE_CHARS:
            raise InputFailure(f"raw log line {number} exceeds character bound")
        line = ANSI_RE.sub("", raw.rstrip("\r"))
        clean_lines.append(line)
        if line.startswith("> Task "):
            remainder = line[len("> Task ") :]
            pieces = remainder.split()
            if not pieces or not TASK_PATH_RE.fullmatch(pieces[0]):
                compiler_issues.append(f"malformed Gradle task header at line {number}")
                current_task = None
                continue
            current_task = pieces[0]
            suffix = " ".join(pieces[1:])
            if not suffix:
                state = "EXECUTED"
            elif suffix in KNOWN_TASK_STATES:
                state = suffix
            else:
                state = f"UNKNOWN:{suffix}"
            task_states.setdefault(current_task, []).append(
                {"line": number, "state": state}
            )
            continue

        role = _task_role(current_task)
        java_compile_task = (
            current_task is not None
            and JAVA_COMPILE_TASK_RE.fullmatch(current_task) is not None
        )
        worker_launch = WORKER_LAUNCH_RE.fullmatch(line)
        if worker_launch is not None:
            worker_launches.append(
                {
                    "line": number,
                    "worker": worker_launch.group("worker"),
                    "working_directory": worker_launch.group("working"),
                    "command": worker_launch.group("command"),
                }
            )
            continue
        match = DIAGNOSTIC_RE.fullmatch(line)
        if match is not None:
            if java_compile_task and role is None:
                compiler_issues.append(
                    "compiler diagnostic belongs to an unauthorized compile task: "
                    f"task={current_task} line={number}"
                )
            elif role is None:
                noncompiler_warning_observations.append(
                    {
                        "line": number,
                        "scope": current_task,
                        "kind": "structured-warning-outside-compiler",
                        "source": match.group("path"),
                        "source_line": int(match.group("line")),
                        "category": match.group("category"),
                        "sha256": _sha256(line.encode("utf-8")),
                    }
                )
            else:
                try:
                    diagnostic = _normalise_diagnostic(
                        match, role, project_root, number
                    )
                    diagnostics[role].append(diagnostic)
                except PolicyRejection as error:
                    compiler_issues.append(str(error))
            continue

        summary = SUMMARY_RE.fullmatch(line)
        if summary is not None:
            count = int(summary.group("count"))
            if java_compile_task and role is None:
                compiler_issues.append(
                    "numeric warning summary belongs to an unauthorized compile "
                    f"task: task={current_task} line={number} count={count}"
                )
            elif role is None:
                noncompiler_warning_observations.append(
                    {
                        "line": number,
                        "scope": current_task,
                        "kind": "numeric-warning-summary-outside-compiler",
                        "count": count,
                    }
                )
            else:
                summaries[role].append(count)
            continue

        lower_line = line.lower()
        raw_javac_kind: str | None = None
        if line.startswith("Note:"):
            raw_javac_kind = "raw-compiler-note"
        elif (
            "unchecked or unsafe operations" in lower_line
            or "-xlint:unchecked" in lower_line
        ):
            raw_javac_kind = "raw-unchecked-compiler-note"
        elif (
            "deprecated api" in lower_line
            or "-xlint:deprecation" in lower_line
        ):
            raw_javac_kind = "raw-deprecation-compiler-note"
        if raw_javac_kind is not None:
            if java_compile_task:
                compiler_issues.append(
                    f"{raw_javac_kind} in {role or current_task} at line {number}"
                )
            else:
                noncompiler_warning_observations.append(
                    {
                        "line": number,
                        "scope": current_task,
                        "kind": raw_javac_kind + "-outside-compiler",
                        "sha256": _sha256(line.encode("utf-8")),
                    }
                )
            continue

        if "deprecat" in lower_line:
            is_gradle_deprecation = (
                lower_line.startswith(
                    "deprecated gradle features were used in this build"
                )
                or lower_line.startswith(
                    "you can use '--warning-mode all' to show the individual "
                    "deprecation warnings"
                )
                or (
                    "gradle" in lower_line
                    and (
                        "has been deprecated" in lower_line
                        or "deprecation warning" in lower_line
                    )
                )
                or "https://docs.gradle.org/" in lower_line
            )
            if is_gradle_deprecation:
                build_tool_diagnostics.append(
                    {
                        "line": number,
                        "scope": current_task,
                        "kind": "gradle-deprecation-text",
                        "sha256": _sha256(line.encode("utf-8")),
                    }
                )
            else:
                if java_compile_task:
                    compiler_issues.append(
                        f"unclassified deprecation text in {role or current_task} "
                        f"at line {number}"
                    )
                else:
                    noncompiler_warning_observations.append(
                        {
                            "line": number,
                            "scope": current_task,
                            "kind": "unclassified-deprecation-outside-compiler",
                            "sha256": _sha256(line.encode("utf-8")),
                        }
                    )
            continue

        if "warning:" in line or "uses unchecked or unsafe operations" in line \
                or "Recompile with -Xlint:unchecked" in line:
            if java_compile_task:
                compiler_issues.append(
                    "raw/unchecked/unknown compiler diagnostic in "
                    f"{role or current_task} at line {number}"
                )
            else:
                noncompiler_warning_observations.append(
                    {
                        "line": number,
                        "scope": current_task,
                        "kind": "warning-text-outside-compiler",
                        "sha256": _sha256(line.encode("utf-8")),
                    }
                )

    test_failure_lines = [
        number
        for number, line in enumerate(clean_lines, 1)
        if re.fullmatch(r"Gradle Test Run :test(?: > .*)? FAILED", line)
    ]
    build_failed_lines = [
        number
        for number, line in enumerate(clean_lines, 1)
        if line.startswith("BUILD FAILED")
    ]
    return {
        "task_states": task_states,
        "diagnostics": diagnostics,
        "summaries": summaries,
        "compiler_issues": compiler_issues,
        "worker_launches": worker_launches,
        "build_tool_diagnostics": build_tool_diagnostics,
        "noncompiler_warning_observations": noncompiler_warning_observations,
        "markers": {
            "build_successful": sum(
                line.startswith("BUILD SUCCESSFUL") for line in clean_lines
            ),
            "build_failed_lines": build_failed_lines,
            "test_failure_lines": test_failure_lines,
            "worker_started_executing": [
                match.group("worker")
                for line in clean_lines
                if (match := WORKER_STARTED_EXECUTING_RE.fullmatch(line))
            ],
            "worker_started": [
                match.group("worker")
                for line in clean_lines
                if (match := WORKER_STARTED_RE.fullmatch(line))
            ],
            "worker_passed": [
                match.group("worker")
                for line in clean_lines
                if (match := WORKER_PASSED_RE.fullmatch(line))
            ],
            "worker_finished": [
                match.group("worker")
                for line in clean_lines
                if (match := WORKER_FINISHED_RE.fullmatch(line))
            ],
            "test_run_passed": clean_lines.count("Gradle Test Run :test PASSED"),
        },
    }


def _observe_task(parsed: dict[str, Any], task: str) -> dict[str, Any]:
    headers = parsed["task_states"].get(task, [])
    if not headers:
        raise PolicyRejection(f"required Gradle task observation is missing: {task}")
    states = {str(header["state"]) for header in headers}
    if len(states) != 1:
        raise PolicyRejection(
            f"Gradle task has conflicting states: task={task} states={sorted(states)}"
        )
    state = next(iter(states))
    if state.startswith("UNKNOWN:"):
        raise PolicyRejection(f"Gradle task has unknown state: task={task} state={state}")
    return {
        "task": task,
        "state": state,
        "header_count": len(headers),
        "header_lines": [int(header["line"]) for header in headers],
    }


def _require_build_success(parsed: dict[str, Any]) -> None:
    markers = parsed["markers"]
    if markers["build_failed_lines"]:
        raise PolicyRejection(
            f"Gradle raw log contains BUILD FAILED at lines "
            f"{markers['build_failed_lines']}"
        )
    if markers["build_successful"] != 1:
        raise PolicyRejection(
            "Gradle raw log must contain exactly one BUILD SUCCESSFUL marker: "
            f"actual={markers['build_successful']}"
        )


def _sorted_diagnostics(values: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    return sorted(
        values,
        key=lambda value: (
            str(value["source"]),
            int(value["line"]),
            str(value["category_symbol"]),
            int(value.get("raw_line", 0)),
        ),
    )


def _compile_roles(parsed: dict[str, Any]) -> dict[str, Any]:
    _require_build_success(parsed)
    if parsed["compiler_issues"]:
        raise PolicyRejection(str(parsed["compiler_issues"][0]))
    if ":test" in parsed["task_states"]:
        raise PolicyRejection("compile producer must not execute the :test task")
    roles: dict[str, Any] = {}
    for role, spec in ROLE_SPECS.items():
        task = _observe_task(parsed, str(spec["task"]))
        if task["header_count"] != 1:
            raise PolicyRejection(
                f"{role} direct compile task header count differs: "
                f"expected=1 actual={task['header_count']}"
            )
        if task["state"] != "EXECUTED":
            raise PolicyRejection(
                f"{role} was not directly executed: state={task['state']}"
            )
        actual = _sorted_diagnostics(parsed["diagnostics"][role])
        expected = _expected_diagnostics(role)
        actual_counter = Counter(_diagnostic_signature(item) for item in actual)
        expected_counter = Counter(_diagnostic_signature(item) for item in expected)
        if actual_counter != expected_counter:
            raise PolicyRejection(
                f"{role} diagnostic identity differs: expected="
                f"{sorted(expected_counter.elements())} actual="
                f"{sorted(actual_counter.elements())}"
            )
        summaries = list(parsed["summaries"][role])
        if summaries != [2]:
            raise PolicyRejection(
                f"{role} numeric summary differs: expected=[2] actual={summaries}"
            )
        roles[role] = {
            **task,
            "source_set": spec["source_set"],
            "attribution": "DIRECT_EXECUTION",
            "diagnostic_occurrences": len(actual),
            "distinct_symbols": sorted(item["symbol"] for item in actual),
            "diagnostics": actual,
            "numeric_summary_lines": len(summaries),
            "numeric_summary_values": summaries,
            "numeric_summary_total": sum(summaries),
        }
    return roles


def _build_compile_result(
    context: dict[str, Any],
    raw: dict[str, Any],
    parsed: dict[str, Any],
    outputs: dict[str, Any],
    source_files: dict[str, Any],
) -> dict[str, Any]:
    roles = _compile_roles(parsed)
    return {
        "schema": SCHEMA,
        "mode": "compile",
        "status": "PASS",
        "marker": PASS_MARKER,
        "policy": "DIRECT_MAIN_AND_TEST_COMPILATION_EVIDENCE",
        "context": context,
        "raw": raw,
        "authorized_warning_sources": source_files,
        "roles": roles,
        "outputs": outputs,
        "build_tool_diagnostics": parsed["build_tool_diagnostics"],
        "noncompiler_warning_observations": parsed[
            "noncompiler_warning_observations"
        ],
    }


def _validate_compile_proof_structure(
    proof: Any,
    context: dict[str, Any],
    project_root: Path,
) -> dict[str, Any]:
    if not isinstance(proof, dict):
        raise PolicyRejection("compile proof is not a JSON object")
    if proof.get("schema") != SCHEMA or proof.get("mode") != "compile":
        raise PolicyRejection("compile proof schema or mode differs")
    if proof.get("status") != "PASS" or proof.get("marker") != PASS_MARKER:
        raise PolicyRejection("compile proof is not a structured PASS")
    if proof.get("context") != context:
        raise PolicyRejection(
            "compile proof campaign/root/source/toolchain/config/run/attempt/job "
            "context differs"
        )
    roles = proof.get("roles")
    if not isinstance(roles, dict) or set(roles) != set(ROLE_SPECS):
        raise PolicyRejection("compile proof does not contain both exact compile roles")
    for role, spec in ROLE_SPECS.items():
        value = roles.get(role)
        if not isinstance(value, dict):
            raise PolicyRejection(f"compile proof role is malformed: {role}")
        if value.get("task") != spec["task"] or value.get("state") != "EXECUTED":
            raise PolicyRejection(f"compile proof role is not direct execution: {role}")
        if value.get("attribution") != "DIRECT_EXECUTION":
            raise PolicyRejection(f"compile proof attribution differs: {role}")
        actual = value.get("diagnostics")
        if not isinstance(actual, list):
            raise PolicyRejection(f"compile proof diagnostics are malformed: {role}")
        actual_counter = Counter(_diagnostic_signature(item) for item in actual)
        expected_counter = Counter(
            _diagnostic_signature(item) for item in _expected_diagnostics(role)
        )
        if actual_counter != expected_counter:
            raise PolicyRejection(f"compile proof diagnostic inventory differs: {role}")
        if value.get("numeric_summary_values") != [2]:
            raise PolicyRejection(f"compile proof summary arithmetic differs: {role}")

    raw_value = proof.get("raw")
    if not isinstance(raw_value, dict):
        raise PolicyRejection("compile proof raw identity is missing")
    try:
        raw_record, raw_text = _raw_record(raw_value.get("path"), "compile proof raw")
    except InputFailure as error:
        raise PolicyRejection(f"compile proof raw is missing/stale: {error}") from error
    if raw_record != raw_value:
        raise PolicyRejection("compile proof raw identity changed")
    recomputed_roles = _compile_roles(_parse_log(raw_text, project_root))
    if recomputed_roles != roles:
        raise PolicyRejection("compile proof roles do not match its preserved raw log")
    outputs = proof.get("outputs")
    if not isinstance(outputs, dict) or set(outputs) != set(ROLE_SPECS):
        raise PolicyRejection("compile proof output manifests are incomplete")
    return proof


def _unit_role(
    role: str,
    parsed: dict[str, Any],
    proof_role: dict[str, Any],
) -> dict[str, Any]:
    spec = ROLE_SPECS[role]
    task = _observe_task(parsed, str(spec["task"]))
    if task["header_count"] != 1:
        raise PolicyRejection(
            f"{role} unit compile task header count differs: "
            f"expected=1 actual={task['header_count']}"
        )
    state = task["state"]
    actual = _sorted_diagnostics(parsed["diagnostics"][role])
    summaries = list(parsed["summaries"][role])
    expected_counter = Counter(
        _diagnostic_signature(item) for item in _expected_diagnostics(role)
    )
    actual_counter = Counter(_diagnostic_signature(item) for item in actual)

    if state in {"UP-TO-DATE", "FROM-CACHE"}:
        if actual or summaries:
            raise PolicyRejection(
                f"{role} {state} was incorrectly accompanied by newly emitted "
                "compiler diagnostics or numeric summaries"
            )
        attribution = "REFERENCED_DIRECT_COMPILE_PROOF"
    elif state == "EXECUTED":
        for identity, count in actual_counter.items():
            if count > expected_counter[identity]:
                raise PolicyRejection(
                    f"{role} reexecution emitted unknown/duplicate diagnostic: {identity}"
                )
        if actual:
            if summaries != [len(actual)]:
                raise PolicyRejection(
                    f"{role} reexecution summary arithmetic differs: "
                    f"diagnostics={len(actual)} summaries={summaries}"
                )
        elif summaries:
            raise PolicyRejection(
                f"{role} reexecution emitted a summary without diagnostics: {summaries}"
            )
        attribution = "OBSERVED_REEXECUTION_AUTHORIZED_SUBSET"
    else:
        raise PolicyRejection(
            f"{role} has prohibited unit compilation state: {state}"
        )

    return {
        **task,
        "source_set": spec["source_set"],
        "attribution": attribution,
        "referenced_direct_role": {
            "task": proof_role["task"],
            "state": proof_role["state"],
            "attribution": proof_role["attribution"],
            "diagnostic_occurrences": proof_role["diagnostic_occurrences"],
            "numeric_summary_values": proof_role["numeric_summary_values"],
        },
        "observed_diagnostic_occurrences": len(actual),
        "observed_diagnostics": actual,
        "observed_numeric_summary_values": summaries,
        "observed_numeric_summary_total": sum(summaries),
    }


def _validate_worker_launch(
    parsed: dict[str, Any],
    context: dict[str, Any],
    project_root: Path,
) -> dict[str, Any]:
    launches = parsed["worker_launches"]
    if len(launches) != 1:
        raise PolicyRejection(
            f"expected exactly one test-worker process launch: actual={len(launches)}"
        )
    launch = launches[0]
    expected_working = project_root / "build/minecraft-junit"
    if Path(str(launch["working_directory"])) != expected_working:
        raise PolicyRejection(
            "test worker belongs to a different worktree: "
            f"expected working directory={expected_working} "
            f"actual={launch['working_directory']}"
        )
    try:
        command = shlex.split(str(launch["command"]), posix=True)
    except ValueError as error:
        raise PolicyRejection(f"test-worker command cannot be parsed: {error}") from error
    expected_java = Path(str(context["java"]["home"])) / "bin/java"
    if not command or Path(command[0]) != expected_java:
        raise PolicyRejection(
            f"test-worker Java differs: expected={expected_java} "
            f"actual={command[0] if command else None}"
        )
    expected_worker = f"Gradle Test Executor {launch['worker']}"
    if command.count(expected_worker) != 1:
        raise PolicyRejection(
            "test-worker process identity differs between launch label and argv"
        )
    if command[-2:] != [
        "worker.org.gradle.process.internal.worker.GradleWorkerMain",
        expected_worker,
    ]:
        raise PolicyRejection(
            "test-worker main class or terminal process identity differs"
        )
    xmx_arguments = [value for value in command if value.startswith("-Xmx")]
    if xmx_arguments != ["-Xmx512m"]:
        raise PolicyRejection(
            "test worker must retain exactly one -Xmx512m and no other -Xmx: "
            f"actual={xmx_arguments}"
        )
    if any(value.startswith("-Xms") for value in command):
        raise PolicyRejection("test worker must not introduce -Xms")
    mod_folder_arguments = [
        value for value in command if value.startswith("-Dfml.modFolders=")
    ]
    if len(mod_folder_arguments) != 1:
        raise PolicyRejection(
            "test worker must expose exactly one project mod-folders classpath"
        )
    mod_folders = mod_folder_arguments[0].split("=", 1)[1].split(os.pathsep)
    folder_paths: list[Path] = []
    for entry in mod_folders:
        if "%%" not in entry:
            raise PolicyRejection("test-worker mod-folders entry is malformed")
        label, path_text = entry.split("%%", 1)
        if label != MOD_FOLDER_LABEL:
            raise PolicyRejection(
                f"test-worker mod-folders label differs: expected="
                f"{MOD_FOLDER_LABEL} actual={label}"
            )
        path = Path(path_text)
        if not path.is_absolute() or Path(os.path.abspath(path_text)) != path:
            raise PolicyRejection(
                f"test-worker mod-folders path is non-canonical: {path_text}"
            )
        try:
            common = Path(os.path.commonpath((os.fspath(project_root), path_text)))
        except ValueError as error:
            raise PolicyRejection(
                f"test-worker mod-folders path escapes the worktree: {path_text}"
            ) from error
        if common != project_root:
            raise PolicyRejection(
                f"test-worker mod-folders path belongs to another worktree: {path_text}"
            )
        folder_paths.append(path)
    expected_folder_paths = [
        project_root / relative for relative in MOD_FOLDER_RELATIVE_PATHS
    ]
    if folder_paths != expected_folder_paths:
        raise PolicyRejection(
            "test-worker mod-folders inventory differs: "
            f"expected={[os.fspath(path) for path in expected_folder_paths]} "
            f"actual={[os.fspath(path) for path in folder_paths]}"
        )
    if len(set(folder_paths)) != len(folder_paths):
        raise PolicyRejection("test-worker mod-folders inventory contains duplicates")
    return {
        "raw_line": int(launch["line"]),
        "worker": expected_worker,
        "worker_id": str(launch["worker"]),
        "working_directory": os.fspath(expected_working),
        "java": os.fspath(expected_java),
        "heap": "-Xmx512m",
        "xms_count": 0,
        "mod_folder_count": len(folder_paths),
        "command_sha256": _sha256(str(launch["command"]).encode("utf-8")),
    }


def _require_fresh_test_execution(
    parsed: dict[str, Any],
    context: dict[str, Any],
    project_root: Path,
) -> dict[str, Any]:
    task = _observe_task(parsed, ":test")
    if task["state"] != "EXECUTED":
        raise PolicyRejection(
            f"fresh product test execution is absent: state={task['state']}"
        )
    markers = parsed["markers"]
    worker_launch = _validate_worker_launch(parsed, context, project_root)
    worker_id = str(worker_launch["worker_id"])
    for key in (
        "worker_started_executing",
        "worker_started",
        "worker_passed",
        "worker_finished",
    ):
        if markers[key] != [worker_id]:
            raise PolicyRejection(
                f"fresh test marker differs: {key} expected={[worker_id]} "
                f"actual={markers[key]}"
            )
    if markers["test_run_passed"] != 1:
        raise PolicyRejection(
            "fresh test marker differs: test_run_passed expected=1 "
            f"actual={markers['test_run_passed']}"
        )
    if markers["test_failure_lines"]:
        raise PolicyRejection(
            f"unit log contains failed test events at lines "
            f"{markers['test_failure_lines']}"
        )
    return {
        **task,
        "worker": worker_launch["worker"],
        "worker_started_executing": 1,
        "worker_started": 1,
        "worker_passed": 1,
        "worker_finished": 1,
        "test_run_passed": 1,
        "worker_launch": worker_launch,
    }


def _build_unit_result(
    context: dict[str, Any],
    raw: dict[str, Any],
    parsed: dict[str, Any],
    outputs: dict[str, Any],
    source_files: dict[str, Any],
    proof: dict[str, Any],
    proof_record: dict[str, Any],
    project_root: Path,
) -> dict[str, Any]:
    _require_build_success(parsed)
    if parsed["compiler_issues"]:
        raise PolicyRejection(str(parsed["compiler_issues"][0]))
    if raw["path"] == proof["raw"]["path"]:
        raise PolicyRejection("unit raw must not alias the compile producer raw")
    if outputs != proof["outputs"]:
        raise PolicyRejection(
            "main/test class outputs differ from the accepted same-campaign compile proof"
        )
    roles = {
        role: _unit_role(role, parsed, proof["roles"][role])
        for role in ROLE_SPECS
    }
    test = _require_fresh_test_execution(parsed, context, project_root)
    return {
        "schema": SCHEMA,
        "mode": "unit",
        "status": "PASS",
        "marker": PASS_MARKER,
        "policy": "TASK_AWARE_LATER_ATTRIBUTION_WITH_FRESH_TEST_EXECUTION",
        "context": context,
        "raw": raw,
        "compile_proof": proof_record,
        "authorized_warning_sources": source_files,
        "roles": roles,
        "test": test,
        "outputs": outputs,
        "build_tool_diagnostics": parsed["build_tool_diagnostics"],
        "noncompiler_warning_observations": parsed[
            "noncompiler_warning_observations"
        ],
    }


def _canonical_result_path(value: str | Path) -> Path:
    path = Path(value)
    if not path.is_absolute():
        raise InputFailure(f"result path must be absolute: {path}")
    parent = _canonical_existing_dir(path.parent, "result parent")
    expected = parent / path.name
    if Path(os.path.abspath(os.fspath(path))) != expected:
        raise InputFailure(f"result path is not canonical: {path}")
    if path.exists() or path.is_symlink():
        raise InputFailure(f"result path already exists: {path}")
    return expected


def _write_exclusive(path: Path, value: dict[str, Any]) -> None:
    data = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        indent=2,
    ).encode("utf-8") + b"\n"
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor: int | None = None
    try:
        descriptor = os.open(path, flags, 0o644)
        offset = 0
        while offset < len(data):
            offset += os.write(descriptor, data[offset:])
        os.fsync(descriptor)
    except OSError as error:
        raise InputFailure(f"could not write exclusive result {path}: {error}") from error
    finally:
        if descriptor is not None:
            os.close(descriptor)


def _failure_result(
    mode: str,
    kind: str,
    detail: str,
    context: dict[str, Any] | None,
    raw: dict[str, Any] | None,
) -> dict[str, Any]:
    result: dict[str, Any] = {
        "schema": SCHEMA,
        "mode": mode,
        "status": "FAIL",
        "marker": None,
        "failure_kind": kind,
        "failures": [detail],
    }
    if context is not None:
        result["context"] = context
    if raw is not None:
        result["raw"] = raw
    return result


def _load_compile_proof(
    path_value: str | Path,
    context: dict[str, Any],
    project_root: Path,
) -> tuple[dict[str, Any], dict[str, Any]]:
    try:
        resolved, data, info = _read_regular(
            path_value,
            "compile proof",
            MAX_JSON_BYTES,
        )
        proof = _json_no_duplicates(data, "compile proof")
    except InputFailure as error:
        raise PolicyRejection(f"compile proof is missing/stale/invalid: {error}") from error
    proof = _validate_compile_proof_structure(proof, context, project_root)
    return proof, {
        "path": os.fspath(resolved),
        "mode": f"{stat.S_IMODE(info.st_mode):04o}",
        "bytes": len(data),
        "sha256": _sha256(data),
        "producer_raw_sha256": proof["raw"]["sha256"],
    }


def _run_admission(arguments: argparse.Namespace) -> int:
    mode = arguments.mode
    context: dict[str, Any] | None = None
    raw_record: dict[str, Any] | None = None
    try:
        result_path = _canonical_result_path(arguments.result)
    except InputFailure as error:
        print(f"TOOLING_INPUT_ERROR: {error}", file=sys.stderr)
        return 2
    try:
        context, project_root = _build_context(arguments)
        source_files = _verify_authorized_source_lines(project_root)
        raw_record, raw_text = _raw_record(arguments.raw_log, f"{mode} raw log")
        parsed = _parse_log(raw_text, project_root)
        outputs = {
            "MAIN_COMPILE": _collect_output_manifest(
                project_root, arguments.main_output, "MAIN_COMPILE"
            ),
            "TEST_COMPILE": _collect_output_manifest(
                project_root, arguments.test_output, "TEST_COMPILE"
            ),
        }
        if mode == "compile":
            result = _build_compile_result(
                context, raw_record, parsed, outputs, source_files
            )
        else:
            proof, proof_record = _load_compile_proof(
                arguments.compile_proof, context, project_root
            )
            result = _build_unit_result(
                context,
                raw_record,
                parsed,
                outputs,
                source_files,
                proof,
                proof_record,
                project_root,
            )
        exit_code = 0
    except PolicyRejection as error:
        result = _failure_result(
            mode,
            "POLICY_REJECTION",
            str(error),
            context,
            raw_record,
        )
        exit_code = 3
    except InputFailure as error:
        result = _failure_result(
            mode,
            "TOOLING_INPUT_ERROR",
            str(error),
            context,
            raw_record,
        )
        exit_code = 2
    except Exception as error:  # Defensive closure: never turn an exception into PASS.
        result = _failure_result(
            mode,
            "TOOLING_INPUT_ERROR",
            f"unexpected {type(error).__name__}: {error}",
            context,
            raw_record,
        )
        exit_code = 2
    try:
        _write_exclusive(result_path, result)
    except InputFailure as error:
        print(f"TOOLING_INPUT_ERROR: {error}", file=sys.stderr)
        return 2
    if exit_code == 0:
        print(f"{PASS_MARKER} mode={mode} result={result_path}")
    else:
        print(
            f"{result['failure_kind']}: {result['failures'][0]} result={result_path}",
            file=sys.stderr,
        )
    return exit_code


def _self_test_project(root: Path) -> tuple[argparse.Namespace, dict[str, Any], Path]:
    files: dict[str, bytes] = {
        "build.gradle": b"plugins { id 'java' }\n",
        "settings.gradle": b"rootProject.name = 'wc1-self-test'\n",
        "gradle.properties": b"org.gradle.caching=true\n",
        "gradlew": b"#!/bin/sh\nexit 99\n",
        "gradlew.bat": b"@exit /b 99\r\n",
        ".github/workflows/build.yml": b"name: self-test\n",
        "scripts/run-p9-s3-rd1-unit-test-diagnostics.sh": b"#!/bin/sh\nexit 99\n",
        "gradle/wrapper/gradle-wrapper.jar": b"self-test-wrapper\n",
        "gradle/wrapper/gradle-wrapper.properties": (
            b"distributionUrl=https\\://services.gradle.org/distributions/"
            b"gradle-9.2.1-bin.zip\n"
            b"distributionSha256Sum="
            b"72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f\n"
        ),
        "gradle/p9-s3-rd1-test-diagnostics.init.gradle": b"// self-test\n",
        MAIN_SOURCE: (
            ("// filler\n" * 16)
            + "        bus = EventBusSubscriber.Bus.MOD)\n"
        ).encode("utf-8"),
        TEST_SOURCE: (
            ("// filler\n" * 44)
            + "        assertEquals(EventBusSubscriber.Bus.MOD, subscriber.bus());\n"
        ).encode("utf-8"),
        "src/main/java/example/Other.java": b"package example; class Other {}\n",
        "src/test/java/example/OtherTest.java": b"package example; class OtherTest {}\n",
        "build/classes/java/main/example/Other.class": b"main-class\n",
        "build/classes/java/test/example/OtherTest.class": b"test-class\n",
    }
    for relative, data in files.items():
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    os.chmod(root / "gradlew", 0o755)
    os.chmod(root / "scripts/run-p9-s3-rd1-unit-test-diagnostics.sh", 0o755)
    java_home = root / "self-test-jdk"
    (java_home / "bin").mkdir(parents=True)
    (java_home / "bin/java").write_bytes(b"self-test-java\n")
    (java_home / "bin/javac").write_bytes(b"self-test-javac\n")
    os.chmod(java_home / "bin/java", 0o755)
    os.chmod(java_home / "bin/javac", 0o755)
    (java_home / "release").write_text(
        'JAVA_VERSION="21.0.8"\n'
        'JAVA_RUNTIME_VERSION="21.0.8+9-LTS"\n'
        'IMPLEMENTOR="WC1 self-test"\n'
        'OS_ARCH="fixture"\n',
        encoding="utf-8",
    )
    arguments = argparse.Namespace(
        campaign=CAMPAIGN,
        source_identity="freeze:" + "a" * 64,
        commit="b" * 40,
        tree="c" * 40,
        run_id="self-test-run",
        attempt=1,
        job="self-test-job",
        project_root=os.fspath(root),
        java_home=os.fspath(java_home),
        main_output=os.fspath(root / "build/classes/java/main"),
        test_output=os.fspath(root / "build/classes/java/test"),
    )
    context, project_root = _build_context(
        arguments,
        verify_git=False,
        enforce_tool_location=False,
    )
    return arguments, context, project_root


def _warning_line(root: Path, role: str, symbol: str, category: str = "removal") -> str:
    spec = ROLE_SPECS[role]
    return (
        f"{root / str(spec['source'])}:{spec['line']}: warning: [{category}] "
        f"{symbol} has been deprecated and marked for removal"
    )


def _self_compile_log(root: Path) -> str:
    lines = ["> Task :compileJava"]
    lines.extend(_warning_line(root, "MAIN_COMPILE", symbol) for symbol in ROLE_SPECS["MAIN_COMPILE"]["symbols"])
    lines.extend(("2 warnings", "> Task :compileTestJava"))
    lines.extend(_warning_line(root, "TEST_COMPILE", symbol) for symbol in ROLE_SPECS["TEST_COMPILE"]["symbols"])
    lines.extend(("2 warnings", "BUILD SUCCESSFUL in 1s"))
    return "\n".join(lines) + "\n"


def _self_unit_log(
    root: Path,
    java_home: Path,
    main_state: str = "UP-TO-DATE",
    test_state: str = "FROM-CACHE",
    product_state: str = "EXECUTED",
    worker_id: str = "7",
) -> str:
    product_suffix = "" if product_state == "EXECUTED" else f" {product_state}"
    worker = f"Gradle Test Executor {worker_id}"
    mod_folders = os.pathsep.join(
        f"{MOD_FOLDER_LABEL}%%{root / relative}"
        for relative in MOD_FOLDER_RELATIVE_PATHS
    )
    lines = [
        f"> Task :compileJava {main_state}",
        f"> Task :compileTestJava {test_state}",
        f"> Task :test{product_suffix}",
        f"{worker} started executing tests.",
        (
            f"Starting process '{worker}'. Working directory: "
            f"{root / 'build/minecraft-junit'} Command: {java_home / 'bin/java'} "
            f"-Dfml.modFolders={mod_folders} "
            "-Xmx512m worker.org.gradle.process.internal.worker.GradleWorkerMain "
            f"'{worker}'"
        ),
        f"Gradle Test Run :test > {worker} STARTED",
        f"Gradle Test Run :test > {worker} > ExampleTest > works() STARTED",
        f"Gradle Test Run :test > {worker} > ExampleTest > works() PASSED",
        f"Gradle Test Run :test > {worker} PASSED",
        f"{worker} finished executing tests.",
        f"> Task :test{product_suffix}",
        "Gradle Test Run :test PASSED",
        "BUILD SUCCESSFUL in 1s",
    ]
    return "\n".join(lines) + "\n"


def _expect_rejection(label: str, operation: Callable[[], Any]) -> None:
    try:
        operation()
    except PolicyRejection:
        return
    raise AssertionError(f"{label} unexpectedly passed")


def _run_self_test() -> int:
    completed: list[str] = []
    try:
        with tempfile.TemporaryDirectory(prefix="p9-s4-wc1-warning-self-test-") as value:
            root = Path(value).resolve()
            arguments, context, project_root = _self_test_project(root)
            source_files = _verify_authorized_source_lines(project_root)
            outputs = {
                role: _collect_output_manifest(
                    project_root,
                    getattr(arguments, "main_output" if role == "MAIN_COMPILE" else "test_output"),
                    role,
                )
                for role in ROLE_SPECS
            }

            compile_text = _self_compile_log(project_root)
            compile_raw_path = project_root / "compile.raw.log"
            compile_raw_path.write_text(compile_text, encoding="utf-8")
            compile_raw, _ = _raw_record(compile_raw_path, "self-test compile raw")
            compile_parsed = _parse_log(compile_text, project_root)
            proof = _build_compile_result(
                context, compile_raw, compile_parsed, outputs, source_files
            )
            completed.append("1-direct-both-roles")

            proof_path = project_root / "compile-proof.json"
            proof_path.write_bytes(
                json.dumps(proof, sort_keys=True, indent=2).encode("utf-8") + b"\n"
            )
            loaded_proof, _ = _load_compile_proof(proof_path, context, project_root)
            unit_text = _self_unit_log(
                project_root, Path(str(context["java"]["home"]))
            )
            fixture_warning = (
                f"{project_root / 'fixture/DeliberateWarning.java'}:9: "
                "warning: [fixture] deliberately printed by the product test"
            )
            unit_text = unit_text.replace(
                "Gradle Test Executor 7 started executing tests.\n",
                fixture_warning
                + "\nfixture deprecation message from the product test"
                + "\nDeprecated Gradle features were used in this build, making "
                "it incompatible with Gradle 10."
                + "\nGradle Test Executor 7 started executing tests.\n",
                1,
            )
            unit_parsed = _parse_log(unit_text, project_root)
            fixture_observations = [
                value
                for value in unit_parsed["noncompiler_warning_observations"]
                if value.get("kind") == "structured-warning-outside-compiler"
            ]
            if len(fixture_observations) != 1:
                raise AssertionError(
                    "test fixture warning was not classified outside compiler scope"
                )
            unclassified_deprecations = [
                value
                for value in unit_parsed["noncompiler_warning_observations"]
                if value.get("kind")
                == "unclassified-deprecation-outside-compiler"
            ]
            if len(unclassified_deprecations) != 1:
                raise AssertionError(
                    "test fixture deprecation was not kept outside Gradle scope"
                )
            if len(unit_parsed["build_tool_diagnostics"]) != 1:
                raise AssertionError(
                    "known Gradle deprecation was not classified as build-tool text"
                )
            unit_raw_path = project_root / "unit.raw.log"
            unit_raw_path.write_text(unit_text, encoding="utf-8")
            unit_raw, _ = _raw_record(unit_raw_path, "self-test unit raw")
            _build_unit_result(
                context,
                unit_raw,
                unit_parsed,
                outputs,
                source_files,
                loaded_proof,
                _external_file_record(proof_path, "self-test proof", MAX_JSON_BYTES),
                project_root,
            )
            completed.append("2-cached-attribution")

            cached_test_proof = copy.deepcopy(proof)
            cached_test_proof["roles"]["TEST_COMPILE"]["state"] = "FROM-CACHE"
            _expect_rejection(
                "3-test-cache-without-direct-proof",
                lambda: _validate_compile_proof_structure(
                    cached_test_proof, context, project_root
                ),
            )
            completed.append("3-test-cache-without-direct-proof")

            unknown_lines = _self_compile_log(project_root).splitlines()
            unknown_lines[2] = _warning_line(
                project_root,
                "MAIN_COMPILE",
                "unchecked conversion",
                "unchecked",
            )
            _expect_rejection(
                "4-unknown-warning",
                lambda: _compile_roles(
                    _parse_log("\n".join(unknown_lines) + "\n", project_root)
                ),
            )
            raw_unchecked = _self_compile_log(project_root).replace(
                "2 warnings\n> Task :compileTestJava",
                "Note: Some input files use unchecked or unsafe operations.\n"
                "2 warnings\n> Task :compileTestJava",
                1,
            )
            _expect_rejection(
                "4-raw-unchecked-note",
                lambda: _compile_roles(_parse_log(raw_unchecked, project_root)),
            )
            raw_deprecation = _self_compile_log(project_root).replace(
                "2 warnings\n> Task :compileTestJava",
                "Note: Some input files use or override a deprecated API.\n"
                "2 warnings\n> Task :compileTestJava",
                1,
            )
            _expect_rejection(
                "4-raw-deprecation-note",
                lambda: _compile_roles(_parse_log(raw_deprecation, project_root)),
            )
            raw_preview = _self_compile_log(project_root).replace(
                "2 warnings\n> Task :compileTestJava",
                "Note: Some input files use preview features of Java SE 21.\n"
                "2 warnings\n> Task :compileTestJava",
                1,
            )
            _expect_rejection(
                "4-raw-preview-note",
                lambda: _compile_roles(_parse_log(raw_preview, project_root)),
            )
            ambiguous_deprecation = _self_compile_log(project_root).replace(
                "2 warnings\n> Task :compileTestJava",
                "Annotation processor API has been deprecated.\n"
                "2 warnings\n> Task :compileTestJava",
                1,
            )
            _expect_rejection(
                "4-unclassified-deprecation",
                lambda: _compile_roles(
                    _parse_log(ambiguous_deprecation, project_root)
                ),
            )
            auxiliary_compile_warning = _self_compile_log(project_root).replace(
                "> Task :compileTestJava",
                "> Task :compileP4A3ProbeJava\n"
                f"{project_root / 'fixture/Probe.java'}:3: "
                "warning: [unchecked] auxiliary compiler warning\n"
                "1 warning\n"
                "> Task :compileTestJava",
                1,
            )
            _expect_rejection(
                "4-auxiliary-compile-warning",
                lambda: _compile_roles(
                    _parse_log(auxiliary_compile_warning, project_root)
                ),
            )
            completed.append("4-unknown-warning")

            stale_context = copy.deepcopy(context)
            stale_context["source_identity"] = "freeze:" + "d" * 64
            stale_context["context_sha256"] = _canonical_digest(
                {key: value for key, value in stale_context.items() if key != "context_sha256"}
            )
            _expect_rejection(
                "5-stale-source-proof",
                lambda: _validate_compile_proof_structure(
                    proof, stale_context, project_root
                ),
            )
            _expect_rejection(
                "5-missing-proof",
                lambda: _load_compile_proof(
                    project_root / "missing-compile-proof.json",
                    context,
                    project_root,
                ),
            )
            stale_raw_path = project_root / "stale-compile.raw.log"
            stale_raw_path.write_text(compile_text, encoding="utf-8")
            stale_raw_proof = copy.deepcopy(proof)
            stale_raw_proof["raw"], _ = _raw_record(
                stale_raw_path, "self-test stale raw baseline"
            )
            stale_raw_path.write_text(
                compile_text + "post-proof mutation\n", encoding="utf-8"
            )
            _expect_rejection(
                "5-stale-raw-proof",
                lambda: _validate_compile_proof_structure(
                    stale_raw_proof, context, project_root
                ),
            )
            foreign_toolchain_context = copy.deepcopy(context)
            foreign_toolchain_context["java"]["java_version"] = "21-foreign"
            foreign_toolchain_context["context_sha256"] = _canonical_digest(
                {
                    key: value
                    for key, value in foreign_toolchain_context.items()
                    if key != "context_sha256"
                }
            )
            _expect_rejection(
                "5-foreign-toolchain-proof",
                lambda: _validate_compile_proof_structure(
                    proof, foreign_toolchain_context, project_root
                ),
            )
            completed.append("5-stale-source-proof")

            missing_test_proof = copy.deepcopy(proof)
            del missing_test_proof["roles"]["TEST_COMPILE"]
            _expect_rejection(
                "6-main-cannot-substitute-test",
                lambda: _validate_compile_proof_structure(
                    missing_test_proof, context, project_root
                ),
            )
            completed.append("6-main-cannot-substitute-test")

            cached_test_execution = _parse_log(
                _self_unit_log(
                    project_root,
                    Path(str(context["java"]["home"])),
                    product_state="FROM-CACHE",
                ),
                project_root,
            )
            _expect_rejection(
                "7-product-test-cache-rejected",
                lambda: _require_fresh_test_execution(
                    cached_test_execution, context, project_root
                ),
            )
            up_to_date_test_execution = _parse_log(
                _self_unit_log(
                    project_root,
                    Path(str(context["java"]["home"])),
                    product_state="UP-TO-DATE",
                ),
                project_root,
            )
            _expect_rejection(
                "7-product-test-up-to-date-rejected",
                lambda: _require_fresh_test_execution(
                    up_to_date_test_execution, context, project_root
                ),
            )
            completed.append("7-product-test-cache-rejected")

            bad_summary = _self_compile_log(project_root).replace(
                "2 warnings\n> Task :compileTestJava", "1 warning\n> Task :compileTestJava"
            )
            _expect_rejection(
                "8-summary-arithmetic",
                lambda: _compile_roles(_parse_log(bad_summary, project_root)),
            )
            split_summary = _self_compile_log(project_root).replace(
                "2 warnings\n> Task :compileTestJava",
                "1 warning\n1 warning\n> Task :compileTestJava",
                1,
            )
            _expect_rejection(
                "8-summary-line-count-with-same-total",
                lambda: _compile_roles(_parse_log(split_summary, project_root)),
            )
            completed.append("8-summary-arithmetic")

            changed = project_root / "build/classes/java/test/example/OtherTest.class"
            changed.write_bytes(b"changed-test-class\n")
            changed_outputs = {
                "MAIN_COMPILE": _collect_output_manifest(
                    project_root, arguments.main_output, "MAIN_COMPILE"
                ),
                "TEST_COMPILE": _collect_output_manifest(
                    project_root, arguments.test_output, "TEST_COMPILE"
                ),
            }
            _expect_rejection(
                "9-changed-output",
                lambda: _build_unit_result(
                    context,
                    unit_raw,
                    unit_parsed,
                    changed_outputs,
                    source_files,
                    proof,
                    _external_file_record(
                        proof_path, "self-test proof", MAX_JSON_BYTES
                    ),
                    project_root,
                ),
            )
            foreign_unit_text = unit_text.replace(
                os.fspath(project_root / "build/resources/test"),
                os.fspath(project_root.parent / "foreign/build/resources/test"),
                1,
            )
            _expect_rejection(
                "9-wrong-worktree-classpath",
                lambda: _build_unit_result(
                    context,
                    unit_raw,
                    _parse_log(foreign_unit_text, project_root),
                    outputs,
                    source_files,
                    proof,
                    _external_file_record(
                        proof_path, "self-test proof", MAX_JSON_BYTES
                    ),
                    project_root,
                ),
            )
            extra_heap_unit_text = unit_text.replace(
                "-Xmx512m worker.org.gradle.process.internal.worker.GradleWorkerMain",
                "-Xmx512m -Xmx2g "
                "worker.org.gradle.process.internal.worker.GradleWorkerMain",
                1,
            )
            _expect_rejection(
                "9-extra-worker-heap",
                lambda: _build_unit_result(
                    context,
                    unit_raw,
                    _parse_log(extra_heap_unit_text, project_root),
                    outputs,
                    source_files,
                    proof,
                    _external_file_record(
                        proof_path, "self-test proof", MAX_JSON_BYTES
                    ),
                    project_root,
                ),
            )
            wrong_terminal_worker = unit_text.replace(
                "-Xmx512m worker.org.gradle.process.internal.worker.GradleWorkerMain "
                "'Gradle Test Executor 7'",
                "-Xmx512m 'Gradle Test Executor 7' "
                "worker.org.gradle.process.internal.worker.GradleWorkerMain "
                "'Gradle Test Executor 8'",
                1,
            )
            _expect_rejection(
                "9-wrong-terminal-worker",
                lambda: _build_unit_result(
                    context,
                    unit_raw,
                    _parse_log(wrong_terminal_worker, project_root),
                    outputs,
                    source_files,
                    proof,
                    _external_file_record(
                        proof_path, "self-test proof", MAX_JSON_BYTES
                    ),
                    project_root,
                ),
            )
            completed.append("9-changed-output")
    except Exception as error:
        print(
            f"P9_S4_WARNING_ATTRIBUTION_SELF_TEST_FAIL "
            f"completed={len(completed)} detail={type(error).__name__}: {error}",
            file=sys.stderr,
        )
        return 2
    if len(completed) != 9:
        print(
            f"P9_S4_WARNING_ATTRIBUTION_SELF_TEST_FAIL matrix={len(completed)}",
            file=sys.stderr,
        )
        return 2
    print(SELF_TEST_MARKER)
    return 0


def _positive_integer(value: str) -> int:
    try:
        number = int(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("must be an integer") from error
    if number < 1:
        raise argparse.ArgumentTypeError("must be positive")
    return number


def _add_common_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--project-root", required=True)
    parser.add_argument("--raw-log", required=True)
    parser.add_argument("--result", required=True)
    parser.add_argument("--campaign", required=True)
    parser.add_argument("--source-identity", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--tree", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--attempt", required=True, type=_positive_integer)
    parser.add_argument("--job", required=True)
    parser.add_argument("--java-home", required=True)
    parser.add_argument("--main-output", required=True)
    parser.add_argument("--test-output", required=True)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="P9-S4 WC1 task-aware compiler-warning attribution"
    )
    commands = parser.add_subparsers(dest="mode", required=True)
    compile_parser = commands.add_parser(
        "compile", help="admit direct compileJava + compileTestJava evidence"
    )
    _add_common_arguments(compile_parser)
    unit_parser = commands.add_parser(
        "unit", help="admit cached/reexecuted compiler tasks plus fresh :test"
    )
    _add_common_arguments(unit_parser)
    unit_parser.add_argument("--compile-proof", required=True)
    commands.add_parser(
        "self-test", help="run the exact nine-control WC1 acceptance matrix"
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    arguments = _parser().parse_args(argv)
    if arguments.mode == "self-test":
        return _run_self_test()
    return _run_admission(arguments)


if __name__ == "__main__":
    raise SystemExit(main())
