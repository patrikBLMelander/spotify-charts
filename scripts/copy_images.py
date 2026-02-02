#!/usr/bin/env python3
"""
Script to copy image_url from Signe W04 to all other weeks where the same tracks exist
"""
import json
import os
from pathlib import Path

# Path to Signe data directory
SIGNE_DIR = Path(__file__).parent.parent / "data" / "Signe"

def load_json_file(filepath):
    """Load and parse a JSON file"""
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

def save_json_file(filepath, data):
    """Save data as formatted JSON"""
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

def main():
    # Load W04 as the source
    w04_file = SIGNE_DIR / "2026-W04.json"
    if not w04_file.exists():
        print(f"Error: {w04_file} not found")
        return
    
    w04_data = load_json_file(w04_file)
    
    # Create a map of track_id -> image_url from W04
    image_map = {}
    for entry in w04_data.get("entries", []):
        track_id = entry.get("track_id")
        image_url = entry.get("image_url")
        if track_id and image_url:
            image_map[track_id] = image_url
    
    print(f"Found {len(image_map)} tracks with images in W04")
    
    # Process all other week files
    week_files = sorted(SIGNE_DIR.glob("2026-W*.json"))
    updated_count = 0
    
    for week_file in week_files:
        if week_file.name == "2026-W04.json":
            continue  # Skip the source file
        
        print(f"\nProcessing {week_file.name}...")
        week_data = load_json_file(week_file)
        entries = week_data.get("entries", [])
        file_updated = False
        
        for entry in entries:
            track_id = entry.get("track_id")
            if track_id in image_map:
                current_image = entry.get("image_url")
                if not current_image or current_image.strip() == "":
                    entry["image_url"] = image_map[track_id]
                    file_updated = True
                    updated_count += 1
                    print(f"  Added image for track {track_id}: {entry.get('title', 'Unknown')}")
        
        if file_updated:
            save_json_file(week_file, week_data)
            print(f"  ✓ Saved {week_file.name}")
        else:
            print(f"  - No updates needed for {week_file.name}")
    
    print(f"\n✓ Done! Updated {updated_count} entries across all files.")

if __name__ == "__main__":
    main()
