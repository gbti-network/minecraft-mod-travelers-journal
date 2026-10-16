"""Helpers for test-matrix.sh. Run with `python3 -I`.

  fabric-api <mc>        newest Fabric API on Modrinth for <mc>: url, sha512, version
  modinfo <jar>          mod id, version and declared minecraft range of a jar
  widen <jar> <out>      copy of <jar> whose minecraft range is widened to "*"
  port                   a free localhost TCP port
"""
import json
import socket
import sys
import urllib.parse
import urllib.request
import zipfile

UA = {"User-Agent": "gbti-network/travelers-journal-test-matrix"}


def fabric_api(mc):
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(["fabric"])})
    req = urllib.request.Request(f"https://api.modrinth.com/v2/project/fabric-api/version?{q}", headers=UA)
    versions = json.load(urllib.request.urlopen(req, timeout=30))
    if not versions:
        sys.exit(f"no Fabric API on Modrinth for {mc}")
    v = versions[0]
    f = next((f for f in v["files"] if f["primary"]), v["files"][0])
    print(f["url"], f["hashes"]["sha512"], v["version_number"], sep="\t")


def mod_json(jar):
    with zipfile.ZipFile(jar) as z:
        return json.loads(z.read("fabric.mod.json"))


def modinfo(jar):
    m = mod_json(jar)
    print(m["id"], m["version"], m.get("depends", {}).get("minecraft", "*"), sep="\t")


def widen(jar, out):
    with zipfile.ZipFile(jar) as src, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            data = src.read(info.filename)
            if info.filename == "fabric.mod.json":
                m = json.loads(data)
                m.setdefault("depends", {})["minecraft"] = "*"
                data = json.dumps(m, indent=2).encode()
            dst.writestr(info, data)


def port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        print(s.getsockname()[1])


if __name__ == "__main__":
    cmd, *args = sys.argv[1:] or ["help"]
    {"fabric-api": fabric_api, "modinfo": modinfo, "widen": widen, "port": port}.get(
        cmd, lambda *a: sys.exit(__doc__))(*args)
