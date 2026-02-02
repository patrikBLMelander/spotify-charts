#!/usr/bin/env python3
"""
Script to copy image_url from Signe W04 to Walter W04 for matching tracks
"""
import json
import os
from pathlib import Path

# Paths
BASE_DIR = Path(__file__).parent.parent / "data"
SIGNE_W04 = BASE_DIR / "Signe" / "2026-W04.json"
WALTER_W04 = BASE_DIR / "Walter" / "2026-W04.json"

def load_json_file(filepath):
    """Load and parse a JSON file"""
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

def save_json_file(filepath, data):
    """Save data as formatted JSON"""
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

def main():
    # Load Signe W04 as the source
    if not SIGNE_W04.exists():
        print(f"Error: {SIGNE_W04} not found")
        return
    
    signe_data = load_json_file(SIGNE_W04)
    
    # Create a map of track_id -> image_url from Signe W04
    image_map = {}
    for entry in signe_data.get("entries", []):
        track_id = entry.get("track_id")
        image_url = entry.get("image_url")
        if track_id and image_url:
            image_map[track_id] = image_url
    
    print(f"Found {len(image_map)} tracks with images in Signe W04")
    
    # Load Walter W04
    if not WALTER_W04.exists():
        print(f"Error: {WALTER_W04} not found")
        return
    
    walter_data = load_json_file(WALTER_W04)
    entries = walter_data.get("entries", [])
    updated_count = 0
    
    print(f"\nProcessing {WALTER_W04.name}...")
    
    for entry in entries:
        track_id = entry.get("track_id")
        if track_id in image_map:
            current_image = entry.get("image_url")
            if not current_image or current_image.strip() == "":
                entry["image_url"] = image_map[track_id]
                updated_count += 1
                print(f"  Added image for track {track_id}: {entry.get('title', 'Unknown')}")
            else:
                print(f"  Track {track_id} ({entry.get('title', 'Unknown')}) already has an image, skipping")
    
    if updated_count > 0:
        save_json_file(WALTER_W04, walter_data)
        print(f"\n✓ Saved {WALTER_W04.name} with {updated_count} new images")
    else:
        print(f"\n- No updates needed for {WALTER_W04.name}")
    
    # Also check other Walter weeks
    walter_dir = BASE_DIR / "Walter"
    week_files = sorted(walter_dir.glob("2026-W*.json"))
    
    for week_file in week_files:
        if week_file.name == "2026-W04.json":
            continue  # Already processed
        
        print(f"\nProcessing {week_file.name}...")
        week_data = load_json_file(week_file)
        entries = week_data.get("entries", [])
        file_updated = False
        file_count = 0
        
        for entry in entries:
            track_id = entry.get("track_id")
            if track_id in image_map:
                current_image = entry.get("image_url")
                if not current_image or current_image.strip() == "":
                    entry["image_url"] = image_map[track_id]
                    file_updated = True
                    file_count += 1
                    print(f"  Added image for track {track_id}: {entry.get('title', 'Unknown')}")
        
        if file_updated:
            save_json_file(week_file, week_data)
            print(f"  ✓ Saved {week_file.name} with {file_count} new images")
            updated_count += file_count
        else:
            print(f"  - No updates needed for {week_file.name}")
    
    print(f"\n✓ Done! Updated {updated_count} entries total across all Walter files.")

if __name__ == "__main__":
    main()
