"""Packs the per-range jars into the one jar that ships. Run with `python3 -I`.

  bundle.py <out.jar> <version> <jar>...

This is Fabric's jar-in-jar. The outer jar is the mod a server lists (id travelers-journal) and holds
no code. Each per-range jar goes under META-INF/jars/ with its id changed to travelers-journal-mc,
so Fabric finds one id with a candidate per range and loads the one whose minecraft range fits the
server. The ranges must not overlap; the per-range test matrix proves that before this runs.

The outer jar requires travelers-journal-mc and lists every range it covers, so on a version no
range covers, Fabric refuses it and names the versions it supports.
"""
import io
import json
import sys
import zipfile

NESTED_ID = "travelers-journal-mc"
# Entries this script writes get one fixed time, so the same inputs give the same jar
FIXED_TIME = (1980, 2, 1, 0, 0, 0)


def nested_jar(path):
    """The jar at <path> with its id changed, and the range it declares."""
    out = io.BytesIO()
    with zipfile.ZipFile(path) as src, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as dst:
        mod = json.loads(src.read("fabric.mod.json"))
        label = mod["version"].split("+mc", 1)[1].replace("-", " to ")
        mod["id"] = NESTED_ID
        mod["name"] = f"{mod['name']} for Minecraft {label}"
        for info in src.infolist():
            data = src.read(info.filename)
            if info.filename == "fabric.mod.json":
                data = json.dumps(mod, indent=2).encode()
            dst.writestr(info, data)
    return out.getvalue(), mod


def entry(name, compress):
    info = zipfile.ZipInfo(name, FIXED_TIME)
    info.compress_type = compress
    return info


def main(out, version, jars):
    nested, ranges, first = [], [], None
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as bundle:
        for path in jars:
            data, mod = nested_jar(path)
            first = first or (path, mod)
            name = "META-INF/jars/" + path.rsplit("/", 1)[-1]
            # Stored, not deflated: the jar inside is already compressed
            bundle.writestr(entry(name, zipfile.ZIP_STORED), data)
            nested.append({"file": name})
            ranges.append(mod["depends"]["minecraft"])
        path, mod = first
        with zipfile.ZipFile(path) as src:
            for extra in (mod.get("icon"), "LICENSE_travelers-journal"):
                if extra and extra in src.namelist():
                    bundle.writestr(entry(extra, zipfile.ZIP_DEFLATED), src.read(extra))
        outer = {
            "schemaVersion": 1,
            "id": "travelers-journal",
            "version": version,
            "name": mod["name"].split(" for Minecraft ")[0],
            "description": mod["description"],
            "authors": mod["authors"],
            "contributors": mod.get("contributors", []),
            "contact": mod.get("contact", {}),
            "license": mod.get("license"),
            "icon": mod.get("icon"),
            "environment": "server",
            "jars": nested,
            "depends": {
                "fabricloader": mod["depends"]["fabricloader"],
                "fabric-api": "*",
                # Any one of the ranges; Fabric reads a list as "any of these"
                "minecraft": ranges,
                NESTED_ID: "*",
            },
        }
        bundle.writestr(entry("fabric.mod.json", zipfile.ZIP_DEFLATED), json.dumps(outer, indent=2))
    print(f"{out}: {len(nested)} jars for Minecraft {', '.join(ranges)}")


if __name__ == "__main__":
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
