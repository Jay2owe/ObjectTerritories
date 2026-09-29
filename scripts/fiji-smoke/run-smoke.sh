#!/usr/bin/env bash
# Headless smoke test of the built plugin jar in a disposable Fiji.
#
#   bash scripts/fiji-smoke/run-smoke.sh <fiji-dir> <work-dir> [log-dir]
#
# Installs target/Object_Territories-<version>.jar into <fiji-dir>/plugins
# (removing any other Object_Territories jar there), builds synthetic inputs in
# <work-dir>, runs each smoke macro headless and checks its output. Prints one
# PASS/FAIL line per check and exits non-zero if any check fails.
# Use a throwaway copy of Fiji: the plugins folder is modified.
set -u

FIJI="${1:?usage: run-smoke.sh <fiji-dir> <work-dir> [log-dir]}"
WORK="${2:?usage: run-smoke.sh <fiji-dir> <work-dir> [log-dir]}"
LOGS="${3:-$WORK/logs}"
HERE="$(cd "$(dirname "$0")" && pwd)"
PROJECT="$(cd "$HERE/../.." && pwd)"
PREFIX="${SMOKE_LOG_PREFIX:-ot-05-smoke}"

native() {
    if command -v cygpath > /dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

EXE=""
for candidate in fiji-windows-x64.exe ImageJ-win64.exe fiji-linux-x64 ImageJ-linux64 fiji; do
    if [ -f "$FIJI/$candidate" ]; then EXE="$FIJI/$candidate"; break; fi
done
[ -n "$EXE" ] || { echo "FAIL setup: no Fiji launcher in $FIJI"; exit 2; }

JAR=""
for candidate in "$PROJECT"/target/Object_Territories-*.jar; do
    case "$candidate" in
        *-sources.jar|*-tests.jar|*/original-*) ;;
        *) [ -f "$candidate" ] && JAR="$candidate" ;;
    esac
done
[ -n "$JAR" ] || { echo "FAIL setup: build the plugin first (./mvnw clean verify)"; exit 2; }

for old in "$FIJI"/plugins/Object_Territories-*.jar; do
    [ -f "$old" ] && rm -f "$old"
done
cp "$JAR" "$FIJI/plugins/"
echo "Installed $(basename "$JAR") into $FIJI/plugins"

rm -rf "$WORK"
mkdir -p "$WORK" "$LOGS"
WORK_NATIVE="$(native "$WORK")"
failures=0

# Runs one macro headless; stdout and stderr go to separate logs because the
# launcher only writes its output when redirected to a file.
run_macro() {
    local name="$1" short="${1#smoke-}"
    "$EXE" --headless -macro "$(native "$HERE/$name.ijm")" "$WORK_NATIVE" \
        > "$LOGS/$PREFIX-$short.log" 2> "$LOGS/$PREFIX-$short.err.log"
    echo $? > "$LOGS/$PREFIX-$short.exit"
}

check() {
    local label="$1" log="$2"
    if grep -q "SMOKE PASS $label" "$log" && ! grep -q "SMOKE FAIL" "$log"; then
        echo "PASS $label"
    else
        echo "FAIL $label (see $log)"
        grep "SMOKE FAIL" "$log" | sed 's/^/    /'
        failures=$((failures + 1))
    fi
}

run_macro make-fixtures
check fixtures "$LOGS/$PREFIX-make-fixtures.log"

run_macro smoke-commands
check commands "$LOGS/$PREFIX-commands.log"

run_macro smoke-2d
check 2d "$LOGS/$PREFIX-2d.log"

run_macro smoke-3d
check 3d "$LOGS/$PREFIX-3d.log"

run_macro smoke-batch
check batch2d "$LOGS/$PREFIX-batch.log"
check batch3d "$LOGS/$PREFIX-batch.log"

# Errors: each bad run must log one clear ERROR line naming the problem, must
# not carry on as if it had succeeded, and must print no stack trace to stdout.
errors_ok=1
for pair in "smoke-errors-option|permutations must be at least 1" \
            "smoke-errors-missing|label image is not open: Not open"; do
    name="${pair%%|*}"
    message="${pair#*|}"
    run_macro "$name"
    log="$LOGS/$PREFIX-${name#smoke-}.log"
    if ! grep -q "\[Object Territories\] ERROR: $message" "$log"; then
        echo "    $name: no '[Object Territories] ERROR: $message' line"
        errors_ok=0
    fi
    if grep -q "at territories\." "$log"; then
        echo "    $name: stack trace printed to stdout"
        errors_ok=0
    fi
    if grep -q "SMOKE UNEXPECTED" "$log"; then
        echo "    $name: the macro continued after the error"
        errors_ok=0
    fi
done
if [ "$errors_ok" -eq 1 ]; then
    echo "PASS errors"
else
    echo "FAIL errors"
    failures=$((failures + 1))
fi

if [ "$failures" -eq 0 ]; then
    echo "SMOKE: all checks passed ($(basename "$JAR"))"
    exit 0
fi
echo "SMOKE: $failures check(s) failed"
exit 1
