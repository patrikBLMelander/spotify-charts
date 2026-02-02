#!/bin/bash
# Script to fetch track information from backend API and update JSON file
# Usage: ./update_track_info.sh <track_id> <json_file_path>

if [ $# -lt 2 ]; then
    echo "Usage: $0 <track_id> <json_file_path>"
    exit 1
fi

TRACK_ID=$1
JSON_FILE=$2

# Fetch track info from backend API
TRACK_INFO=$(curl -s "http://localhost:8080/api/tracks/${TRACK_ID}/info")

if [ $? -eq 0 ] && [ -n "$TRACK_INFO" ]; then
    echo "Track info for ${TRACK_ID}:"
    echo "$TRACK_INFO" | jq '.'
else
    echo "Failed to fetch track info for ${TRACK_ID}"
    exit 1
fi
