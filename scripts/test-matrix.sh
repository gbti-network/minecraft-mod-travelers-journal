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
#   test-matrix.sh upgrade <old-jar> <new-jar> <probe> <version> [config-dir]   one upgrade cell
#   test-matrix.sh upgrades <outdir> <new-jar> <probe> [version]   every past release, upgraded
#
# --widen tests a copy of the jar whose minecraft range is "*", to learn whether its code runs on
# a version its range excludes. A matrix passes when no cell is FAIL, CRASH or ENV and every
# version has a PASS.
#
# An upgrade cell is two boots on one world, as a server admin upgrades: the old jar first (with
# [config-dir] seeded as its config, else the defaults it writes), then the new jar in its place.
# UpgradeProbe.java holds the checks. "upgrades" takes every Traveler's Journal release Modrinth
# lists for [version] (default 1.21.1, where every release before 1.3.0 ran), plus any jars named
# in EXTRA_PAST, and upgrades each twice: with its own default config and with
# src/probe/fixtures/past-install.
#
# Linux only (WSL is fine). Servers live under ~/.cache/travelers-boot, on the
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
EXPECT_CHECKS=27
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
	[[ -p $fifo ]] || mkfifo "$fifo"
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

workdir() { # version name: a fresh server directory with the launcher, the EULA and Fabric API
	local d="$CACHE/mc/$1" work
	mkdir -p "$CACHE/runs"
	work=$(mktemp -d "$CACHE/runs/$2-$1-XXXX")
	mkdir -p "$work/mods" "$work/.fabric"
	cp "$d/fabric-server-launch.jar" "$work/"
	cp "$EULA_FROM" "$work/eula.txt"
	ln -s "$d/control/libraries" "$work/libraries"
	[[ -d $d/control/versions ]] && ln -s "$d/control/versions" "$work/versions"
	ln -s "$d/control/.fabric/server" "$work/.fabric/server"
	cp "$d/fabric-api.jar" "$work/mods/"
	write_props "$work" "$(helper port)"
	echo "$work"
}

# Prints nothing when <log> shows the probe finished with <expect> passes and no failure, error
# or crash report; otherwise one line naming every problem.
probe_problems() { # log expect workdir
	local log=$1 expect=$2 done_line err problems=()
	done_line=$(grep -m1 -oE '\[TJ-PROBE\] DONE pass=[0-9]+ fail=[0-9]+' "$log")
	if [[ -z $done_line ]]; then
		problems+=("probe never finished")
	elif [[ $done_line != *"pass=$expect fail=0" ]]; then
		problems+=("${done_line#*] }: $(grep -E '\[TJ-PROBE\] FAIL' "$log" | sed 's/.*\[TJ-PROBE\] FAIL //' | tr '\n' ';')")
	fi
	err=$(grep -m1 -E "$ERRORS" "$log" | oneline)
	[[ $err ]] && problems+=("$err")
	[[ -z "$(ls -A "$3/crash-reports" 2>/dev/null)" ]] || problems+=("crash report written")
	(( ${#problems[@]} )) && printf '%s; ' "${problems[@]}" | oneline
	return 0
}

run_one() { # jar probe version [--widen]
	local jar=$1 probe=$2 v=$3 widen=${4:-} d="$CACHE/mc/$3" start=$SECONDS
	local id mver range label work log verdict evidence ready=1 rc=0
	IFS=$'\t' read -r id mver range < <(helper modinfo "$jar")
	label=$mver
	[[ $widen ]] && label="$mver(widened)"
	result() { printf '%s\t%s\t%s\t%s\t%s\n' "$label" "$v" "$1" "$((SECONDS - start))" "$2"; }
	if [[ ! -f $d/control.ok ]]; then
		result ENV "control boot failed or not fetched: $d/control.log"
		return
	fi
	work=$(workdir "$v" journal)
	cp "$probe" "$work/mods/"
	if [[ $widen ]]; then helper widen "$jar" "$work/mods/$(basename "$jar")"; else cp "$jar" "$work/mods/"; fi
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
		evidence=$(probe_problems "$log" "$EXPECT_CHECKS" "$work")
		if [[ $evidence ]]; then
			verdict=FAIL
		else
			verdict=PASS
			evidence=$(grep -m1 -oE 'DONE pass=[0-9]+ fail=[0-9]+' "$log")
		fi
	fi
	if [[ ${TB_OUT:-} ]]; then
		mkdir -p "$TB_OUT/logs"
		cp "$log" "$TB_OUT/logs/$label-$v.log"
	fi
	result "$verdict" "$evidence"
}

upgrade_one() { # old-jar new-jar probe version [config-dir]
	local old=$1 new=$2 probe=$3 v=$4 fixture=${5:-} start=$SECONDS
	local over nver label tag work phase log rc problems expect
	IFS=$'\t' read -r _ over _ < <(helper modinfo "$old")
	IFS=$'\t' read -r _ nver _ < <(helper modinfo "$new")
	label="$over to $nver, $([[ $fixture ]] && echo customised || echo default) config"
	tag="upgrade-$over-to-$nver-$([[ $fixture ]] && echo customised || echo default)"
	result() { printf '%s\t%s\t%s\t%s\t%s\n' "$label" "$v" "$1" "$((SECONDS - start))" "$2"; }
	if [[ ! -f $CACHE/mc/$v/control.ok ]]; then
		result ENV "control boot failed or not fetched: $CACHE/mc/$v/control.log"
		return
	fi
	work=$(workdir "$v" journal-upgrade)
	mkdir -p "$work/config/travelers-journal" "$work/replaced-mods"
	[[ $fixture ]] && cp "$fixture"/* "$work/config/travelers-journal/"
	cp "$old" "$probe" "$work/mods/"
	for phase in before after; do
		echo "upgrade-$phase" > "$work/config/travelers-journal-probe.txt"
		log="$work/$phase.log"
		rc=0
		boot "$work" "$log" "until:[TJ-PROBE] DONE" "sleep:1" || rc=$?
		if [[ ${TB_OUT:-} ]]; then
			mkdir -p "$TB_OUT/logs"
			cp "$log" "$TB_OUT/logs/$tag-$v-$phase.log"
		fi
		if grep -qE 'Incompatible mods? (found|set)' "$log"; then
			result REFUSED "$phase: $(grep -m1 -E 'requires' "$log" | sed 's/^[[:space:]]*- //' | oneline)"
			return
		fi
		(( rc == 1 )) && { result CRASH "$phase: never ready: $(grep -m1 -E "$ERRORS" "$log" | oneline)"; return; }
		(( rc == 2 )) && { result CRASH "$phase: crashed after ready: $(grep -m1 -E "$ERRORS" "$log" | oneline)"; return; }
		expect=$([[ $phase == before ]] && echo 2 || echo 8)
		problems=$(probe_problems "$log" "$expect" "$work")
		[[ $problems ]] && { result FAIL "$phase: $problems"; return; }
		# Replace the jar the way an admin would; the old one is moved aside, not deleted
		if [[ $phase == before ]]; then
			mv "$work/mods/$(basename "$old")" "$work/replaced-mods/"
			cp "$new" "$work/mods/"
		fi
	done
	result PASS "both boots clean: 2 checks on the old jar, 8 after the upgrade"
}

upgrades() { # outdir new-jar probe [version]
	local out=$1 new=$2 probe=$3 v=${4:-1.21.1} nver jar bad
	local -a past
	mkdir -p "$out"
	export TB_OUT=$out
	fetch_one "$v" > "$out/fetch.txt"
	IFS=$'\t' read -r _ nver _ < <(helper modinfo "$new")
	mapfile -t past < <(helper past "$v" "$CACHE/past/$v" "$nver")
	# shellcheck disable=SC2206 # EXTRA_PAST is a space-separated list of jar paths
	past+=(${EXTRA_PAST:-})
	(( ${#past[@]} )) || die "no past release found for $v"
	export U_NEW=$new U_PROBE=$probe U_V=$v U_FIXTURE="$HERE/../src/probe/fixtures/past-install"
	for jar in "${past[@]}"; do printf '%s\0%s\0' "$jar" default "$jar" customised; done |
		xargs -0 -P "$PARALLEL" -n 2 sh -c '
			if [ "$2" = customised ]; then exec "$0" upgrade "$1" "$U_NEW" "$U_PROBE" "$U_V" "$U_FIXTURE"; fi
			exec "$0" upgrade "$1" "$U_NEW" "$U_PROBE" "$U_V"' "$0" > "$out/upgrades.raw.tsv"
	sort -t$'\t' -k1,1V "$out/upgrades.raw.tsv" > "$out/upgrades.tsv"
	cut -f1-3 "$out/upgrades.tsv"
	bad=$(awk -F'\t' '$3 != "PASS"' "$out/upgrades.tsv")
	if [[ $bad ]]; then
		printf 'problems:\n%s\nupgrades: FAILED\n' "$bad"
		return 1
	fi
	echo "upgrades: passed, $(wc -l < "$out/upgrades.tsv") cells"
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
	upgrade) shift; upgrade_one "$@" ;;
	upgrades) shift; upgrades "$@" ;;
	*) sed -n '2,36p' "$0" ;;
esac
