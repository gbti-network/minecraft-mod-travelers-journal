#!/usr/bin/env bash
# Test matrix for Traveler's Journal: boots each packaged jar on real Fabric servers, one
# throwaway server per Minecraft version, with the probe test mod (src/probe) beside it. The probe
# joins mock players and checks what a player gets; see JournalProbe.java for the checks.
#
# One verdict per cell:
#
#   PASS     every probe check passed and the log shows no errors
#   FAIL     the server ran, but a probe check failed or the log shows an error
#   CRASH    the server never became ready, or died
#   REFUSED  Fabric's incompatible-mod message: the jar's declared range excludes the version
#   ENV      the control boot (Fabric API alone) failed, so the version cannot be judged here
#
# Usage:
#   test-matrix.sh fetch [versions...]                 server launcher + Fabric API + control boot
#   test-matrix.sh run <jar> <probe> <version> [--widen]   one cell, one TSV line on stdout
#   test-matrix.sh matrix <outdir> <jar> <probe> [<jar> <probe>...]   every pair on every version
#   test-matrix.sh libs <outdir> <dir>                 matrix of every jar in <dir> with its -probe.jar
#
# --widen tests a copy of the jar whose minecraft range is "*", to learn whether its code runs on
# a version its range excludes. A matrix passes when no cell is FAIL, CRASH or ENV and every
# version has a PASS. Linux only (WSL is fine). Servers live under ~/.cache/travelers-boot, on the
# Linux filesystem, in offline mode on a free localhost port. The EULA is copied from EULA_FROM,
# a server whose EULA the owner has accepted; this script never accepts one itself and never
# deletes anything (run directories collect under $CACHE/runs and are cleared by hand).
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
HELPER="$HERE/test-matrix.py"
CACHE="${TB_CACHE:-$HOME/.cache/travelers-boot}"
JAVA="${JAVA:-$HOME/.jdks/jdk-25.0.3+9/bin/java}"
EULA_FROM="${EULA_FROM:-/mnt/d/_Outfits/GBTI/MinecraftServer/.server/eula.txt}"
LOADER=0.19.5
INSTALLER=1.1.2
BOOT_TIMEOUT=${BOOT_TIMEOUT:-300}
PROBE_TIMEOUT=${PROBE_TIMEOUT:-60}
PARALLEL=${PARALLEL:-4}
EXPECT_CHECKS=22
ALL_VERSIONS=(1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11
	26.1 26.1.1 26.1.2 26.2 26.3)
ERRORS='InvalidMixinException|MixinApplyError|MixinTransformerError|NoClassDefFoundError|NoSuchMethodError|NoSuchFieldError|AbstractMethodError|BootstrapMethodError|IncompatibleClassChangeError|ClassCastException|Exception in thread|\[TJ\] (An error|Failed)|TJ-PROBE\] stack trace'

trap '' PIPE # a server that dies mid-run must not take this script (and xargs) down with it
die() { echo "test-matrix: $*" >&2; exit 2; }
helper() { python3 -I "$HELPER" "$@"; }
oneline() { tr '\t\r\n' '   ' | cut -c1-220; }

[[ -x "$JAVA" ]] || die "no Java at $JAVA (set JAVA)"
grep -q '^eula=true' "$EULA_FROM" 2>/dev/null || die "no accepted EULA at $EULA_FROM (set EULA_FROM)"

write_props() { # dir port
	printf '%s\n' "server-port=$2" "server-ip=127.0.0.1" "online-mode=false" "enable-rcon=false" \
		'level-type=minecraft\:flat' "generate-structures=false" "spawn-protection=0" \
		"view-distance=4" "simulation-distance=4" "max-players=4" "motd=travelers journal test matrix" \
		> "$1/server.properties"
}

# Starts the server in <dir>, waits for "Done (", then runs each step: "sleep:N" pauses,
# "until:PATTERN" waits up to PROBE_TIMEOUT seconds for PATTERN in the log, anything else is a
# console command. Then stops it. Returns 1 if the server never became ready, 2 if it died after.
boot() { # dir log [steps...]
	local dir=$1 log=$2 fifo="$1/.stdin" waited=0 pid step died=0 t
	shift 2
	mkfifo "$fifo"
	(cd "$dir" && exec env -i HOME="$dir" PATH=/usr/bin:/bin \
		"$JAVA" -Xms256M -Xmx1G -jar fabric-server-launch.jar nogui) < "$fifo" > "$log" 2>&1 &
	pid=$!
	exec 3> "$fifo" # held open so the server never reads EOF on stdin
	until grep -q 'Done (' "$log"; do
		if ! kill -0 "$pid" 2>/dev/null || (( waited >= BOOT_TIMEOUT )); then
			kill -9 "$pid" 2>/dev/null
			exec 3>&-
			wait "$pid" 2>/dev/null
			return 1
		fi
		sleep 1
		waited=$((waited + 1))
	done
	for step in "$@"; do
		kill -0 "$pid" 2>/dev/null || { died=1; break; }
		case $step in
			sleep:*) sleep "${step#sleep:}" ;;
			until:*)
				for (( t = 0; t < PROBE_TIMEOUT; t++ )); do
					grep -qF -- "${step#until:}" "$log" && break
					kill -0 "$pid" 2>/dev/null || break
					sleep 1
				done ;;
			*) echo "$step" >&3 2>/dev/null || { died=1; break; } ;;
		esac
	done
	kill -0 "$pid" 2>/dev/null || died=1
	(( died )) || echo stop >&3 2>/dev/null
	for _ in $(seq 60); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
	kill -9 "$pid" 2>/dev/null
	exec 3>&-
	wait "$pid" 2>/dev/null
	(( died )) && return 2
	return 0
}

fetch_one() { # version
	local v=$1 d="$CACHE/mc/$1" url sha ver
	mkdir -p "$d"
	if [[ ! -s $d/fabric-server-launch.jar ]]; then
		curl -fsSL -o "$d/launch.part" \
			"https://meta.fabricmc.net/v2/versions/loader/$v/$LOADER/$INSTALLER/server/jar" \
			&& mv "$d/launch.part" "$d/fabric-server-launch.jar" || { echo "fetch $v: launcher download failed"; return; }
	fi
	if [[ ! -s $d/fabric-api.jar ]]; then
		IFS=$'\t' read -r url sha ver < <(helper fabric-api "$v")
		curl -fsSL -o "$d/api.part" "$url" || { echo "fetch $v: Fabric API download failed"; return; }
		echo "$sha  $d/api.part" | sha512sum -c --quiet || { echo "fetch $v: Fabric API checksum mismatch"; return; }
		mv "$d/api.part" "$d/fabric-api.jar"
		echo "$ver" > "$d/fabric-api.version"
	fi
	if [[ ! -f $d/control.ok && ! -d $d/control ]]; then
		mkdir -p "$d/control/mods"
		cp "$d/fabric-server-launch.jar" "$d/control/"
		cp "$EULA_FROM" "$d/control/eula.txt"
		cp "$d/fabric-api.jar" "$d/control/mods/"
		write_props "$d/control" "$(helper port)"
		if boot "$d/control" "$d/control.log" "sleep:2" \
			&& [[ -z "$(ls -A "$d/control/crash-reports" 2>/dev/null)" ]] \
			&& [[ -d $d/control/libraries && -d $d/control/.fabric/server ]]; then
			touch "$d/control.ok"
		fi
	fi
	echo "fetch $v: control $([[ -f $d/control.ok ]] && echo ok || echo FAILED), Fabric API $(cat "$d/fabric-api.version" 2>/dev/null)"
}

run_one() { # jar probe version [--widen]
	local jar=$1 probe=$2 v=$3 widen=${4:-} d="$CACHE/mc/$3" start=$SECONDS
	local id mver range label work log verdict evidence ready=1 rc=0 done_line
	IFS=$'\t' read -r id mver range < <(helper modinfo "$jar")
	label=$mver
	[[ $widen ]] && label="$mver(widened)"
	result() { printf '%s\t%s\t%s\t%s\t%s\n' "$label" "$v" "$1" "$((SECONDS - start))" "$2"; }
	if [[ ! -f $d/control.ok ]]; then
		result ENV "control boot failed or not fetched: $d/control.log"
		return
	fi
	mkdir -p "$CACHE/runs"
	work=$(mktemp -d "$CACHE/runs/journal-$v-XXXX")
	mkdir -p "$work/mods" "$work/.fabric"
	cp "$d/fabric-server-launch.jar" "$work/"
	cp "$EULA_FROM" "$work/eula.txt"
	ln -s "$d/control/libraries" "$work/libraries"
	[[ -d $d/control/versions ]] && ln -s "$d/control/versions" "$work/versions"
	ln -s "$d/control/.fabric/server" "$work/.fabric/server"
	cp "$d/fabric-api.jar" "$probe" "$work/mods/"
	if [[ $widen ]]; then helper widen "$jar" "$work/mods/$(basename "$jar")"; else cp "$jar" "$work/mods/"; fi
	write_props "$work" "$(helper port)"
	log="$work/run.log"
	boot "$work" "$log" "until:[TJ-PROBE] DONE" "sleep:1" || rc=$?
	(( rc == 1 )) && ready=0

	if grep -qE 'Incompatible mods? (found|set)' "$log"; then
		verdict=REFUSED
		evidence=$(grep -m1 -E "\($id\) .* requires .*\(minecraft\)" "$log" | sed 's/^[[:space:]]*- //' | oneline)
	elif (( ! ready )); then
		verdict=CRASH
		evidence=$(grep -m1 -E "$ERRORS" "$log" | oneline)
		[[ $evidence ]] || evidence="no ready line within ${BOOT_TIMEOUT}s: $(tail -n 1 "$log" | oneline)"
	elif (( rc == 2 )); then
		verdict=CRASH
		evidence="crashed after ready: $(grep -m1 -E "$ERRORS" "$log" | oneline)"
	else
		local problems=() err
		done_line=$(grep -m1 -oE '\[TJ-PROBE\] DONE pass=[0-9]+ fail=[0-9]+' "$log")
		if [[ -z $done_line ]]; then
			problems+=("probe never finished")
		elif [[ $done_line != *"pass=$EXPECT_CHECKS fail=0" ]]; then
			problems+=("${done_line#*] }: $(grep -E '\[TJ-PROBE\] FAIL' "$log" | sed 's/.*\[TJ-PROBE\] FAIL //' | tr '\n' ';')")
		fi
		err=$(grep -m1 -E "$ERRORS" "$log" | oneline)
		[[ $err ]] && problems+=("$err")
		[[ -z "$(ls -A "$work/crash-reports" 2>/dev/null)" ]] || problems+=("crash report written")
		if (( ${#problems[@]} )); then
			verdict=FAIL
			evidence=$(printf '%s; ' "${problems[@]}" | oneline)
		else
			verdict=PASS
			evidence="${done_line#*] }"
		fi
	fi
	if [[ ${TB_OUT:-} ]]; then
		mkdir -p "$TB_OUT/logs"
		cp "$log" "$TB_OUT/logs/$label-$v.log"
	fi
	result "$verdict" "$evidence"
}

matrix() { # outdir jar probe [jar probe...]
	local out=$1 v
	shift
	(( $# >= 2 && $# % 2 == 0 )) || die "matrix needs <jar> <probe> pairs"
	mkdir -p "$out"
	export TB_OUT=$out
	for v in "${ALL_VERSIONS[@]}"; do echo "$v"; done | xargs -P "$PARALLEL" -I{} "$0" fetch {} | sort -V > "$out/fetch.txt"
	while (( $# )); do
		for v in "${ALL_VERSIONS[@]}"; do printf '%s\0%s\0%s\0' "$1" "$2" "$v"; done
		shift 2
	done | xargs -0 -P "$PARALLEL" -n 3 sh -c 'exec "$0" run "$1" "$2" "$3"' "$0" > "$out/results.raw.tsv"
	sort -t$'\t' -k1,1V -k2,2V "$out/results.raw.tsv" > "$out/results.tsv"
	summary "$out/results.tsv" | tee "$out/summary.txt"
}

libs() { # outdir libsdir: every mod jar in libsdir, each with its -probe.jar
	local out=$1 dir jar pairs=()
	dir=$(cd "$2" && pwd) || die "no directory $2"
	for jar in "$dir"/*.jar; do
		[[ $jar == *-probe.jar || $jar == *-sources.jar ]] && continue
		[[ -f ${jar%.jar}-probe.jar ]] || die "no probe jar for $jar"
		pairs+=("$jar" "${jar%.jar}-probe.jar")
	done
	matrix "$out" "${pairs[@]}"
}

summary() { # results.tsv
	local v pass bad missing=0
	bad=$(awk -F'\t' '$3 != "PASS" && $3 != "REFUSED"' "$1")
	for v in "${ALL_VERSIONS[@]}"; do
		pass=$(awk -F'\t' -v v="$v" '$2 == v && $3 == "PASS" {printf "%s ", $1}' "$1")
		[[ $pass ]] || missing=1
		printf '%-8s %s\n' "$v" "${pass:-NO PASSING JAR}"
	done
	[[ $bad ]] && printf 'problems:\n%s\n' "$bad"
	if [[ $bad ]] || (( missing )); then
		echo "matrix: FAILED"
		return 1
	fi
	echo "matrix: passed, $(wc -l < "$1") cells"
}

case ${1:-} in
	fetch) shift; [[ $# -gt 0 ]] || set -- "${ALL_VERSIONS[@]}"; for v in "$@"; do fetch_one "$v"; done ;;
	run) shift; run_one "$@" ;;
	matrix) shift; matrix "$@" ;;
	libs) shift; libs "$@" ;;
	*) sed -n '2,26p' "$0" ;;
esac
