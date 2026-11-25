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
MIXINS = ["V0OnlineSessionSuccessMixin", "V0OnlineSessionVerificationMixin"]
MOD_METADATA = "META-INF/neoforge.mods.toml"
METADATA_SUFFIX = ('\n[[mixins]]\nconfig="' + MIXIN_CONFIG + '"\n').encode("ascii")
REPLACEMENT = "com.yo1no.gramarye.V0Distribution"
ROOT = Path(__file__).resolve().parent.parent
PUBLIC, PRIVATE, PROTECTED, STATIC = 1, 2, 4, 8
VISIBILITY = PUBLIC | PRIVATE | PROTECTED
MOVED_POSITION = "com.yo1no.gramarye.v0engineering.mixin.P9V0LoadedPositionMixin"
SHARED_POSITION = "com.yo1no.gramarye.mixin.P9V0LoadedPositionMixin"
NORMAL_PROFILE = "(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/V0StartupLoadState;)Lcom/yo1no/gramarye/V0StartupProfile;"


def scoped_u0_surfaces():
    """Named amendment-23 additions; generated visibility is counted, not exported as a plugin API."""
    base = "com.yo1no.gramarye."
    network = base + "magic.network.P7ServerAuthorizationBoundary$"
    permit = "Lcom/yo1no/gramarye/U0BoundedEntityQuery$Permit;"
    server, actor = "Lnet/minecraft/server/MinecraftServer;", "Lnet/minecraft/server/level/ServerPlayer;"
    level, entity = "Lnet/minecraft/world/level/Level;", "Lnet/minecraft/world/entity/Entity;"
    box, predicate = "Lnet/minecraft/world/phys/AABB;", "Ljava/util/function/Predicate;"
    receipt = "L" + network.replace(".", "/") + "NormalReceipt;"
    port_result = "L" + network.replace(".", "/") + "NormalIngressDisposition;"
    disposition = "L" + network.replace(".", "/") + "AdmissionDisposition;"
    feedback = "L" + network.replace(".", "/") + "NormalFeedback;"
    profile = "Lcom/yo1no/gramarye/V0StartupProfile;"
    kind, state = "Lcom/yo1no/gramarye/V0StartupProfile$Kind;", "Lcom/yo1no/gramarye/V0StartupLoadState;"
    # name, descriptor, visibility, static, role. Constructors and ordinary helpers stay closed.
    rows = {
        base + "U0BoundedEntityQuery": ("public", [
            ("<init>", "()V", "private", False, "explicit constructor"),
            ("collect", "(" + permit + "Ljava/lang/Object;Lnet/minecraft/world/level/entity/EntitySectionStorage;)V", "public", True, "explicit native bridge"),
            ("scopedCandidates", "(" + level + entity + box + predicate + ")Ljava/util/List;", "public", True, "explicit native bridge")]),
        base + "U0BoundedEntityQuery$Permit": ("public", [
            ("<init>", "(Lnet/minecraft/server/level/ServerLevel;Lcom/yo1no/gramarye/P9StarterProjectile;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;" + box + predicate + "Ljava/lang/Object;[J)V", "private", False, "explicit constructor")]),
        base + "U0BoundedEntityQuery$Result": ("package", []),
        base + "U0BoundedEntityQuery$Scan": ("package", []),
        base + "U0BoundedQueryAccess": ("public", [
            ("gramarye$u0Collect", "(" + permit + ")V", "public", False, "implicit interface operation")]),
        base + "mixin.U0SectionQueryMixin": ("package", [
            ("gramarye$u0Collect", "(" + permit + ")V", "public", False, "mandatory interface override")]),
        base + "mixin.U0ProjectileQueryMixin": ("package", []),
        SHARED_POSITION: ("package", [
            ("v0$guardPositionChunk", "(" + level + "IILcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Lnet/minecraft/world/level/chunk/LevelChunk;", "private", False, "moved original native wrapper")]),
        base + "V0StartupProfile": ("package", [
            ("<init>", "(" + state + kind + ")V", "package", False, "record canonical constructor"),
            ("state", "()" + state, "public", False, "generated record accessor"),
            ("kind", "()" + kind, "public", False, "generated record accessor"),
            ("equals", "(Ljava/lang/Object;)Z", "public", False, "generated record method"),
            ("hashCode", "()I", "public", False, "generated record method"),
            ("toString", "()Ljava/lang/String;", "public", False, "generated record method"),
            ("normal", "()" + profile, "package", True, "closed startup selection"),
            ("engineering", "(" + state + ")" + profile, "package", True, "closed startup selection")]),
        base + "V0StartupProfile$Kind": ("package", []),
        base + "V0SlotStartup": ("package", [
            ("<init>", "(" + server + "Lcom/yo1no/gramarye/V0CatalogAuthority;" + state + "Lcom/yo1no/gramarye/SkillRuntimeService;)V", "package", False, "preserved engineering constructor"),
            ("<init>", "(" + server + "Lcom/yo1no/gramarye/V0CatalogAuthority;" + state + "Lcom/yo1no/gramarye/SkillRuntimeService;" + kind + ")V", "package", False, "record canonical constructor"),
            ("server", "()" + server, "public", False, "preserved record accessor"),
            ("authority", "()Lcom/yo1no/gramarye/V0CatalogAuthority;", "public", False, "preserved record accessor"),
            ("v0State", "()" + state, "public", False, "preserved record accessor"),
            ("runtime", "()Lcom/yo1no/gramarye/SkillRuntimeService;", "public", False, "preserved record accessor"),
            ("profile", "()" + kind, "public", False, "generated record accessor"),
            ("equals", "(Ljava/lang/Object;)Z", "public", False, "preserved record method"),
            ("hashCode", "()I", "public", False, "preserved record method"),
            ("toString", "()Ljava/lang/String;", "public", False, "preserved record method")]),
        network + "NormalRootIngressPort": ("public", [
            ("authorizeAndAdmit", "(" + server + actor + receipt + ")" + port_result, "public", False, "implicit interface operation")]),
        network + "NormalReceipt": ("public", [
            ("<init>", "(Lcom/yo1no/gramarye/magic/network/P7SessionIdentity;J)V", "private", False, "explicit constructor"),
            ("defer", "()Z", "public", False, "explicit closed result join"),
            ("matches", "(Lcom/yo1no/gramarye/V0CatalogAuthority;Lcom/yo1no/gramarye/magic/network/P7ServerAuthorizationBoundary$V0PlayWitness;)Z", "public", False, "explicit original identity check"),
            ("complete", "(" + server + actor + disposition + feedback + ")V", "public", False, "explicit original result completion")]),
        network + "NormalIngressDisposition": ("public", []),
        network + "NormalFeedback": ("public", []),
    }
    enums = {
        base + "V0StartupProfile$Kind": ["NORMAL", "ENGINEERING"],
        network + "NormalIngressDisposition": ["PENDING", "NOT_READY", "CAPACITY", "COOLDOWN", "INSUFFICIENT_MANA", "UNAVAILABLE", "REJECTED"],
        network + "NormalFeedback": ["NOT_READY", "CAPACITY", "COOLDOWN_ACTIVE", "INSUFFICIENT_MANA", "RATE_LIMITED", "UNAVAILABLE"],
    }
    for owner, names in enums.items():
        descriptor = "L" + owner.replace(".", "/") + ";"
        rows[owner][1].extend((name, descriptor, "public", True, "generated enum constant") for name in names)
        rows[owner][1].extend([
            ("values", "()[" + descriptor, "public", True, "generated enum method"),
            ("valueOf", "(Ljava/lang/String;)" + descriptor, "public", True, "generated enum method")])
    return rows


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


def verify_u0_shared(ordinary, ordinary_names):
    surfaces = scoped_u0_surfaces()
    types = {owner: {"visibility": value[0]} for owner, value in surfaces.items()}
    members = {owner: [
        {"name": name, "descriptor": descriptor, "visibility": visibility, "static": static,
         "classification": "G" if role == "generated enum constant" else "I"}
        for name, descriptor, visibility, static, role in value[1]]
        for owner, value in surfaces.items()}
    for owner in surfaces:
        entry = class_path(owner)
        require(entry in ordinary_names, f"ordinary U0 shared class is missing: {owner}")
        verify_class(entry, ordinary.read(entry), types, members)
    config = json.loads(ordinary.read("gramarye.p11.mixins.json"))
    require(config.get("package") == "com.yo1no.gramarye.mixin"
            and config.get("required") is True and config.get("injectors") == {"defaultRequire": 1},
            "ordinary U0 common mixin owner or requirement drift")
    # The eight configuration consumers retain the complete original-48 plus named-three list.
    # Here the artifact must contain exactly those three names at its adopted append boundary.
    require(config.get("mixins", [])[-3:] == [
                "P9V0LoadedPositionMixin", "U0SectionQueryMixin", "U0ProjectileQueryMixin"]
            and len(config.get("mixins", [])) == 51
            and len(set(config["mixins"])) == 51,
            "ordinary U0 shared mixin role drift")
    return [
        {"owner": owner, "visibility": visibility,
         "members": [{"name": name, "descriptor": descriptor, "visibility": member_visibility,
                      "static": static, "role": role}
                     for name, descriptor, member_visibility, static, role in members]}
        for owner, (visibility, members) in surfaces.items()]


def verify_jars(ordinary_path, engineering_path):
    ledger = json.loads((ROOT / "docs/evidence/v0/java-surface-after.json").read_text())
    types = {row["owner"]: row for row in ledger["types"]
             if row["artifact"] == "engineering-only" or row["owner"] == REPLACEMENT}
    members = {owner: [] for owner in types}
    for row in ledger["declarations"]:
        if row["artifact"] == "engineering-only" or row["owner"] == REPLACEMENT:
            members[row["owner"]].append(row)
    # Amendment 23 A.6/A.7 moves only this pure wrapper into the ordinary/shared artifact.
    # Frozen R2 declarations remain immutable evidence; no other engineering type is relaxed.
    require(MOVED_POSITION in types and len(members[MOVED_POSITION]) == 1,
            "historical position-wrapper role identity drift")
    del types[MOVED_POSITION]
    del members[MOVED_POSITION]
    members[REPLACEMENT].append({"name": "profile", "descriptor": NORMAL_PROFILE,
                                 "classification": "I", "visibility": "package", "static": True})
    new_classes = {class_path(name) for name in types if name != REPLACEMENT}
    engine_private_prefixes = tuple(entry.removesuffix(".class") + "$" for entry in new_classes)
    leaf = class_path(REPLACEMENT)
    platform = json.loads((ROOT / "docs/evidence/v0/manifest.json").read_text())
    mutable = {leaf, MOD_METADATA}

    with zipfile.ZipFile(ordinary_path) as ordinary, zipfile.ZipFile(engineering_path) as engineering:
        ordinary_entries, engineering_entries = lookup(ordinary), lookup(engineering)
        ordinary_names, engineering_names = set(ordinary_entries), set(engineering_entries)
        qualification_roots = (
            'com/yo1no/gramarye/U0NormalCarrierObservation',
            'com/yo1no/gramarye/U0OrdinaryTrace',
            'com/yo1no/gramarye/U0OrdinaryScenario',
            'com/yo1no/gramarye/U0OrdinaryLogoutScenario',
            'com/yo1no/gramarye/harnessmixin/U0A05ScalarTraceMixin',
            'com/yo1no/gramarye/harnessmixin/U0BoundedScanTraceMixin',
            'com/yo1no/gramarye/harnessmixin/U0DistanceTicketTraceMixin',
            'com/yo1no/gramarye/harnessmixin/U0EntityTickTraceMixin',
            'com/yo1no/gramarye/harnessmixin/U0NormalCarrierTraceMixin',
            'com/yo1no/gramarye/harnessmixin/U0NormalInputTraceMixin',
            'com/yo1no/gramarye/harnessmixin/U0NormalReceiptTraceMixin',
            'com/yo1no/gramarye/harnessmixin/U0NormalSlotTraceMixin',
            'com/yo1no/gramarye/harnessmixin/U0CurrentActorTraceMixin',
            'com/yo1no/gramarye/harnessmixin/U0TrueDamagePostTraceMixin',
            'com/yo1no/gramarye/harnessmixin/U0VanillaFeedbackTraceMixin',
            'com/yo1no/gramarye/magic/network/U0NormalInputObservation',
            'com/yo1no/gramarye/magic/runtime/mana/U0OrdinaryManaObservation',
            'com/yo1no/gramarye/U021FutureCompletionBridge',
            'com/yo1no/gramarye/U027FuturePolicyBridge',
            'com/yo1no/gramarye/harnessmixin/U021FutureCatalogColdMixin',
            'com/yo1no/gramarye/harnessmixin/U027NormalCompositionMixin',
            'com/yo1no/gramarye/magic/definition/scoped/U021FutureCatalogUpdate',
            'com/yo1no/gramarye/magic/definition/scoped/U021UpdateState',
            'com/yo1no/gramarye/magic/definition/scoped/U027CatalogBarrierBridge',
        )
        for names in (ordinary_names, engineering_names):
            require(not any(entry == prefix + ".class" or entry.startswith(prefix + "$")
                            for prefix in qualification_roots for entry in names)
                    and "gramarye-u0-ordinary-qualification.mixins.json" not in names
                    and "gramarye-u0-future-qualification.mixins.json" not in names,
                    "distributable contains U0 qualification-only classes or resources")
        old_position = class_path(MOVED_POSITION)
        require(not any(entry == old_position or entry.startswith(old_position.removesuffix(".class") + "$")
                        for entry in ordinary_names | engineering_names),
                "old engineering-only position wrapper was duplicated instead of moved")
        require(ordinary_names <= engineering_names, "engineering archive omitted ordinary entries")
        require(mutable <= ordinary_names, "missing original ordinary metadata or composition leaf")
        require(new_classes.isdisjoint(ordinary_names) and MIXIN_CONFIG not in ordinary_names
                and not any(entry.startswith(engine_private_prefixes) for entry in ordinary_names),
                "ordinary archive contains engineering classes or resources")
        for entry in sorted(ordinary_names - mutable):
            require(ordinary.read(entry) == engineering.read(entry), f"shared archive bytes drift at {entry}")
        shared_surfaces = verify_u0_shared(ordinary, ordinary_names)
        # A shared position/query owner has no engineering issuer or qualification implementation.
        require(all(b"com/yo1no/gramarye/v0engineering/" not in ordinary.read(class_path(owner))
                    for owner in (SHARED_POSITION, "com.yo1no.gramarye.U0BoundedEntityQuery")),
                "ordinary native bridge links an engineering owner")
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
        scenario = class_path(REPLACEMENT + "$Scenario")
        require(scenario in engineering_names, "missing original engineering scenario")
        _, _, scenario_fields, _ = parse_class(engineering.read(scenario))
        require(not any(name == "damage" for name, _ in scenario_fields)
                and b"LivingDamageEvent$Post" not in engineering.read(scenario),
                "engineering scenario retained a second native damage-Post observer")
        for archive in (ordinary, engineering):
            _, _, _, leaf_methods = parse_class(archive.read(leaf))
            require(leaf_methods.get(("profile", NORMAL_PROFILE), -1) & (VISIBILITY | STATIC) == STATIC,
                    "startup profile selector is missing or has widened visibility")

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
            "scoped_authority": "23_DP2_U0_NormalPlayerStarterSpellActivation修正案.md A.6/A.7/C.3",
            "moved_shared_position": {"removed_engineering_owner": MOVED_POSITION,
                                      "ordinary_shared_owner": SHARED_POSITION},
            "ordinary_shared_u0_surface": shared_surfaces,
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
