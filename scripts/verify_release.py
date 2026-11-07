#!/usr/bin/env python3
"""Reject APKs that cannot update the public QSC 1.2.2 release in place."""
import argparse
import os
from pathlib import Path
import re
import subprocess

ORIGINAL_PACKAGE = "com.openrecorder.app.matepad"
# Public certificate from QSC-1.2.2.apk in this repository's qsc-v1.2.2 release.
ORIGINAL_CERTIFICATE = "c94a978cbff36ed765faa07654e3c1b694cd51bfdba2d6c3d165073909ad0940"


def verify(apk, tools, expected_certificate=ORIGINAL_CERTIFICATE):
    if not apk.is_file():
        raise ValueError("Release APK does not exist")
    if apk.stat().st_size > 10 * 1024 * 1024:
        raise ValueError("APK exceeds 10 MiB; check that release shrinking is enabled")
    badging = subprocess.check_output([str(tools / "aapt2"), "dump", "badging", str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not package or package[1] != ORIGINAL_PACKAGE:
        raise ValueError("APK must retain the original QSC application ID")
    if int(package[2]) < 10009:
        raise ValueError("APK must have the new, higher version code")
    if "application-debuggable" in badging:
        raise ValueError("A debuggable APK must not be published as the normal update")
    certificates = subprocess.check_output(
        [str(tools / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$", certificates, re.M)
    if [value.lower() for value in digests] != [expected_certificate.lower()]:
        raise ValueError("Signing certificate does not match the original QSC release; this APK cannot update it")
    print(f"Verified QSC {package[3]} ({package[2]}), original package and certificate, {apk.stat().st_size} bytes")
    return package[3]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", type=Path)
    args = parser.parse_args()
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    tools = args.build_tools or (Path(sdk) / "build-tools" / "36.0.0" if sdk else None)
    if tools is None:
        parser.error("Set ANDROID_HOME or provide --build-tools")
    try:
        verify(args.apk, tools)
    except (ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Release verification failed: {error}\n")
