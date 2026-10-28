"""Inspect actual APK native ELF/ZIP alignment using only Python's standard library.

This is a packaging check, not a substitute for running on a 16 KiB Android device.
Run the SDK's `zipalign -c -P 16 -v 4 APK` as an independent ZIP check too.
"""
import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path


def inspect(apk):
    libraries = []
    with apk.open("rb") as raw, zipfile.ZipFile(apk) as archive:
        for item in archive.infolist():
            if not (item.filename.startswith("lib/") and item.filename.endswith(".so")):
                continue
            data = archive.read(item)
            if data[:4] != b"\x7fELF" or data[5] != 1:
                raise ValueError(f"Unexpected ELF: {item.filename}")
            bits = data[4]
            if bits == 2:
                offset = struct.unpack_from("<Q", data, 32)[0]
                size, count = struct.unpack_from("<HH", data, 54)
                layout = "<IIQQQQQQ"
            elif bits == 1:
                offset = struct.unpack_from("<I", data, 28)[0]
                size, count = struct.unpack_from("<HH", data, 42)
                layout = "<IIIIIIII"
            else:
                raise ValueError("Unknown ELF class")
            loads = []
            for index in range(count):
                fields = struct.unpack_from(layout, data, offset + size * index)
                if fields[0] == 1:
                    alignment = fields[-1]
                    file_offset, address = (fields[2], fields[3]) if bits == 2 else (fields[1], fields[2])
                    if alignment < 16384 or alignment & (alignment - 1) or file_offset % 16384 != address % 16384:
                        raise ValueError(f"ELF segment not 16 KiB aligned: {item.filename}")
                    loads.append(alignment)
            if not loads:
                raise ValueError("No ELF load segments")
            raw.seek(item.header_offset)
            header = raw.read(30)
            name_length, extra_length = struct.unpack_from("<HH", header, 26)
            start = item.header_offset + 30 + name_length + extra_length
            if item.compress_type != zipfile.ZIP_STORED or start % 16384:
                raise ValueError(f"Native library not uncompressed / ZIP 16 KiB aligned: {item.filename}")
            libraries.append(dict(path=item.filename, bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                                  loadAlignments=loads, zipDataOffset=start))
    if not libraries:
        raise ValueError("Expected SQLCipher native libraries were absent")
    with apk.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    return dict(apk=apk.name, sha256=digest, libraries=libraries,
                result="All native ELF segments and ZIP entries support 16 KiB alignment; runtime verification is separate.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    print(json.dumps(inspect(args.apk), indent=2))
