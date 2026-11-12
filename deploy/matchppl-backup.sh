#!/bin/sh
set -eu

DATABASE=/opt/matchppl/matchppl.db
DESTINATION=/var/backups/matchppl
RECIPIENT=/etc/matchppl/backup-recipient.txt
KEEP_DAYS=14

workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

sqlite3 "$DATABASE" ".backup '$workdir/matchppl.db'"
if [ "$(sqlite3 "$workdir/matchppl.db" 'PRAGMA integrity_check;')" != "ok" ]; then
    echo "Integrity check failed: backup not kept" >&2
    exit 1
fi

target="$DESTINATION/matchppl-$(date -u +%Y-%m-%dT%H%M%SZ).db.age"
age --encrypt --recipients-file "$RECIPIENT" --output "$target.partial" "$workdir/matchppl.db"
mv "$target.partial" "$target"
find "$DESTINATION" -name 'matchppl-*.db.age' -mtime +"$KEEP_DAYS" -delete
echo "Backup written: $target"
