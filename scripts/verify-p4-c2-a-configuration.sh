#!/usr/bin/env bash
set -euo pipefail

# Keep this verifier independent of developer-only search tools and shell aliases while retaining
# the JDK selected by Gradle/CI for production-JAR inspection.
if [[ -n "${JAVA_HOME:-}" ]]; then
    PATH="${JAVA_HOME}/bin:/usr/bin:/bin:/usr/sbin:/sbin"
else
    PATH='/usr/bin:/bin:/usr/sbin:/sbin'
fi
export PATH

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

for required_tool in bash grep find mktemp rm jar dirname pwd awk; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "P4-C2-A configuration verifier cannot find required tool: ${required_tool}"
done

REPO_ROOT=''
if REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; then
    :
else
    fail 'P4-C2-A configuration verifier could not resolve the repository root'
fi
cd "${REPO_ROOT}"

PRODUCTION_SOURCE_LIST=''
C2_SOURCE_LIST=''
JAR_FILE_LIST=''
JAR_LISTING=''
PLAYER_SOURCE_LIST=''

cleanup() {
    for file in \
        "${PRODUCTION_SOURCE_LIST}" \
        "${C2_SOURCE_LIST}" \
        "${JAR_FILE_LIST}" \
        "${JAR_LISTING}" \
        "${PLAYER_SOURCE_LIST}"; do
        if [[ -n "${file}" ]]; then
            rm -f -- "${file}"
        fi
    done
}
trap cleanup EXIT HUP INT TERM

grep_failed() {
    local file="$1"
    local status="$2"
    fail "grep failed while checking ${file} (exit ${status})"
}

require_fixed() {
    local file="$1"
    local needle="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Fq -- "${needle}" "${file}" || status=$?
    case "${status}" in
        0) return 0 ;;
        1) fail "${message}" ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
}

forbid_fixed() {
    local file="$1"
    local needle="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Fq -- "${needle}" "${file}" || status=$?
    case "${status}" in
        0) fail "${message}" ;;
        1) return 0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
}

forbid_ere() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Eq -- "${pattern}" "${file}" || status=$?
    case "${status}" in
        0) fail "${message}" ;;
        1) return 0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
}

require_fixed_count() {
    local file="$1"
    local needle="$2"
    local expected="$3"
    local message="$4"
    local actual=''
    local status=0
    actual="$(LC_ALL=C grep -Fc -- "${needle}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) actual=0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    if [[ "${actual}" -ne "${expected}" ]]; then
        fail "${message} (expected ${expected}, found ${actual})"
    fi
}

require_ere_count() {
    local file="$1"
    local pattern="$2"
    local expected="$3"
    local message="$4"
    local actual=''
    local status=0
    actual="$(LC_ALL=C grep -Ec -- "${pattern}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) actual=0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    if [[ "${actual}" -ne "${expected}" ]]; then
        fail "${message} (expected ${expected}, found ${actual})"
    fi
}

require_regular_file() {
    local file="$1"
    local message="$2"
    if [[ ! -f "${file}" || -L "${file}" ]]; then
        fail "${message}"
    fi
}

collect_java_files() {
    local root="$1"
    local destination="$2"
    local status=0
    LC_ALL=C find "${root}" -type f -name '*.java' -print0 > "${destination}" || status=$?
    if [[ "${status}" -ne 0 || ! -s "${destination}" ]]; then
        fail "P4-C2-A configuration verifier could not inspect Java sources under ${root}"
    fi
}

forbid_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local message="$3"
    local file=''
    while IFS= read -r -d '' file; do
        forbid_fixed "${file}" "${needle}" "${message} (${file})"
    done < "${file_list}"
}

forbid_ere_in_file_list() {
    local file_list="$1"
    local pattern="$2"
    local message="$3"
    local file=''
    while IFS= read -r -d '' file; do
        forbid_ere "${file}" "${pattern}" "${message} (${file})"
    done < "${file_list}"
}

forbid_fixed_outside() {
    local file_list="$1"
    local needle="$2"
    local allowed_one="$3"
    local allowed_two="${4:-}"
    local allowed_three="${5:-}"
    local message="$6"
    local allowed_four="${7:-}"
    local allowed_five="${8:-}"
    local allowed_six="${9:-}"
    local file=''
    while IFS= read -r -d '' file; do
        if [[ "${file}" == "${allowed_one}" \
                || ( -n "${allowed_two}" && "${file}" == "${allowed_two}" ) \
                || ( -n "${allowed_three}" && "${file}" == "${allowed_three}" ) \
                || ( -n "${allowed_four}" && "${file}" == "${allowed_four}" ) \
                || ( -n "${allowed_five}" && "${file}" == "${allowed_five}" ) \
                || ( -n "${allowed_six}" && "${file}" == "${allowed_six}" ) ]]; then
            continue
        fi
        forbid_fixed "${file}" "${needle}" "${message} (${file})"
    done < "${file_list}"
}

count_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local total=0
    local count=0
    local status=0
    local file=''
    while IFS= read -r -d '' file; do
        status=0
        count="$(LC_ALL=C grep -Fc -- "${needle}" "${file}")" || status=$?
        case "${status}" in
            0) ;;
            1) count=0 ;;
            *) grep_failed "${file}" "${status}" ;;
        esac
        total=$((total + count))
    done < "${file_list}"
    printf '%s\n' "${total}"
}

verify_exact_gametest_components() {
    local file="$1"
    local actual=''
    local expected=''
    # These are native serializer/copy components on unplaced holders, never a managed
    # respawn grant. Keep each entire exception body closed, not a GameTest-wide exemption.
    actual="$(LC_ALL=C awk '
        /^    private static ServerPlayer componentCopy\(/ { selected = 1 }
        selected { line = $0; gsub(/[[:space:]]/, "", line); body = body line }
        selected && /^    }/ { print body; selected = 0 }
    ' "${file}")"
    expected='privatestaticServerPlayercomponentCopy(MinecraftServerserver,ServerPlayeroriginal,booleandeath){vartarget=unplacedPlayer(server,original.getUUID(),"p4c2-copy-component");net.neoforged.neoforge.attachment.AttachmentInternals.onPlayerClone(newnet.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(target,original,death));returntarget;}'
    [[ "${actual}" == "${expected}" ]] || fail 'P4-C2-A unplaced serialized-copy component body drifted'
    actual="$(LC_ALL=C awk '
        /^    private static void loadAttachmentFixture\(/ { selected = 1 }
        selected && !/^[[:space:]]*\/\// { line = $0; gsub(/[[:space:]]/, "", line); body = body line }
        selected && /^    }/ { print body; selected = 0 }
    ' "${file}")"
    expected='privatestaticvoidloadAttachmentFixture(ServerPlayerplayer,Tagattachment){player.setData(PlayerSkillAttachments.type(),readUnboundComponent(player,attachment));}'
    [[ "${actual}" == "${expected}" ]] || fail 'P4-C2-A unplaced material fixture assignment drifted'
    actual="$(LC_ALL=C awk '
        /^    private static PlayerSkillAttachmentState readUnboundComponent\(/ { selected = 1 }
        selected { line = $0; gsub(/[[:space:]]/, "", line); body = body line }
        selected && /^    }/ { print body; selected = 0 }
    ' "${file}")"
    expected='privatestaticPlayerSkillAttachmentStatereadUnboundComponent(ServerPlayerplayer,Tagattachment){if(player.getServer().getPlayerList().getPlayer(player.getUUID())==player||player.isAddedToLevel()||player.hasData(PlayerSkillAttachments.type())){thrownewAssertionError("componentinputrequiresanunplacedfreshholder");}returnPlayerSkillAttachmentSerializer.INSTANCE.read(player,attachment.copy(),player.registryAccess());}'
    [[ "${actual}" == "${expected}" ]] || fail 'P4-C2-A unplaced fresh-holder input guards drifted'
    require_fixed_count "${file}" 'PlayerEvent' 1 'P4-C2-A component must not add a gameplay event observer'
    require_fixed_count "${file}" '.setData(' 1 'P4-C2-A component must not add another Attachment mutator'
    require_fixed "${file}" 'holder.getServer().getPlayerList().getPlayer(holder.getUUID()) != holder' \
        'P4-C2-A copied component must remain distinct from a managed online source'
}

verify_exact_sources_and_registration() {
    local package_path='src/main/java/com/yo1no/gramarye/magic/definition/player'
    local registration="${package_path}/PlayerSkillAttachments.java"
    local service="${package_path}/PlayerSkillAttachmentService.java"
    local source_observation="${package_path}/PlayerSkillAttachmentSourceObservation.java"
    local admission_source='src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentAdmissionSource.java'
    local bound_source='src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1BoundPlayerSkillAttachmentAdmissionSource.java'
    local game_tests="${package_path}/PlayerSkillAttachmentGameTests.java"
    local mana_path='src/main/java/com/yo1no/gramarye/magic/runtime/mana'
    local mana_definition="${mana_path}/ManaAttachments.java"
    local mana_bridge="${mana_path}/ManaAttachmentDefinitionBridge.java"
    local mana_game_tests="${mana_path}/ManaLifecycleGameTests.java"
    local cooldown_definition='src/main/java/com/yo1no/gramarye/P11CastCooldownAttachments.java'
    local cooldown_bridge='src/main/java/com/yo1no/gramarye/P11CastCooldownDefinitionBridge.java'
    local cooldown_material='src/main/java/com/yo1no/gramarye/P11CastCooldownMaterial.java'
    local cooldown_codec='src/main/java/com/yo1no/gramarye/P11CastCooldownCodec.java'
    local p8_client='src/main/java/com/yo1no/gramarye/GramaryeClient.java'
    local p8_client_factories='src/main/java/com/yo1no/gramarye/magic/api/registry/P8BuiltInClientProfileFactories.java'
    local p9_entity_registration='src/main/java/com/yo1no/gramarye/P9StarterProjectileRegistration.java'
    local p9_client_input='src/main/java/com/yo1no/gramarye/magic/network/P9ClientCastInput.java'
    local p11_storage_boundary='src/main/java/com/yo1no/gramarye/P11NativeStorageBoundary.java'
    local p11_configuration='src/main/java/com/yo1no/gramarye/P11ConfigurationBoundary.java'
    local p11_registration=''
    local serialize_line=''
    local death_line=''

    PRODUCTION_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-a-production.XXXXXX")" \
        || fail 'P4-C2-A verifier could not create production source list'
    C2_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-a-reviewed.XXXXXX")" \
        || fail 'P4-C2-A verifier could not create reviewed source list'
    collect_java_files src/main/java "${PRODUCTION_SOURCE_LIST}"

    for source in \
        PlayerSkillAttachmentBuildResult \
        PlayerSkillAttachmentGameTests \
        PlayerSkillAttachmentService \
        PlayerSkillAttachments \
        ObservedPlayerSkillAttachment; do
        require_regular_file \
            "${package_path}/${source}.java" \
            "P4-C2-A reviewed production source is missing: ${source}.java"
        printf '%s\0' "${package_path}/${source}.java" >> "${C2_SOURCE_LIST}"
    done
    require_regular_file "${admission_source}" \
        'P4-E1-A.1 sealed Attachment admission source is missing'
    require_regular_file "${bound_source}" \
        'P4-E1-A.1 bound Attachment admission source is missing'
    require_regular_file "${mana_definition}" \
        'P6-S2 mana Attachment definition owner is missing'
    require_regular_file "${mana_bridge}" \
        'P6-S2-R3 public mana Attachment definition bridge is missing'
    require_regular_file "${mana_game_tests}" \
        'P6-S2 mana lifecycle GameTest holder is missing'
    for source in "${cooldown_definition}" "${cooldown_bridge}" "${cooldown_material}" "${cooldown_codec}"; do
        require_regular_file "${source}" 'P11 cooldown definition/material/codec source is missing'
    done
    require_regular_file "${p9_entity_registration}" \
        'P9-S3 exact projectile EntityType registration owner is missing'
    require_fixed "${admission_source}" \
        'public sealed abstract class PlayerSkillAttachmentAdmissionSource<I, P>' \
        'P4-E1-A.1 sealed Attachment admission declaration drifted'
    require_fixed "${bound_source}" \
        'extends PlayerSkillAttachmentAdmissionSource<Tag, HolderLookup.Provider>' \
        'P4-E1-A.1 package-private Tag/provider binding drifted'
    for operation in \
        'admitForRootAudit(' \
        'rootCount(RootAuditAdmitted admitted)' \
        'drainRootProjection(RootAuditAdmitted admitted, RootAuditSink sink)' \
        'discardRootProjection(RootAuditAdmitted admitted)'; do
        require_fixed "${service}" "${operation}" \
            "P4-E1-A.1 player service operation drifted: ${operation}"
    done

    for literal in \
        'DeferredRegister<AttachmentType<?>>' \
        'DeferredHolder<' \
        'NeoForgeRegistries.Keys.ATTACHMENT_TYPES' \
        '"player_skills"' \
        'PlayerSkillAttachmentPersistenceBridge::freshEmptyReady' \
        '.serialize(PlayerSkillAttachmentSerializer.INSTANCE)' \
        '.copyOnDeath()' \
        '.build()'; do
        require_fixed "${registration}" "${literal}" \
            "P4-C2-A registration lost reviewed fragment ${literal}"
    done
    require_fixed_count "${registration}" '.serialize(PlayerSkillAttachmentSerializer.INSTANCE)' 1 \
        'P4-C2-A registration must wire the C1 serializer exactly once'
    require_fixed_count "${registration}" '.copyOnDeath()' 1 \
        'P4-C2-A player-skills definition must enable copyOnDeath exactly once'
    serialize_line="$(LC_ALL=C grep -Fn -- '.serialize(PlayerSkillAttachmentSerializer.INSTANCE)' "${registration}")"
    death_line="$(LC_ALL=C grep -Fn -- '.copyOnDeath()' "${registration}")"
    serialize_line="${serialize_line%%:*}"
    death_line="${death_line%%:*}"
    if [[ "${serialize_line}" -ge "${death_line}" ]]; then
        fail 'P4-C2-A registration must serialize before copyOnDeath'
    fi
    forbid_fixed "${registration}" '.sync(' \
        'P4-C2-A permanent Attachment must remain server-only and unsynchronized'

    for literal in \
        'public final class ManaAttachmentDefinitionBridge' \
        'public static ResourceLocation attachmentId()' \
        'public static AttachmentType<?> attachmentType()'; do
        require_fixed_count "${mana_bridge}" "${literal}" 1 \
            "P6-S2-R3 mana definition bridge lost exact public surface ${literal}"
    done
    require_fixed_count "${mana_bridge}" 'private ManaAttachmentDefinitionBridge()' 1 \
        'P6-S2-R3 mana definition bridge must have one private constructor'
    require_ere_count "${mana_bridge}" '^[[:space:]]*public[[:space:]]' 3 \
        'P6-S2-R3 mana definition bridge must expose only its class and two accessors'
    require_ere_count "${mana_bridge}" \
        '^[[:space:]]+public static (ResourceLocation attachmentId|AttachmentType<\?> attachmentType)\(\)[[:space:]]*\{' \
        2 'P6-S2-R3 mana definition bridge public accessor declarations must be exact'
    require_ere_count "${mana_bridge}" '^[[:space:]]*protected[[:space:]]' 0 \
        'P6-S2-R3 mana definition bridge must expose no protected member'
    require_fixed_count \
        "${registration}" 'ManaAttachmentDefinitionBridge.attachmentId().getPath()' 1 \
        'P6-S2-R3 player_mana ID must enter the sole DeferredRegister exactly once'
    require_fixed_count \
        "${registration}" 'ManaAttachmentDefinitionBridge::attachmentType' 1 \
        'P6-S2-R3 player_mana definition must enter the sole DeferredRegister exactly once'
    require_fixed_count "${registration}" 'P11CastCooldownDefinitionBridge.attachmentId().getPath()' 1 \
        'P11 cast_cooldowns ID must enter the sole DeferredRegister exactly once'
    require_fixed_count "${registration}" 'P11CastCooldownDefinitionBridge::attachmentType' 1 \
        'P11 cooldown definition must enter the sole DeferredRegister exactly once'
    require_fixed_count "${registration}" 'DeferredHolder<' 3 \
        'P11 sole registration owner must contain exactly skills/mana/cooldown holders'
    require_fixed_count "${registration}" 'ATTACHMENT_TYPES.register(' 4 \
        'P11 sole registration owner must contain three entries and one bus registration'
    require_fixed_count "${mana_definition}" '"player_mana"' 1 \
        'P6-S2 mana definition must own the stable player_mana path exactly once'
    require_fixed_count \
        "${mana_definition}" '.serialize(ManaAttachmentSerializer.INSTANCE)' 1 \
        'P6-S2 mana definition must wire its serializer exactly once'
    require_fixed_count "${mana_definition}" '.copyOnDeath()' 1 \
        'P6-S2 mana definition must enable copyOnDeath exactly once'
    require_fixed_count "${mana_definition}" '.copyHandler(ManaLifecycle::copy)' 1 \
        'P6-S2 mana definition must wire the exact custom copy handler once'

    for literal in \
        'public final class P11CastCooldownDefinitionBridge' \
        'private P11CastCooldownDefinitionBridge()' \
        'public static ResourceLocation attachmentId() { return P11CastCooldownAttachments.ID; }' \
        'public static AttachmentType<?> attachmentType() { return P11CastCooldownAttachments.TYPE; }'; do
        require_fixed_count "${cooldown_bridge}" "${literal}" 1 \
            "P11 cooldown definition-only bridge drifted: ${literal}"
    done
    require_ere_count "${cooldown_bridge}" '^[[:space:]]*public[[:space:]]' 3 \
        'P11 cooldown bridge exposes only its class and two definition accessors'
    require_ere_count "${cooldown_bridge}" '^[[:space:]]*protected[[:space:]]' 0 \
        'P11 cooldown bridge exposes no protected member'
    for literal in \
        'final class P11CastCooldownAttachments {' \
        'ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "cast_cooldowns")' \
        'AttachmentType.builder(P11CastCooldownData::unbound)' \
        '.serialize(new Serializer()).copyOnDeath().build();' \
        'return actor.hasData(TYPE) ? actor.getData(TYPE) : null;' \
        'if (existing(actor) != expected) { throw new IllegalStateException("COOLDOWN_MATERIAL_CHANGED"); }' \
        'var before = P11CastCooldownMaterial.capture(actor);' \
        'actor.setData(TYPE, replacement);' \
        'P11NativeStorageBoundary.cooldownPublished(before, P11CastCooldownMaterial.capture(actor));' \
        'var result = P11CastCooldownCodec.read(input);' \
        'P11NativeStorageBoundary.cooldownReadCompleted(holder, P11CastCooldownMaterial.read(holder, result));' \
        'Tag output = P11CastCooldownCodec.write(data);' \
        'P11NativeStorageBoundary.cooldownWritten(P11CastCooldownMaterial.written(data, output));'; do
        require_fixed_count "${cooldown_definition}" "${literal}" 1 \
            "P11 cooldown native definition/serializer/access contract drifted: ${literal}"
    done
    require_fixed_count "${cooldown_definition}" '.copyOnDeath()' 1 \
        'P11 cooldown must retain exactly one serialized copyOnDeath definition'
    require_ere_count "${cooldown_definition}" 'public[[:space:]]' 2 \
        'P11 cooldown definition has only the two mandatory private serializer overrides'
    for literal in '.sync(' '.copyHandler(' 'freshEmptyReady' 'public final class' 'protected '; do
        forbid_fixed "${cooldown_definition}" "${literal}" \
            "P11 cooldown definition must remain unbound/server-only/serialized-copy (${literal})"
    done
    for literal in \
        'private State(ServerPlayer actor, P11CastCooldownData data)' \
        'boolean current(ServerPlayer expected) { return bound(expected) && P11CastCooldownAttachments.existing(expected) == data; }' \
        'boolean same(State other) { return other != null && actor == other.actor && data == other.data; }' \
        'private Write(P11CastCooldownData data, Tag output)' \
        'return output == actual && data == P11CastCooldownAttachments.existing(actor);'; do
        require_fixed_count "${cooldown_material}" "${literal}" 1 \
            "P11 cooldown exact material witness drifted: ${literal}"
    done
    for source in "${cooldown_material}" "${cooldown_codec}"; do
        forbid_ere "${source}" '(^|[[:space:]])(public|protected)[[:space:]]' \
            'P11 cooldown material/codec must remain package-private with no public bridge'
    done
    for literal in 'static P11CastCooldownData read(Tag input)' \
        'static CompoundTag write(P11CastCooldownData data)' \
        'if (data.kind == P11CastCooldownData.Kind.UNBOUND) { throw new IllegalStateException("COOLDOWN_UNBOUND_MATERIAL"); }'; do
        require_fixed_count "${cooldown_codec}" "${literal}" 1 \
            "P11 cooldown codec must preserve its closed native datum entrypoints: ${literal}"
    done

    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" 'AttachmentType' "${registration}" "${mana_definition}" \
        "${mana_bridge}" 'Attachment definition surface escaped the exact five-file allowlist' \
        "${cooldown_definition}" "${cooldown_bridge}"
    for literal in \
        'DeferredRegister<AttachmentType<?>>' \
        'NeoForgeRegistries.Keys.ATTACHMENT_TYPES' \
        'ATTACHMENT_TYPES.register('; do
        forbid_fixed_outside \
            "${PRODUCTION_SOURCE_LIST}" "${literal}" "${registration}" '' '' \
            'Attachment registry mutation surface escaped its unique owner'
    done
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" 'DeferredHolder' "${registration}" \
        "${p9_entity_registration}" '' \
        'DeferredHolder surface escaped the exact Attachment and P9 EntityType owners'
    require_fixed "${p9_entity_registration}" \
        'DeferredHolder<EntityType<?>, EntityType<P9StarterProjectile>>' \
        'P9-S3 projectile registration lost its exact typed DeferredHolder'
    forbid_fixed "${p9_entity_registration}" 'AttachmentType' \
        'P9-S3 EntityType registration must not acquire Attachment ownership'
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" '.copyOnDeath()' "${registration}" \
        "${mana_definition}" "${cooldown_definition}" \
        'Attachment copyOnDeath definition escaped its exact three-file allowlist'
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" '.copyHandler(' "${mana_definition}" '' '' \
        'Attachment copyHandler definition escaped the mana definition owner'
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" 'RegisterEvent' "${p8_client_factories}" '' '' \
        'RegisterEvent escaped the exact P8 client factory registration owner'
    require_fixed_count "${p8_client_factories}" 'RegisterEvent' 2 \
        'P8 client factory registration must retain its exact import and parameter type'
    require_fixed_count "${p8_client_factories}" \
        'bus = EventBusSubscriber.Bus.MOD)' 1 \
        'P8 built-in factory registration must execute on the client mod bus'
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" 'event.register(' "${p8_client_factories}" \
        "${p8_client}" "${p9_client_input}" \
        'event.register escaped the exact P8 registry/factory, P9 key and P11 Configuration task owners' \
        "${p11_configuration}"
    require_fixed_count "${p8_client_factories}" 'event.register(' 1 \
        'P8 built-in factory owner must perform exactly one startup registration batch'
    require_fixed_count "${p8_client}" 'event.register(' 1 \
        'P8 client bootstrap must register exactly one client factory registry'
    require_fixed_count "${p9_client_input}" 'event.register(' 1 \
        'P9 client input must register exactly one key mapping through its event'
    require_fixed_count "${p11_configuration}" 'event.register(' 1 \
        'P11 Configuration must register exactly one native Configuration task'
    require_fixed_count "${p11_configuration}" \
        'event.register(new P11ConfigurationTask(listener));' 1 \
        'P11 Configuration registration must retain its exact task and listener'
    p11_registration="$(LC_ALL=C awk '
        /^    public static void registerTasks\(/ { selected = 1 }
        selected { line = $0; gsub(/[[:space:]]/, "", line); body = body line }
        selected && /^    }/ { print body; selected = 0 }
    ' "${p11_configuration}")"
    [[ "${p11_registration}" == 'publicstaticvoidregisterTasks(RegisterConfigurationTasksEventevent){if(event.getListener()instanceofServerConfigurationPacketListenerImpllistener){event.register(newP11ConfigurationTask(listener));}}' ]] \
        || fail 'P11 Configuration task registration escaped its exact native listener method'
    for literal in \
        'DeferredRegister<AttachmentType<?>>' \
        'DeferredHolder' \
        'NeoForgeRegistries.Keys.ATTACHMENT_TYPES' \
        'ATTACHMENT_TYPES.register(' \
        'RegisterEvent' \
        'event.register('; do
        forbid_fixed "${mana_definition}" "${literal}" \
            "ManaAttachments must remain definition/access-only, not registry mutation (${literal})"
        forbid_fixed "${mana_bridge}" "${literal}" \
            "ManaAttachmentDefinitionBridge must remain mutation-free (${literal})"
        for source in "${cooldown_definition}" "${cooldown_bridge}" "${cooldown_material}" "${cooldown_codec}"; do
            forbid_fixed "${source}" "${literal}" \
                "P11 cooldown definition/material/codec must not mutate registration (${literal})"
        done
    done
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" '"player_skills"' "${registration}" "${game_tests}" "${p11_storage_boundary}" \
        'stable player skill Attachment ID escaped registration/tests/exact native observation'
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" '"player_mana"' "${mana_definition}" \
        "${mana_game_tests}" "${p11_storage_boundary}" \
        'stable player mana Attachment ID escaped definition/tests/exact native observation'
    require_fixed_count "${p11_storage_boundary}" \
        'private static final ResourceLocation SKILLS = ResourceLocation.fromNamespaceAndPath("gramarye", "player_skills");' 1 \
        'P11 must retain the exact read-only player skill registry key'
    require_fixed_count "${p11_storage_boundary}" \
        'private static final ResourceLocation MANA = ResourceLocation.fromNamespaceAndPath("gramarye", "player_mana");' 1 \
        'P11 must retain the exact read-only mana registry key'
    require_fixed_count "${p11_storage_boundary}" '"player_skills"' 1 \
        'P11 must not add another player skill ID use'
    require_fixed_count "${p11_storage_boundary}" '"player_mana"' 1 \
        'P11 must not add another mana ID use'
    forbid_fixed_outside "${PRODUCTION_SOURCE_LIST}" '"cast_cooldowns"' \
        "${cooldown_definition}" '' '' 'stable cooldown ID escaped its exact definition owner'
    require_fixed_count "${p11_storage_boundary}" \
        'private static final ResourceLocation COOLDOWNS = P11CastCooldownAttachments.ID;' 1 \
        'P11 native storage must use the sole cooldown definition ID'
    for owner in "${service}" "${game_tests}" "${source_observation}" "${mana_definition}" "${cooldown_definition}"; do
        require_fixed "${owner}" '.getData(' \
            "reviewed Attachment getData owner lost its access (${owner})"
    done
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" '.getData(' "${service}" "${game_tests}" \
        "${source_observation}" \
        'player Attachment getData escaped the exact five-file access allowlist' \
        "${mana_definition}" "${cooldown_definition}"
    require_fixed "${service}" '.setData(' \
        'player skill Attachment service lost its controlled setData access'
    require_fixed "${mana_definition}" '.setData(' \
        'mana Attachment definition/access owner lost its controlled setData access'
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" '.setData(' "${service}" "${mana_definition}" "${cooldown_definition}" \
        'Attachment setData escaped the exact owners and unplaced GameTest component' \
        "${game_tests}"
    verify_exact_gametest_components "${game_tests}"
    forbid_fixed_in_file_list \
        "${PRODUCTION_SOURCE_LIST}" '.removeData(' \
        'production must never remove the permanent player skill Attachment entry'
}

verify_phase_bounds_and_normal_tests() {
    local game_tests='src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java'
    local mana_game_tests='src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaLifecycleGameTests.java'
    local baseline_count=''
    local mana_count=''
    local normal_count=''

    for literal in \
        'PendingAttachmentJournal' \
        'SkillDefinitionSubmissionService' \
        'SkillDefinitionStore' \
        'SkillRetentionRootSnapshot' \
        'OfflineRoot' \
        'RootCollector' \
        'RootIndex' \
        'Reconciliation' \
        'CustomPacketPayload' \
        'StreamCodec' \
        'PayloadRegistrar' \
        'PacketDistributor' \
        'net.minecraft.client' \
        'PlayerEvent.Clone' \
        '.commit(' \
        '.reclaim(' \
        '.sync('; do
        if [[ "${literal}" == 'Reconciliation' ]]; then
            while IFS= read -r -d '' reviewed_source; do
                if [[ "${reviewed_source}" != \
                        'src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java' ]]; then
                    forbid_fixed "${reviewed_source}" "${literal}" \
                        'reconciliation escaped the exact P4-E2 player-service owner'
                fi
            done < "${C2_SOURCE_LIST}"
        elif [[ "${literal}" == 'PlayerEvent.Clone' ]]; then
            forbid_fixed_outside "${C2_SOURCE_LIST}" "${literal}" "${game_tests}" '' '' \
                'PlayerEvent.Clone escaped the exact unplaced serialized-copy component'
        else
            forbid_fixed_in_file_list \
                "${C2_SOURCE_LIST}" "${literal}" \
                'P4-C2-A reviewed source contains later/forbidden surface'
        fi
    done
    forbid_ere_in_file_list \
        "${C2_SOURCE_LIST}" \
        'Long\.MAX_VALUE|long[[:space:]]+(expected|target|mutation)[A-Za-z]*Generation' \
        'P4-C2-A generation must remain int/Integer.MAX_VALUE'

    normal_count="$(count_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" '@GameTest(')"
    mana_count="$(count_fixed_in_file_list <(printf '%s\0' "${mana_game_tests}") '@GameTest(')"
    baseline_count=$((normal_count - mana_count - $(bash scripts/verify-p7-s4-source-contracts.sh --game-test-count) + 19))
    if [[ "${baseline_count}" -ne 12 ]]; then
        fail "P4-C2-A reviewed baseline GameTest count must be twelve (found ${baseline_count})"
    fi
    if [[ "${mana_count}" -ne 7 ]]; then
        fail "P6-S2 mana lifecycle GameTest addition must be seven (found ${mana_count})"
    fi
    if [[ "${normal_count}" -ne "$(bash scripts/verify-p7-s4-source-contracts.sh --game-test-count)" ]]; then
        fail "P4-C2-A preserved twelve plus mana seven plus exact S4 GameTests inventory differs (found ${normal_count})"
    fi
    require_fixed_count "${game_tests}" '@GameTest(' 2 \
        'P4-C2-A normal holder must contain exactly two GameTests'
    require_fixed "${game_tests}" '@GameTestHolder(Gramarye.MOD_ID)' \
        'P4-C2-A normal holder lost the production GameTest namespace'
    require_fixed "${mana_game_tests}" '@GameTestHolder(Gramarye.MOD_ID)' \
        'P6-S2 mana lifecycle holder lost the production GameTest namespace'
    for test_id in \
        newPlayerAbsentStateIsAvailableZero \
        validAttachmentSerializesAndLoadsExactly \
        malformedAttachmentRemainsUnavailableWithoutMutation \
        deathCloneCopiesExactManaState \
        nonDeathCloneCopiesExactManaState \
        dimensionTravelKeepsSingleManaTruth \
        duplicatePersistentManaTruthIsAbsent; do
        require_fixed_count "${mana_game_tests}" "public static void ${test_id}(" 1 \
            "P6-S2 mana lifecycle GameTest ID changed or disappeared: ${test_id}"
    done

    require_regular_file \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' \
        'P4-D3-A reviewed recovery service is missing'
    require_fixed \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' \
        'PlayerEvent.PlayerLoggedInEvent' \
        'P4-D3-A reviewed login recovery event owner is missing'
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" 'PlayerEvent' \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' \
        'src/main/java/com/yo1no/gramarye/magic/network/P7ServerLifecycleEvents.java' \
        'src/main/java/com/yo1no/gramarye/P7S4LoginManaGameTests.java' \
        'PlayerEvent escaped the exact P4-D3-A recovery-service allowlist' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java' \
        'src/main/java/com/yo1no/gramarye/P8ServerPresentationService.java' \
        "${game_tests}"

    for literal in \
        "sourceSets.create('p4C2Probe')" \
        "sourceSets.create('p4C2GameTest')" \
        "tasks.register('p4C2FixedHeapGate')"; do
        require_fixed build.gradle "${literal}" \
            "P4-C2-A boundary lost reviewed test-only C2-B marker ${literal}"
    done
    require_fixed .github/workflows/build.yml 'p4-c-memory-gates:' \
        'P4-C2-A boundary lost the reviewed P4-C memory job'
    [[ -d src/p4C2Probe/java && -d src/p4C2GameTest/java ]] \
        || fail 'P4-C2-B isolated Java source roots are missing'
}

verify_generation_owner() {
    local generation='src/main/java/com/yo1no/gramarye/magic/definition/player/MutationGeneration.java'
    local file=''
    PLAYER_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-a-player.XXXXXX")" \
        || fail 'P4-C2-A verifier could not create player source list'
    collect_java_files \
        src/main/java/com/yo1no/gramarye/magic/definition/player \
        "${PLAYER_SOURCE_LIST}"
    while IFS= read -r -d '' file; do
        if [[ "${file}" == "${generation}" ]]; then
            continue
        fi
        forbid_ere \
            "${file}" \
            '(current|expected|mutation)[A-Za-z]*Generation[[:space:]]*\+[[:space:]]*1|current[[:space:]]*\+[[:space:]]*1' \
            'generation successor arithmetic escaped MutationGeneration'
    done < "${PLAYER_SOURCE_LIST}"
    require_fixed "${generation}" 'current == Integer.MAX_VALUE' \
        'MutationGeneration lost the checked exhaustion boundary'
    require_fixed "${generation}" 'OptionalInt.of(current + 1)' \
        'MutationGeneration lost the sole checked successor arithmetic'
    rm -f -- "${PLAYER_SOURCE_LIST}"
    PLAYER_SOURCE_LIST=''
}

verify_production_jar() {
    local jar_path=''
    local jar_count=0
    local status=0

    JAR_FILE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-a-jars.XXXXXX")" \
        || fail 'P4-C2-A verifier could not create JAR list'
    JAR_LISTING="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-a-jar-listing.XXXXXX")" \
        || fail 'P4-C2-A verifier could not create JAR listing'
    LC_ALL=C find build/libs -maxdepth 1 -type f -name 'gramarye-*.jar' -print0 \
        > "${JAR_FILE_LIST}" || status=$?
    if [[ "${status}" -ne 0 ]]; then
        fail "find failed while checking build/libs (exit ${status})"
    fi
    while IFS= read -r -d '' jar_path; do
        jar_count=$((jar_count + 1))
        status=0
        jar tf "${jar_path}" > "${JAR_LISTING}" || status=$?
        if [[ "${status}" -ne 0 ]]; then
            fail "jar failed while checking ${jar_path} (exit ${status})"
        fi
        for class_name in \
            PlayerSkillAttachmentBuildResult \
            PlayerSkillAttachmentGameTests \
            PlayerSkillAttachmentService \
            PlayerSkillAttachments \
            ObservedPlayerSkillAttachment; do
            require_fixed \
                "${JAR_LISTING}" \
                "com/yo1no/gramarye/magic/definition/player/${class_name}.class" \
                "P4-C2-A production JAR lacks reviewed class ${class_name}"
        done
        for class_path in \
            'com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentAdmissionSource.class' \
            'com/yo1no/gramarye/magic/definition/store/P4E1BoundPlayerSkillAttachmentAdmissionSource.class' \
            'com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService$OpaqueAdmissionSource.class' \
            'com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService$RootAuditAdmitted.class' \
            'com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService$RootAuditSink.class' \
            'com/yo1no/gramarye/magic/runtime/mana/ManaAttachmentDefinitionBridge.class' \
            'com/yo1no/gramarye/P11CastCooldownAttachments.class' \
            'com/yo1no/gramarye/P11CastCooldownAttachments$Serializer.class' \
            'com/yo1no/gramarye/P11CastCooldownDefinitionBridge.class' \
            'com/yo1no/gramarye/P11CastCooldownMaterial.class' \
            'com/yo1no/gramarye/P11CastCooldownCodec.class'; do
            require_fixed "${JAR_LISTING}" "${class_path}" \
                "P4-E1-A.1 production JAR lacks reviewed class ${class_path}"
        done
        for literal in \
            p4C2Probe p4C2GameTest gramarye_p4_c2 \
            P4D3 p4D3Probe p4D3GameTest gramarye_p4_d3; do
            forbid_fixed "${JAR_LISTING}" "${literal}" \
                "P4-C2-B fixture leaked into production JAR (${literal})"
        done
    done < "${JAR_FILE_LIST}"
    if [[ "${jar_count}" -lt 1 ]]; then
        fail 'P4-C2-A configuration verifier could not find a production JAR'
    fi
}

main() {
    verify_exact_sources_and_registration
    verify_phase_bounds_and_normal_tests
    verify_generation_owner
    verify_production_jar
    bash scripts/verify-p4-d1-configuration.sh
    printf '%s\n' 'Verified P4-C2-A registration, ownership, lifecycle, phase, and JAR contracts.'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
