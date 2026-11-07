"""Verify built independent providers and link their mod member references against exact local JARs.
Does not launch Minecraft. Test artifacts and inspection evidence stay in this adapter.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path
from typing import Any

BASE = Path(__file__).resolve().parent
TARGETS = {
    "malum": ("malum-1.20.1-1.6.7.jar", "1.20.1-1.6.7", "ceda6ce4cc19de21ff6c21a732d8f9270ecc83cdae69027b13aaa24b536307f8", "dev.mcpmcp.malum.MalumProvider", "com/sammy/malum/", "lodestone-1.20.1-1.6.4.1.jar", "team/lodestar/lodestone/"),
    "wizards_reborn": ("wizards_reborn-1.20.1-0.2.9.jar", "1.20.1-0.2.9", "38f040319fe4dc16400b2507e3cb627a321cddba314de0d4acc4f2dcd8ffbfe7", "dev.mcpmcp.wizards.WizardsProvider", "mod/maxbogomol/wizards_reborn/", "fluffy_fur-1.20.1-0.2.2.jar", "mod/maxbogomol/fluffy_fur/"),
}


def parse_class(data: bytes) -> dict[str, Any]:
    off = 0
    def read(fmt: str) -> Any:
        nonlocal off
        n = struct.calcsize(fmt)
        result = struct.unpack_from(fmt, data, off)
        off += n
        return result[0] if len(result) == 1 else result
    assert read(">I") == 0xCAFEBABE
    minor, major = read(">HH")
    count = read(">H")
    cp: list[Any] = [None] * count
    i = 1
    while i < count:
        tag = read(">B")
        if tag == 1:
            n = read(">H"); cp[i] = (tag, data[off:off+n].decode("utf-8", errors="replace")); off += n
        elif tag in (3, 4): off += 4
        elif tag in (5, 6): off += 8; i += 1
        elif tag in (7, 8, 16, 19, 20): cp[i] = (tag, read(">H"))
        elif tag in (9, 10, 11, 12, 17, 18): cp[i] = (tag, read(">H"), read(">H"))
        elif tag == 15: cp[i] = (tag, read(">B"), read(">H"))
        else: raise AssertionError(f"Unknown constant pool tag {tag}")
        i += 1
    def utf(n): return cp[n][1]
    def cls(n): return utf(cp[n][1]) if n else None
    flags, this, parent = read(">HHH")
    interfaces = [cls(read(">H")) for _ in range(read(">H"))]
    def attributes():
        nonlocal off
        for _ in range(read(">H")):
            read(">H"); n = read(">I"); off += n
    members = {}
    for kind in ("field", "method"):
        entries = set()
        for _ in range(read(">H")):
            access, name, desc = read(">HHH"); entries.add((utf(name), utf(desc))); attributes()
        members[kind] = entries
    refs = []
    for c in cp:
        if c and c[0] in (9, 10, 11):
            nt = cp[c[2]]
            refs.append(("field" if c[0] == 9 else "method", cls(c[1]), utf(nt[1]), utf(nt[2])))
    return dict(name=cls(this), parent=cls(parent), interfaces=interfaces, major=major, refs=refs, **members)


def verify(mods_dir):
    results = []
    for mod, (pattern, version, target_hash, provider, prefix, dependency, dependency_prefix) in TARGETS.items():
        target_matches = list(mods_dir.glob("*" + pattern))
        dependency_matches = list(mods_dir.glob("*" + dependency))
        assert len(target_matches) == len(dependency_matches) == 1
        target = target_matches[0]
        assert hashlib.sha256(target.read_bytes()).hexdigest() == target_hash, f"Target hash mismatch: {mod}"
        artifact = BASE / "distribution" / mod / "adapter.jar"
        manifest = json.loads((artifact.parent / "plugin.json").read_text(encoding="utf-8"))
        assert manifest == dict(api=1, id="mcpmcp."+mod, mod_id=mod, minecraft="1.20.1", loader="forge", target_version=version, target_sha256=target_hash, actions=[])
        known: dict[str, dict[str, Any]] = {}
        # Enum.name() is inherited from the JDK, not declared in the target enum.
        jmod = Path("D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9/jmods/java.base.jmod")
        with zipfile.ZipFile(jmod) as jz:
            known["java/lang/Enum"] = parse_class(jz.read("classes/java/lang/Enum.class"))
        for dep in (target, dependency_matches[0]):
            with zipfile.ZipFile(dep) as z:
                for name in z.namelist():
                    if name.endswith(".class"): known[name[:-6]] = parse_class(z.read(name))
        def has_member(owner, kind, name, desc, visited=None):
            visited = set() if visited is None else visited
            if owner in visited: return False
            visited.add(owner)
            c = known.get(owner)
            if c is None: return False
            if (name, desc) in c[kind]: return True
            return any(has_member(p, kind, name, desc, visited) for p in [c["parent"], *c["interfaces"]] if p)
        linked = set()
        with zipfile.ZipFile(artifact) as z:
            service_files = [n for n in z.namelist() if n.startswith("META-INF/services/") and not n.endswith("/")]
            assert service_files == ["META-INF/services/dev.mcpmcp.api.McpmcpPlugin"]
            assert z.read(service_files[0]).decode().strip() == provider
            classes = [n for n in z.namelist() if n.endswith(".class")]
            own = "dev/mcpmcp/malum/" if mod == "malum" else "dev/mcpmcp/wizards/"
            assert all(n.startswith((own, "dev/mcpmcp/magic/")) for n in classes), f"Bundled unrelated classes: {mod}"
            assert not any("Test" in n for n in classes)
            for path in classes:
                c = parse_class(z.read(path)); assert c["major"] == 61, f"Not Java 17: {path}"
                for kind, owner, name, desc in c["refs"]:
                    assert not owner.startswith(("java/io/", "java/nio/file/", "java/lang/reflect/", "javax/script/")), f"Unexpected local I/O or scripts: {path} {owner}"
                    if owner.startswith((prefix, dependency_prefix)):
                        assert has_member(owner, kind, name, desc), f"Unresolved pinned mod member: {owner}.{name}{desc}"
                        if kind == "method":
                            assert not name.startswith(("set", "add", "remove", "insert", "extract", "start", "end", "craft", "tick", "onUse", "wissenWandFunction")), f"Unexpected mod mutator: {owner}.{name}"
                        linked.add((kind, owner, name, desc))
            native = parse_class(z.read("dev/mcpmcp/magic/NativeMagicBackend.class"))
            native_use = [r for r in native["refs"] if r[1] == "net/minecraft/client/multiplayer/MultiPlayerGameMode" and r[2] == "m_233732_"]
            assert len(native_use) == 1, "Native right-click must be reobfuscated"
            assert not any(r[1].startswith("net/minecraft/network/protocol/game/Serverbound") for r in native["refs"]), "No raw C2S bypass"
        staged_hash = hashlib.sha256(artifact.read_bytes()).hexdigest()
        assert staged_hash == hashlib.sha256((BASE/mod/"build/libs/adapter.jar").read_bytes()).hexdigest()
        assert staged_hash == hashlib.sha256((BASE/mod/"build/reobfJar/output.jar").read_bytes()).hexdigest()
        result = dict(mod=mod, artifact=str(artifact), artifact_sha256=staged_hash, bytes=artifact.stat().st_size, service_provider=provider, target_version=version, target_sha256=target_hash, production_classes=len(classes), pinned_member_references_verified=len(linked), raw_c2s=False, bundled_target_or_core=False, live_game_tested=False)
        results.append(result)
        print(f"PASS {mod}: {len(classes)} Java 17 classes, {len(linked)} pinned member references, one independent provider")
    report = dict(result="passed", checks=results, live_game_tested=False)
    (BASE/"evidence/artifact-verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    return report

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--mods-dir", type=Path, default=Path("D:/Games/mc/Cheat/versions/神秘启旅/mods"))
    args = p.parse_args()
    verify(args.mods_dir)
