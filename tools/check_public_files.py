"""Inspect tracked INDEX contents without printing potential secrets. Python 3 only."""
from pathlib import PurePosixPath
import re
import struct
import subprocess
import sys


def git(*args):
    return subprocess.check_output(["git", *args])


ROOT_FILES = {
    ".gitignore", ".gitattributes", "LICENSE", "README.md", "CONTRIBUTING.md",
    "SECURITY.md", "THIRD_PARTY_NOTICES.md", "build.ps1", "test.ps1",
}
RULES = {
    "GitHub credential": rb"\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})\b",
    "AWS access key": rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b",
    "private key": rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    "JWT-like credential": rb"\beyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\b",
    "literal bearer credential": rb"(?i)bearer\s+[A-Za-z0-9._~+/-]{24,}",
    "personal absolute path": rb"(?i)(?:[a-z]:[\\/]users[\\/][a-z0-9_. -]+|/home/[a-z0-9_.-]+|/Users/[a-z0-9_.-]+)",
    "signed download URL": rb"(?i)[?&](?:x-amz-signature|x-goog-signature|sig)=[A-Za-z0-9%]{16,}",
}
EMAIL = re.compile(rb"\b[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")
UUID = re.compile(rb"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b")
# Public example profile used by the documented API fixtures, not an account export.
PUBLIC_UUID_FIXTURES = {
    b"069a79f4-44e9-4726-a5be-fca90e38aaf5",
    b"00000000-0000-0000-0000-000000000001",
    b"00000000-0000-0000-0000-000000000002",
}


def allowed(path):
    p = PurePosixPath(path)
    if path in ROOT_FILES:
        return True
    if p.parts[0] in {"src", "tests"}:
        return p.suffix == ".java"
    if p.parts[0] == "resources":
        return p.suffix in {".json", ".lss", ".png", ".MF"} or p.name == ".assetsroot"
    if p.parts[0] in {"docs", ".github"}:
        return p.suffix == ".md"
    return path == "tools/check_public_files.py"


def png_has_private_metadata(data):
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        return True
    offset = 8
    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        if kind in {b"tEXt", b"zTXt", b"iTXt", b"eXIf"}:
            return True
        offset += 12 + length
        if offset > len(data):
            return True
        if kind == b"IEND":
            return False
    return True


def main():
    records = [item for item in git("ls-files", "--stage", "-z").split(b"\0") if item]
    failures = []
    for record in records:
        info, raw_path = record.split(b"\t", 1)
        path = raw_path.decode("utf-8")
        mode, _, stage = info.decode("ascii").split()
        if mode not in {"100644", "100755"} or stage != "0":
            failures.append((path, "non-regular file or merge conflict"))
            continue
        if not allowed(path):
            failures.append((path, "not on publication allowlist"))
            continue
        data = git("show", ":" + path)
        if len(data) > 2 * 1024 * 1024:
            failures.append((path, "unexpectedly large file"))
            continue
        if path.endswith(".png"):
            if png_has_private_metadata(data):
                failures.append((path, "unexpected PNG metadata or malformed image"))
            continue
        try:
            data.decode("utf-8-sig")
        except UnicodeDecodeError:
            failures.append((path, "unexpected binary content"))
            continue
        for label, pattern in RULES.items():
            if re.search(pattern, data):
                failures.append((path, label))
        for match in EMAIL.finditer(data):
            # Deliberately invalid URL user-info in a texture-host rejection test.
            if match.group() == b"textures.minecraft.net@evil.example":
                continue
            if not match.group().lower().endswith(b"@users.noreply.github.com"):
                failures.append((path, "email address requires explicit review"))
        for match in UUID.finditer(data):
            if match.group().lower() not in PUBLIC_UUID_FIXTURES:
                failures.append((path, "non-fixture player UUID requires explicit review"))
    for path, reason in failures:
        print(f"BLOCK: {path}: {reason}")
    if failures:
        return 1
    if not records:
        print("BLOCK: no tracked files to inspect")
        return 1
    print(f"PASS: {len(records)} indexed files inspected; no blocked content detected.")
    print("This check complements manual review; it cannot detect every possible secret.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
