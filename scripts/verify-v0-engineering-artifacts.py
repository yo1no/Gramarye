#!/usr/bin/env python3
"""Verify the shipped ordinary/engineering JAR pair against the adopted V0 artifact boundary."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

MIXIN_CONFIG = "gramarye.v0.engineering.mixins.json"
MIXIN_PACKAGE = "com.yo1no.gramarye.v0engineering.mixin"
MIXINS = ["P9V0LoadedPositionMixin", "V0OnlineSessionSuccessMixin", "V0OnlineSessionVerificationMixin"]
MOD_METADATA = "META-INF/neoforge.mods.toml"
METADATA_SUFFIX = ('\n[[mixins]]\nconfig="' + MIXIN_CONFIG + '"\n').encode("ascii")
REPLACEMENT = "com.yo1no.gramarye.V0Distribution"
ROOT = Path(__file__).resolve().parent.parent
PUBLIC, PRIVATE, PROTECTED, STATIC = 1, 2, 4, 8
VISIBILITY = PUBLIC | PRIVATE | PROTECTED


def require(condition, message):
    if not condition:
        raise ValueError(message)


def lookup(zip_file):
    names = {}
    for info in zip_file.infolist():
        if not info.is_dir():
            require(info.filename not in names, f"duplicate archive entry {info.filename}")
            names[info.filename] = info
    return names


def class_path(name):
    return name.replace(".", "/") + ".class"


def parse_class(raw):
    require(raw[:8] == bytes.fromhex("cafebabe00000041"), "V0 artifact class is not Java 21")
    offset = 8

    def read(size):
        nonlocal offset
        after = offset + size
        require(after <= len(raw), "truncated V0 classfile")
        result = raw[offset:after]
        offset = after
        return result

    def number(size):
        return int.from_bytes(read(size), "big")

    pool = [None]
    pool_size = number(2)
    widths = {3: 4, 4: 4, 5: 8, 6: 8, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3,
              16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while len(pool) < pool_size:
        tag = number(1)
        if tag == 1:
            pool.append(read(number(2)).decode("utf-8", errors="replace"))
        elif tag == 7:
            pool.append(number(2))
        else:
            read(widths[tag])
            pool.append(None)
            if tag in (5, 6):
                pool.append(None)

    def class_name(class_index):
        return pool[pool[class_index]].replace("/", ".")

    def attributes():
        return {pool[number(2)]: read(number(4)) for _ in range(number(2))}

    def members():
        entries = {}
        for _ in range(number(2)):
            visibility, name, descriptor = number(2), pool[number(2)], pool[number(2)]
            require((name, descriptor) not in entries, f"duplicate member {name}{descriptor}")
            entries[name, descriptor] = visibility
            attributes()
        return entries

    flags = number(2)
    owner = class_name(number(2))
    number(2)
    for _ in range(number(2)):
        number(2)
    fields, methods = members(), members()
    inner_table = attributes().get("InnerClasses", b"\x00\x00")
    require(offset == len(raw), f"unconsumed classfile bytes {owner}")
    for position in range(2, len(inner_table), 8):
        member, _, _, access = struct.unpack_from(">HHHH", inner_table, position)
        if class_name(member) == owner:
            flags = access
            break
    return owner, flags, fields, methods


def verify_class(name, raw, inventory_types, inventory_members):
    owner, flags, fields, methods = parse_class(raw)
    require(class_path(owner) == name, f"classfile owner mismatch for {name}")
    declaration = inventory_types.get(owner)
    if declaration is None:
        # Undeclared helpers must be private to a named class and have no extra visible members.
        host = owner.split("$", 1)[0]
        require(host in inventory_types and flags & VISIBILITY == PRIVATE,
                f"unapproved engineering type {owner}")
    else:
        expected = {"public": PUBLIC, "private": PRIVATE, "protected": PROTECTED, "package": 0}
        require(flags & VISIBILITY == expected[declaration["visibility"]], f"class visibility drift {owner}")

    approved = {(row["name"], row["descriptor"]): row for row in inventory_members.get(owner, ())}
    for key, row in approved.items():
        bucket = fields if row["classification"] == "G" else methods
        require(key in bucket, f"missing engineering declaration {owner} {key}")
        flags = bucket[key]
        require(flags & VISIBILITY == {"public": PUBLIC, "private": PRIVATE, "package": 0}[row["visibility"]],
                f"declaration visibility drift {owner} {key}")
        if isinstance(row["static"], bool):
            require(bool(flags & STATIC) == row["static"], f"declaration static drift {owner} {key}")
    for key, flags in (fields | methods).items():
        if flags & (PUBLIC | PROTECTED):
            require(key in approved, f"unapproved visible member {owner} {key}")

    require(b"net/minecraft/client/" not in raw and b"net.minecraft.client." not in raw
            and b"net/neoforged/neoforge/client/" not in raw,
            f"client linkage in engineering-only class {owner}")


def verify_jars(ordinary_path, engineering_path):
    ledger = json.loads((ROOT / "docs/evidence/v0/java-surface-after.json").read_text())
    types = {row["owner"]: row for row in ledger["types"]
             if row["artifact"] == "engineering-only" or row["owner"] == REPLACEMENT}
    members = {owner: [] for owner in types}
    for row in ledger["declarations"]:
        if row["artifact"] == "engineering-only" or row["owner"] == REPLACEMENT:
            members[row["owner"]].append(row)
    new_classes = {class_path(name) for name in types if name != REPLACEMENT}
    engine_private_prefixes = tuple(entry.removesuffix(".class") + "$" for entry in new_classes)
    leaf = class_path(REPLACEMENT)
    platform = json.loads((ROOT / "docs/evidence/v0/manifest.json").read_text())
    mutable = {leaf, MOD_METADATA}

    with zipfile.ZipFile(ordinary_path) as ordinary, zipfile.ZipFile(engineering_path) as engineering:
        ordinary_entries, engineering_entries = lookup(ordinary), lookup(engineering)
        ordinary_names, engineering_names = set(ordinary_entries), set(engineering_entries)
        require(ordinary_names <= engineering_names, "engineering archive omitted ordinary entries")
        require(mutable <= ordinary_names, "missing original ordinary metadata or composition leaf")
        require(new_classes.isdisjoint(ordinary_names) and MIXIN_CONFIG not in ordinary_names
                and not any(entry.startswith(engine_private_prefixes) for entry in ordinary_names),
                "ordinary archive contains engineering classes or resources")
        for entry in sorted(ordinary_names - mutable):
            require(ordinary.read(entry) == engineering.read(entry), f"shared archive bytes drift at {entry}")
        require(ordinary.read(leaf) != engineering.read(leaf), "engineering assembly does not replace ordinary leaf")
        require(engineering.read(MOD_METADATA) == ordinary.read(MOD_METADATA) + METADATA_SUFFIX,
                "NeoForge engineering metadata is not the exact ordinary metadata plus V0 mixins")
        config = json.loads(engineering.read(MIXIN_CONFIG))
        require(config.get("required") is True and config.get("defaultRequire", 1) == 1
                and config.get("package") == MIXIN_PACKAGE and config.get("mixins") == MIXINS
                and not config.get("client") and not config.get("server")
                and config.get("injectors") == {"defaultRequire": 1},
                "incorrect engineering mixin declarations")

        added = engineering_names - ordinary_names
        require(new_classes <= added, f"missing engineering classes: {sorted(new_classes - added)}")
        for entry in sorted((added - {MIXIN_CONFIG}) | {leaf}):
            require(entry.startswith("com/yo1no/gramarye/") and entry.endswith(".class"),
                    f"unapproved engineering archive resource {entry}")
            verify_class(entry, engineering.read(entry), types, members)

        require("META-INF/MANIFEST.MF" in ordinary_names
                and "META-INF/jarjar/metadata.json" in ordinary_names,
                "ordinary manifest or JarJar metadata is missing")
        manifest = ordinary.read("META-INF/MANIFEST.MF").decode("utf-8").splitlines()
        versions = platform["platform"]
        require(f"Gramarye-Minecraft-Version: {versions['minecraft']}" in manifest
                and f"Gramarye-NeoForge-Version: {versions['neoforge']}" in manifest,
                "ordinary/engineering archives disagree with the adopted platform coordinates")
        jarjar = json.loads(ordinary.read("META-INF/jarjar/metadata.json"))
        require(all(row["path"] in ordinary_names for row in jarjar["jars"]), "missing ordinary JarJar member")
        require(all("mixinextras" not in row["path"].lower() for row in jarjar["jars"]),
                "Gramarye carries a second copy of the native MixinExtras provider")
        return {
            "adopted_platform_bundle": platform["bundle_id"],
            "ordinary": {"bytes": ordinary_path.stat().st_size,
                         "sha256": hashlib.sha256(ordinary_path.read_bytes()).hexdigest()},
            "engineering": {"bytes": engineering_path.stat().st_size,
                            "sha256": hashlib.sha256(engineering_path.read_bytes()).hexdigest()},
            "shared_unchanged_entries": len(ordinary_names - mutable),
            "replacement_entries": sorted(mutable),
            "engineering_additions": sorted(added),
            "exact_engineering_declared_members": sum(map(len, members.values())),
            "mixin_configuration": MIXIN_CONFIG,
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ordinary", type=Path)
    parser.add_argument("engineering", type=Path)
    arguments = parser.parse_args()
    try:
        result = verify_jars(arguments.ordinary, arguments.engineering)
    except (OSError, KeyError, ValueError, zipfile.BadZipFile) as error:
        parser.exit(1, f"V0 artifact verification failed: {error}\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
