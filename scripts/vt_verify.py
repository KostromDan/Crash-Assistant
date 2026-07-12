#!/usr/bin/env python3
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import logging
import mimetypes
import os
import re
import shutil
import time
import uuid
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

VT_API_BASE = "https://www.virustotal.com/api/v3"
MODRINTH_API_BASE = "https://api.modrinth.com/v2"
MODRINTH_PROJECT_ID_OR_SLUG = "crash-assistant"
MODRINTH_USER_AGENT = "CrashAssistant-VT-Verify/1.0"

API_KEY_ENV_VAR = "VIRUS_TOTAL_API_KEY"
SCAN_DIRECTORY = Path(__file__).resolve().parent
PROJECT_ROOT_DIRECTORY = SCAN_DIRECTORY.parent
ROOT_BUILD_GRADLE = PROJECT_ROOT_DIRECTORY / "build.gradle"
ROOT_GRADLE_PROPERTIES = PROJECT_ROOT_DIRECTORY / "gradle.properties"
TEMP_DIRECTORY_ROOT = SCAN_DIRECTORY / "tmp"
FILE_EXTENSIONS: set[str] | None = {".jar"}
MAX_FILES_TO_SCAN: int | None = None

MAX_DOWNLOAD_WORKERS = 8
MAX_UPLOAD_WORKERS = 5
MAX_POLL_WORKERS = 5
POLL_INTERVAL_SECONDS = 5
RESULTS_WAIT_TIMEOUT_SECONDS: int | None = 1800

BLACKLIST_REASONS: set[str] = set()
BLACKLIST_REASONS_ENV_VAR = "VT_WARNING_BLACKLIST"
DETECTION_DETAIL_BLOCKLIST: set[str] = {
    "Panda [malicious] -> Vulnerability/Log4J",
}

# --------------------------------------------------------------------------------------------------------------
# This script verifies newly published Crash Assistant artifacts in VirusTotal.
# We had rare false-positive incidents (including Bitdefender twice in one month on different files),
# which caused confusion and support issues.
# By scanning release files immediately after publishing, we can react fast and prevent user confusion.
# --------------------------------------------------------------------------------------------------------------
LOG_FILE = SCAN_DIRECTORY / "vt_verify.log"


class VirusTotalError(RuntimeError):
    pass


def setup_logger() -> logging.Logger:
    logger = logging.getLogger("vt_verify")
    logger.setLevel(logging.INFO)

    if logger.handlers:
        return logger

    formatter = logging.Formatter("%(asctime)s | %(levelname)s | %(message)s")

    file_handler = logging.FileHandler(LOG_FILE, encoding="utf-8")
    file_handler.setFormatter(formatter)
    logger.addHandler(file_handler)

    stream_handler = logging.StreamHandler()
    stream_handler.setFormatter(formatter)
    logger.addHandler(stream_handler)

    return logger


def build_headers(api_key: str) -> dict[str, str]:
    return {"x-apikey": api_key}


def http_json(
    method: str,
    url: str,
    api_key: str,
    logger: logging.Logger,
    body: bytes | None = None,
    extra_headers: dict[str, str] | None = None,
) -> dict[str, Any]:
    headers = build_headers(api_key)
    if extra_headers:
        headers.update(extra_headers)

    request = Request(url=url, data=body, headers=headers, method=method)
    logger.info("HTTP %s %s", method, url)
    try:
        with urlopen(request) as response:
            payload = json.loads(response.read().decode("utf-8"))
            analysis_id = payload.get("data", {}).get("id")
            status = payload.get("data", {}).get("attributes", {}).get("status")
            logger.info(
                "HTTP RESPONSE %s %s | code=%s | analysis_id=%s | status=%s",
                method,
                url,
                getattr(response, "status", "unknown"),
                analysis_id,
                status,
            )
            return payload
    except HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        error_code = "unknown"
        error_message = details.strip()
        try:
            parsed = json.loads(details)
            error_obj = parsed.get("error", {}) if isinstance(parsed, dict) else {}
            error_code = str(error_obj.get("code", "unknown"))
            error_message = str(error_obj.get("message", error_message))
        except json.JSONDecodeError:
            pass

        logger.error(
            "HTTP ERROR %s %s | code=%s | vt_code=%s | vt_message=%s",
            method,
            url,
            exc.code,
            error_code,
            error_message,
        )
        raise VirusTotalError(
            f"HTTP {exc.code} {url} | vt_code={error_code} | vt_message={error_message}"
        ) from exc
    except URLError as exc:
        logger.error("NETWORK ERROR %s %s | error=%s", method, url, exc)
        raise VirusTotalError(f"Network error for {url}: {exc}") from exc


def http_json_public(
    method: str,
    url: str,
    logger: logging.Logger,
    body: bytes | None = None,
    extra_headers: dict[str, str] | None = None,
) -> Any:
    headers = {"User-Agent": MODRINTH_USER_AGENT}
    if extra_headers:
        headers.update(extra_headers)

    request = Request(url=url, data=body, headers=headers, method=method)
    logger.info("HTTP %s %s", method, url)
    try:
        with urlopen(request) as response:
            payload = json.loads(response.read().decode("utf-8"))
            logger.info(
                "HTTP RESPONSE %s %s | code=%s",
                method,
                url,
                getattr(response, "status", "unknown"),
            )
            return payload
    except HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        logger.error(
            "HTTP ERROR %s %s | code=%s | message=%s",
            method,
            url,
            exc.code,
            details.strip(),
        )
        raise VirusTotalError(f"HTTP {exc.code} {url} | {details.strip()}") from exc
    except URLError as exc:
        logger.error("NETWORK ERROR %s %s | error=%s", method, url, exc)
        raise VirusTotalError(f"Network error for {url}: {exc}") from exc


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def create_multipart_payload(file_path: Path) -> tuple[bytes, str]:
    boundary = f"----VTBoundary{uuid.uuid4().hex}"
    content_type = mimetypes.guess_type(file_path.name)[0] or "application/octet-stream"

    with file_path.open("rb") as file_handle:
        file_bytes = file_handle.read()

    parts: list[bytes] = []
    parts.append(f"--{boundary}\r\n".encode())
    parts.append(
        (
            f'Content-Disposition: form-data; name="file"; filename="{file_path.name}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n"
        ).encode()
    )
    parts.append(file_bytes)
    parts.append("\r\n".encode())
    parts.append(f"--{boundary}--\r\n".encode())

    return b"".join(parts), boundary


def upload_file_for_analysis(file_path: Path, api_key: str, logger: logging.Logger) -> dict[str, Any]:
    body, boundary = create_multipart_payload(file_path)
    result = http_json(
        method="POST",
        url=f"{VT_API_BASE}/files",
        api_key=api_key,
        logger=logger,
        body=body,
        extra_headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )

    analysis_id = result.get("data", {}).get("id")
    if not analysis_id:
        raise VirusTotalError(f"Missing analysis id in upload response for file '{file_path.name}'")

    return {
        "file": str(file_path),
        "sha256": sha256_file(file_path),
        "analysis_id": str(analysis_id),
    }


def fetch_analysis_once(analysis_id: str, api_key: str, logger: logging.Logger) -> dict[str, Any]:
    return http_json(
        method="GET",
        url=f"{VT_API_BASE}/analyses/{analysis_id}",
        api_key=api_key,
        logger=logger,
    )


def parse_blacklist(raw_values: list[str]) -> set[str]:
    values: set[str] = set()
    for raw in raw_values:
        for piece in raw.split(","):
            normalized = piece.strip().lower()
            if normalized:
                values.add(normalized)
    return values


def build_reason_blacklist() -> set[str]:
    env_raw = os.getenv(BLACKLIST_REASONS_ENV_VAR, "")
    merged = set(BLACKLIST_REASONS)
    if env_raw.strip():
        merged.update(parse_blacklist([env_raw]))
    return {item.strip().lower() for item in merged if item.strip()}


def list_candidate_files(directory: Path, extensions: set[str] | None) -> list[Path]:
    files = [path for path in sorted(directory.iterdir()) if path.is_file()]
    if not extensions:
        return files

    normalized = {ext.lower() for ext in extensions}
    return [path for path in files if path.suffix.lower() in normalized]


def read_project_version(logger: logging.Logger) -> str:
    if ROOT_BUILD_GRADLE.exists():
        content = ROOT_BUILD_GRADLE.read_text(encoding="utf-8")
        match = re.search(r"(?m)^\s*version\s*=\s*['\"]([^'\"]+)['\"]", content)
        if match and match.group(1).strip():
            value = match.group(1).strip()
            logger.info("Resolved project version from build.gradle: %s", value)
            return value

    if ROOT_GRADLE_PROPERTIES.exists():
        for line in ROOT_GRADLE_PROPERTIES.read_text(encoding="utf-8").splitlines():
            raw = line.strip()
            if not raw or raw.startswith("#"):
                continue
            if raw.startswith("version="):
                value = raw.split("=", 1)[1].strip()
                if value:
                    logger.info("Resolved project version from gradle.properties: %s", value)
                    return value

    raise VirusTotalError(
        f"Failed to resolve project version from '{ROOT_BUILD_GRADLE}' "
        f"or '{ROOT_GRADLE_PROPERTIES}'"
    )


def normalize_version_string(value: str) -> str:
    return value.strip().lower().lstrip("v")


def is_matching_release_version(release: dict[str, Any], project_version: str) -> bool:
    target = normalize_version_string(project_version)
    version_number = normalize_version_string(str(release.get("version_number", "")))
    version_name = normalize_version_string(str(release.get("name", "")))
    candidates = {version_number, version_name}
    candidates.discard("")
    return target in candidates


def safe_filename(name: str) -> str:
    sanitized = re.sub(r"[^A-Za-z0-9._-]", "_", name.strip())
    return sanitized or f"artifact_{uuid.uuid4().hex}.jar"


def download_file(url: str, target_path: Path, logger: logging.Logger) -> None:
    request = Request(url=url, headers={"User-Agent": MODRINTH_USER_AGENT}, method="GET")
    logger.info("Downloading artifact: %s -> %s", url, target_path)
    with urlopen(request) as response, target_path.open("wb") as out:
        shutil.copyfileobj(response, out)
    logger.info("Downloaded artifact: %s", target_path)


def create_temp_run_directory() -> Path:
    TEMP_DIRECTORY_ROOT.mkdir(parents=True, exist_ok=True)
    run_dir = TEMP_DIRECTORY_ROOT / f"vt_run_{int(time.time())}_{uuid.uuid4().hex[:8]}"
    run_dir.mkdir(parents=True, exist_ok=False)
    return run_dir


def cleanup_temp_run_directory(run_dir: Path, logger: logging.Logger) -> None:
    if run_dir.exists():
        shutil.rmtree(run_dir, ignore_errors=True)
        logger.info("Removed temp run directory: %s", run_dir)

    try:
        if TEMP_DIRECTORY_ROOT.exists() and not any(TEMP_DIRECTORY_ROOT.iterdir()):
            TEMP_DIRECTORY_ROOT.rmdir()
            logger.info("Removed empty temp root directory: %s", TEMP_DIRECTORY_ROOT)
    except OSError:
        logger.warning("Failed to remove temp root directory: %s", TEMP_DIRECTORY_ROOT)


def fetch_modrinth_releases(logger: logging.Logger) -> list[dict[str, Any]]:
    payload = http_json_public(
        method="GET",
        url=f"{MODRINTH_API_BASE}/project/{MODRINTH_PROJECT_ID_OR_SLUG}/version",
        logger=logger,
    )
    if not isinstance(payload, list):
        raise VirusTotalError("Unexpected Modrinth response shape: expected list of versions")
    return [item for item in payload if isinstance(item, dict)]


def download_releases_for_project_version(project_version: str, logger: logging.Logger) -> tuple[Path, list[Path]]:
    logger.info("Stage 1 started: downloading Modrinth releases for version '%s'", project_version)
    releases = fetch_modrinth_releases(logger)
    total_files_in_modrinth = sum(
        len(release.get("files", []))
        for release in releases
        if isinstance(release.get("files"), list)
    )
    logger.info(
        "Modrinth discovery stats | releases=%s files=%s project=%s",
        len(releases),
        total_files_in_modrinth,
        MODRINTH_PROJECT_ID_OR_SLUG,
    )
    selected = [release for release in releases if is_matching_release_version(release, project_version)]
    selected_files_in_modrinth = sum(
        len(release.get("files", []))
        for release in selected
        if isinstance(release.get("files"), list)
    )
    logger.info(
        "Version selection stats | matched_releases=%s matched_files=%s",
        len(selected),
        selected_files_in_modrinth,
    )

    if not selected:
        raise VirusTotalError(
            f"No Modrinth releases matched project version '{project_version}' "
            f"for project '{MODRINTH_PROJECT_ID_OR_SLUG}'"
        )

    run_dir = create_temp_run_directory()
    downloaded: list[Path] = []
    normalized_exts = {ext.lower() for ext in FILE_EXTENSIONS} if FILE_EXTENSIONS else None
    download_jobs: list[tuple[str, Path]] = []
    reserved_names: set[str] = set()

    try:
        for release in selected:
            release_id = str(release.get("id", "unknown_release"))
            files = release.get("files", [])
            if not isinstance(files, list):
                continue

            for index, file_obj in enumerate(files):
                if not isinstance(file_obj, dict):
                    continue

                url = str(file_obj.get("url", "")).strip()
                filename_raw = str(file_obj.get("filename", "")).strip()
                if not url:
                    continue

                filename = safe_filename(filename_raw or f"{release_id}_{index}.jar")
                target_path = run_dir / filename

                suffix = target_path.suffix.lower()
                if normalized_exts is not None and suffix not in normalized_exts:
                    logger.info("Skipping non-target extension file: %s", filename)
                    continue

                while target_path.name in reserved_names or target_path.exists():
                    target_path = run_dir / safe_filename(
                        f"{target_path.stem}_{uuid.uuid4().hex[:8]}{target_path.suffix}"
                    )
                reserved_names.add(target_path.name)
                download_jobs.append((url, target_path))

        total_jobs = len(download_jobs)
        if total_jobs == 0:
            logger.info("No downloadable artifacts after filters.")
        else:
            workers = min(MAX_DOWNLOAD_WORKERS, total_jobs)
            logger.info("Downloading Modrinth artifacts in parallel | workers=%s total=%s", workers, total_jobs)
            with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
                future_to_path = {
                    executor.submit(download_file, url, target_path, logger): target_path
                    for url, target_path in download_jobs
                }
                for future in concurrent.futures.as_completed(future_to_path):
                    target_path = future_to_path[future]
                    future.result()
                    downloaded.append(target_path)
                    processed = len(downloaded)
                    remaining = total_jobs - processed
                    logger.info(
                        "Modrinth download progress | processed=%s remaining=%s",
                        processed,
                        remaining,
                    )
    except Exception:
        cleanup_temp_run_directory(run_dir, logger)
        raise

    downloaded.sort(key=lambda path: path.name)
    logger.info(
        "Stage 1 completed: releases=%s downloaded_files=%s temp_dir=%s",
        len(selected),
        len(downloaded),
        run_dir,
    )
    return run_dir, downloaded


def extract_detections(analysis: dict[str, Any], file_path: Path) -> list[dict[str, Any]]:
    attrs = analysis.get("data", {}).get("attributes", {})
    results = attrs.get("results") or attrs.get("last_analysis_results") or {}

    warnings: list[dict[str, Any]] = []
    for engine_name, result in results.items():
        category = (result or {}).get("category")
        if category not in {"malicious", "suspicious"}:
            continue

        engine_result = (result or {}).get("result") or "<no signature>"
        details = f"{engine_name} [{category}] -> {engine_result}"
        warnings.append(
            {
                "file": str(file_path),
                "reason": "engine_detection",
                "details": details,
            }
        )

    return warnings


def should_include_warning(warning: dict[str, Any], reason_blacklist: set[str]) -> bool:
    reason = str(warning.get("reason", "")).strip().lower()
    details = str(warning.get("details", "")).strip()

    if reason in reason_blacklist:
        return False
    if details in DETECTION_DETAIL_BLOCKLIST:
        return False
    return True


def upload_files_parallel(
    files: list[Path],
    api_key: str,
    logger: logging.Logger,
) -> list[dict[str, Any]]:
    infos: list[dict[str, Any]] = []
    total = len(files)

    workers = min(MAX_UPLOAD_WORKERS, total)
    logger.info("Uploading files in parallel | workers=%s total=%s", workers, total)

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        future_to_path = {
            executor.submit(upload_file_for_analysis, file_path, api_key, logger): file_path
            for file_path in files
        }
        for future in concurrent.futures.as_completed(future_to_path):
            infos.append(future.result())
            processed = len(infos)
            remaining = total - processed
            logger.info("Upload progress | processed=%s remaining=%s", processed, remaining)

    return infos


def wait_for_all_analyses(
    infos: list[dict[str, Any]],
    api_key: str,
    logger: logging.Logger,
) -> dict[str, dict[str, Any]]:
    pending: dict[str, dict[str, Any]] = {}
    completed: dict[str, dict[str, Any]] = {}

    for info in infos:
        analysis_id = str(info["analysis_id"])
        pending[analysis_id] = {"info": info}

    started_at = time.monotonic()
    iteration = 0
    total = len(infos)

    while pending:
        iteration += 1
        if RESULTS_WAIT_TIMEOUT_SECONDS is not None:
            elapsed = time.monotonic() - started_at
            if elapsed > RESULTS_WAIT_TIMEOUT_SECONDS:
                raise VirusTotalError(
                    "Timeout while waiting analyses completion "
                    f"({len(pending)} still pending after {RESULTS_WAIT_TIMEOUT_SECONDS}s)"
                )

        due_ids = list(pending.keys())
        logger.info(
            "Polling iteration %s started | to_check=%s pending=%s",
            iteration,
            len(due_ids),
            len(pending),
        )

        workers = min(MAX_POLL_WORKERS, len(due_ids))
        with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
            future_to_analysis_id = {
                executor.submit(fetch_analysis_once, analysis_id, api_key, logger): analysis_id
                for analysis_id in due_ids
            }

            for future in concurrent.futures.as_completed(future_to_analysis_id):
                analysis_id = future_to_analysis_id[future]
                analysis = future.result()

                status = (
                    analysis.get("data", {})
                    .get("attributes", {})
                    .get("status", "unknown")
                )

                if status == "completed":
                    completed[analysis_id] = analysis
                    pending.pop(analysis_id, None)
                    logger.info("Analysis completed: %s", analysis_id)
                else:
                    logger.info("Analysis pending: %s | status=%s", analysis_id, status)

        processed = len(completed)
        remaining = total - processed
        logger.info(
            "Polling iteration %s finished | processed=%s remaining=%s",
            iteration,
            processed,
            remaining,
        )

        if pending:
            time.sleep(POLL_INTERVAL_SECONDS)

    return completed


def main() -> int:
    logger = setup_logger()
    logger.info("Starting VirusTotal verification")

    api_key = os.getenv(API_KEY_ENV_VAR)
    if not api_key:
        print(f"ERROR: Environment variable '{API_KEY_ENV_VAR}' is not set.")
        logger.error("Missing environment variable: %s", API_KEY_ENV_VAR)
        return 2

    temp_run_dir: Path | None = None
    try:
        project_version = read_project_version(logger)
        temp_run_dir, candidates = download_releases_for_project_version(project_version, logger)

        if MAX_FILES_TO_SCAN is not None:
            candidates = candidates[:MAX_FILES_TO_SCAN]

        if not candidates:
            print("No files found for verification.")
            logger.info("No files found for verification")
            return 0

        reason_blacklist = build_reason_blacklist()
        logger.info("Stage 2 started: VirusTotal analysis | files=%s", len(candidates))
        file_infos = upload_files_parallel(candidates, api_key, logger)
        analysis_by_id = wait_for_all_analyses(file_infos, api_key, logger)

        all_warnings: list[dict[str, Any]] = []

        for info in file_infos:
            analysis_id = str(info["analysis_id"])
            analysis = analysis_by_id.get(analysis_id)
            if analysis is None:
                all_warnings.append(
                    {
                        "file": info["file"],
                        "reason": "analysis_missing",
                        "details": f"Analysis result is missing for id {analysis_id}",
                    }
                )
                continue

            raw_warnings = extract_detections(analysis, Path(str(info["file"])))
            filtered_warnings = [
                warning for warning in raw_warnings if should_include_warning(warning, reason_blacklist)
            ]
            logger.info(
                "Detection stats for %s | raw_detections=%s | after_blocklist=%s",
                Path(str(info["file"])).name,
                len(raw_warnings),
                len(filtered_warnings),
            )
            all_warnings.extend(filtered_warnings)

        file_infos.sort(key=lambda item: str(item["file"]))
        all_warnings.sort(key=lambda item: (str(item["file"]), str(item["details"])))

        print("\nChecked files:")
        print(json.dumps(file_infos, ensure_ascii=False, indent=2))

        print("\nWarnings:")
        print(json.dumps(all_warnings, ensure_ascii=False, indent=2))

        has_detection = any(
            str(warning.get("reason", "")).strip().lower() == "engine_detection"
            for warning in all_warnings
        )
        verdict = "DETECTION_FOUND" if has_detection else "NO_DETECTIONS_FOUND"

        print(f"\nFinal detection verdict: {verdict}")
        logger.info("Final detection verdict: %s", verdict)
        logger.info("Log file: %s", LOG_FILE)
        return 0
    except (VirusTotalError, Exception) as exc:
        print(f"FATAL: {exc}")
        logger.exception("Fatal error. Stopping immediately.")
        return 1
    finally:
        if temp_run_dir is not None:
            cleanup_temp_run_directory(temp_run_dir, logger)


if __name__ == "__main__":
    raise SystemExit(main())
