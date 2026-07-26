#!/usr/bin/env bash

set -euo pipefail

readonly READY_SIGNAL='For help, type "help"'
readonly STOPPING_SIGNAL='Stopping server'
readonly GZIP_CLASS='org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream'
readonly STARTUP_TIMEOUT_SECONDS=240
readonly SHUTDOWN_TIMEOUT_SECONDS=60

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

if [[ "$#" -ne 5 ]]; then
    fail 'Usage: run-p4-b2-packaged-runtime-smoke.sh <input> <server> <neo-version> <compress-version> <java>'
fi

readonly INPUT_ROOT="$1"
readonly SERVER_ROOT="$2"
readonly NEO_VERSION="$3"
readonly COMPRESS_VERSION="$4"
readonly JAVA_EXECUTABLE="$5"
readonly INSTALLER="$INPUT_ROOT/neoforge-installer.jar"
readonly MOD_ARTIFACT="$INPUT_ROOT/gramarye.jar"
readonly FIXTURE_ROOT="$INPUT_ROOT/fixture"
readonly SERVER_LOG="$SERVER_ROOT/packaged-server.log"
readonly INSTALLER_LOG="$SERVER_ROOT/installer.log"
readonly STDIN_FIFO="$SERVER_ROOT/server.stdin"
readonly PRIMARY_RELATIVE='world/data/gramarye_skill_definitions.dat'
readonly MANIFEST_RELATIVE='world/p4-b2-packaged-runtime.properties'

for required_tool in cp date find grep kill mkdir mkfifo pgrep rm sleep tail; do
    command -v "$required_tool" >/dev/null 2>&1 \
        || fail "Packaged runtime smoke cannot find required tool: $required_tool"
done
[[ -f "$JAVA_EXECUTABLE" && -x "$JAVA_EXECUTABLE" ]] \
    || fail 'Packaged runtime smoke requires an executable Java 21 launcher'
for required_file in \
    "$INSTALLER" \
    "$MOD_ARTIFACT" \
    "$FIXTURE_ROOT/$PRIMARY_RELATIVE" \
    "$FIXTURE_ROOT/$MANIFEST_RELATIVE"; do
    [[ -f "$required_file" && ! -L "$required_file" ]] \
        || fail "Packaged runtime smoke input is missing or not a regular file: $required_file"
done
if [[ -e "$SERVER_ROOT" || -L "$SERVER_ROOT" ]]; then
    fail 'Packaged runtime server directory must be absent before installation'
fi

mkdir -p "$SERVER_ROOT"

if ! (
    cd "$SERVER_ROOT"
    "$JAVA_EXECUTABLE" -jar "$INSTALLER" --installServer
) > "$INSTALLER_LOG" 2>&1; then
    tail -n 160 "$INSTALLER_LOG" >&2
    fail 'NeoForge packaged server installation failed'
fi

readonly UNIX_ARGS="$SERVER_ROOT/libraries/net/neoforged/neoforge/$NEO_VERSION/unix_args.txt"
[[ -f "$UNIX_ARGS" && ! -L "$UNIX_ARGS" ]] \
    || fail 'NeoForge installer did not produce its locked unix_args.txt'

mkdir -p "$SERVER_ROOT/mods" "$SERVER_ROOT/world/data"
cp "$MOD_ARTIFACT" "$SERVER_ROOT/mods/gramarye.jar"
cp "$FIXTURE_ROOT/$PRIMARY_RELATIVE" "$SERVER_ROOT/$PRIMARY_RELATIVE"
cp "$FIXTURE_ROOT/$MANIFEST_RELATIVE" "$SERVER_ROOT/$MANIFEST_RELATIVE"

mod_count=0
while IFS= read -r installed_mod; do
    [[ -f "$installed_mod" && ! -L "$installed_mod" ]] \
        || fail 'Packaged runtime mods entry is not a regular file'
    mod_count=$((mod_count + 1))
done < <(find "$SERVER_ROOT/mods" -mindepth 1 -maxdepth 1 -print)
[[ "$mod_count" -eq 1 ]] \
    || fail 'Packaged runtime server must install exactly the Gramarye mod artifact'

printf 'eula=true\n' > "$SERVER_ROOT/eula.txt"
printf '%s\n' \
    'level-name=world' \
    'online-mode=false' \
    'server-port=0' \
    'motd=Gramarye P4-B2 packaged runtime smoke' \
    > "$SERVER_ROOT/server.properties"
printf '%s\n' \
    '-Xms256m' \
    '-Xmx512m' \
    '-Xlog:class+load=info' \
    > "$SERVER_ROOT/user_jvm_args.txt"

mkfifo "$STDIN_FIFO"
: > "$SERVER_LOG"
exec 3<> "$STDIN_FIFO"
server_pid=''

terminate_process_tree() {
    local parent_pid="$1"
    local child_pid=''

    while IFS= read -r child_pid; do
        [[ -n "$child_pid" ]] && terminate_process_tree "$child_pid"
    done < <(pgrep -P "$parent_pid" 2>/dev/null || true)
    kill -TERM "$parent_pid" 2>/dev/null || true
}

cleanup() {
    exec 3>&- || true
    if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
        terminate_process_tree "$server_pid"
        wait "$server_pid" 2>/dev/null || true
    fi
    if [[ -p "$STDIN_FIFO" ]]; then
        rm -f -- "$STDIN_FIFO"
    fi
}
trap cleanup EXIT INT TERM

(
    cd "$SERVER_ROOT"
    "$JAVA_EXECUTABLE" @user_jvm_args.txt @"$UNIX_ARGS" nogui \
        < "$STDIN_FIFO" > "$SERVER_LOG" 2>&1
) &
server_pid="$!"

startup_deadline=$(( $(date +%s) + STARTUP_TIMEOUT_SECONDS ))
ready=0
while kill -0 "$server_pid" 2>/dev/null; do
    if grep -Fq "$READY_SIGNAL" "$SERVER_LOG"; then
        ready=1
        break
    fi
    if (( $(date +%s) >= startup_deadline )); then
        tail -n 200 "$SERVER_LOG" >&2 || true
        fail 'Packaged NeoForge server did not reach its ready signal'
    fi
    sleep 1
done
if [[ "$ready" -ne 1 ]]; then
    tail -n 200 "$SERVER_LOG" >&2 || true
    fail 'Packaged NeoForge server exited before its ready signal'
fi

printf 'stop\n' >&3
shutdown_deadline=$(( $(date +%s) + SHUTDOWN_TIMEOUT_SECONDS ))
while kill -0 "$server_pid" 2>/dev/null; do
    if (( $(date +%s) >= shutdown_deadline )); then
        tail -n 200 "$SERVER_LOG" >&2 || true
        fail 'Packaged NeoForge server did not stop cleanly'
    fi
    sleep 1
done
if ! wait "$server_pid"; then
    tail -n 200 "$SERVER_LOG" >&2 || true
    fail 'Packaged NeoForge server exited with a failure status'
fi
server_pid=''

grep -Fq "$STOPPING_SIGNAL" "$SERVER_LOG" \
    || fail 'Packaged NeoForge server lacked its normal stopping signal'
if grep -Eq '(^|[[:space:]])(java\.lang\.)?(ClassNotFoundException|NoClassDefFoundError):' "$SERVER_LOG" \
        || grep -Fq 'org/apache/commons/compress/compressors/gzip/GzipCompressorInputStream' "$SERVER_LOG"; then
    fail 'Packaged NeoForge server reported a missing runtime class'
fi
if ! grep -F "$GZIP_CLASS" "$SERVER_LOG" \
        | grep -Fq "commons-compress-$COMPRESS_VERSION.jar"; then
    fail 'GzipCompressorInputStream was not loaded from the nested Commons Compress jar'
fi

printf '%s\n' \
    'P4B2_PACKAGED_SERVER_OK ready=true clean_shutdown=true nested_gzip_class=true'
