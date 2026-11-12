#!/bin/sh
set -eu

if [ "$#" -lt 1 ]; then
    echo "Usage: matchppl-update NEW_JAR [VERSION]" >&2
    exit 2
fi

new_jar=$1
version=${2:-unknown}
jar=/opt/matchppl/matchppl.jar
wait_seconds=60

if [ ! -f "$new_jar" ]; then
    echo "No jar found at $new_jar" >&2
    exit 1
fi

echo "Backing up the database..."
systemctl start matchppl-backup.service

if [ -f "$jar" ]; then
    install -o matchppl -g matchppl -m 600 "$jar" "$jar.previous"
fi
install -o matchppl -g matchppl -m 600 "$new_jar" "$jar"
rm -f "$new_jar"

started=$(date '+%Y-%m-%d %H:%M:%S')
echo "Restarting the bot (version $version)..."
systemctl restart matchppl

elapsed=0
while [ "$elapsed" -lt "$wait_seconds" ]; do
    if journalctl -u matchppl --since "$started" --no-pager -q | grep -q 'Bot is logged in'; then
        echo "Version $version is running."
        exit 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done

echo "The bot did not log in within $wait_seconds seconds. Last logs:" >&2
journalctl -u matchppl --since "$started" --no-pager -q | tail -n 20 >&2
if [ -f "$jar.previous" ]; then
    echo "Rolling back to the previous version..." >&2
    install -o matchppl -g matchppl -m 600 "$jar.previous" "$jar"
    systemctl restart matchppl
fi
exit 1
