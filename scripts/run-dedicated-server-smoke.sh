#!/usr/bin/env bash

set -euo pipefail

readonly READY_SIGNAL='For help, type "help"'
readonly STOPPING_SIGNAL='Stopping server'
readonly SHUTDOWN_TIMEOUT_SECONDS=60

timeout_seconds="${GRAMARYE_SERVER_SMOKE_TIMEOUT_SECONDS:-180}"
if [[ ! "$timeout_seconds" =~ ^[1-9][0-9]*$ ]]; then
    echo "GRAMARYE_SERVER_SMOKE_TIMEOUT_SECONDS must be a positive integer." >&2
    exit 2
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "$script_dir/.." && pwd)"
smoke_root="$(mktemp -d "${TMPDIR:-/tmp}/gramarye-server-smoke.XXXXXX")"
game_dir="$smoke_root/game"
server_log="$smoke_root/server.log"
stdin_fifo="$smoke_root/server.stdin"
primary_saved_data="$game_dir/smoke-world/data/gramarye_skill_definitions.dat"
server_pid=""

terminate_process_tree() {
    local parent_pid="$1"
    local child_pid

    if command -v pgrep >/dev/null 2>&1; then
        while IFS= read -r child_pid; do
            [[ -n "$child_pid" ]] && terminate_process_tree "$child_pid"
        done < <(pgrep -P "$parent_pid" 2>/dev/null || true)
    fi

    kill -TERM "$parent_pid" 2>/dev/null || true
}

show_failure_log() {
    echo "--- dedicated server smoke log (last 120 lines) ---" >&2
    tail -n 120 "$server_log" >&2 || true
}

cleanup() {
    exec 3>&- || true
    if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
        terminate_process_tree "$server_pid"
        wait "$server_pid" 2>/dev/null || true
    fi
    rm -rf -- "$smoke_root"
}
trap cleanup EXIT INT TERM

mkdir -p "$game_dir"
printf 'eula=true\n' > "$game_dir/eula.txt"
printf '%s\n' \
    'level-name=smoke-world' \
    'online-mode=false' \
    'server-port=0' \
    'motd=Gramarye P0 dedicated-server smoke' \
    > "$game_dir/server.properties"
mkfifo "$stdin_fifo"
exec 3<>"$stdin_fifo"

cd "$project_dir"
GRAMARYE_SERVER_GAME_DIR="$game_dir" \
    ./gradlew --no-daemon --console=plain runServer \
    < "$stdin_fifo" > "$server_log" 2>&1 &
server_pid="$!"

startup_deadline=$(( $(date +%s) + timeout_seconds ))
ready=0
while kill -0 "$server_pid" 2>/dev/null; do
    if grep -Fq "$READY_SIGNAL" "$server_log"; then
        ready=1
        break
    fi
    if (( $(date +%s) >= startup_deadline )); then
        echo "Dedicated server did not become ready within ${timeout_seconds}s." >&2
        show_failure_log
        exit 1
    fi
    sleep 1
done

if (( ready == 0 )); then
    echo "Dedicated server exited before the ready signal." >&2
    show_failure_log
    exit 1
fi

printf 'stop\n' >&3
shutdown_deadline=$(( $(date +%s) + SHUTDOWN_TIMEOUT_SECONDS ))
while kill -0 "$server_pid" 2>/dev/null; do
    if (( $(date +%s) >= shutdown_deadline )); then
        echo "Dedicated server did not stop within ${SHUTDOWN_TIMEOUT_SECONDS}s after receiving stop." >&2
        show_failure_log
        exit 1
    fi
    sleep 1
done

if ! wait "$server_pid"; then
    echo "Dedicated server process exited with a failure status." >&2
    show_failure_log
    exit 1
fi
server_pid=""

if ! grep -Fq "$STOPPING_SIGNAL" "$server_log"; then
    echo "Dedicated server exited without the normal stopping signal." >&2
    show_failure_log
    exit 1
fi

if [[ -e "$primary_saved_data" || -L "$primary_saved_data" ]]; then
    echo "Absent Gramarye SavedData became dirty and created a primary .dat file." >&2
    show_failure_log
    exit 1
fi

echo "Dedicated server reached the ready signal, kept absent SavedData clean, and stopped cleanly."
