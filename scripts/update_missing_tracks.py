#!/usr/bin/env python3
"""
Script to fetch missing track information from Spotify API and update JSON file
Requires: pip install spotipy requests
Usage: python update_missing_tracks.py
"""

import json
import os
import sys
from pathlib import Path

# Try to use spotipy if available, otherwise use requests
try:
    import spotipy
    from spotipy.oauth2 import SpotifyClientCredentials
    USE_SPOTIPY = True
except ImportError:
    import requests
    USE_SPOTIPY = False

def fetch_track_info_spotipy(track_id):
    """Fetch track information using spotipy"""
    try:
        sp = spotipy.Spotify(client_credentials_manager=SpotifyClientCredentials())
        track = sp.track(track_id)
        
        return {
            "title": track["name"],
            "artists": [artist["name"] for artist in track["artists"]],
            "spotify_url": track["external_urls"]["spotify"]
        }
    except Exception as e:
        print(f"Error fetching track {track_id} with spotipy: {e}")
        return None

def fetch_track_info_api(track_id, client_id=None, client_secret=None):
    """Fetch track information using Spotify API directly"""
    try:
        # Get access token
        auth_url = "https://accounts.spotify.com/api/token"
        auth_data = {
            "grant_type": "client_credentials"
        }
        auth_headers = {}
        
        if client_id and client_secret:
            import base64
            credentials = f"{client_id}:{client_secret}"
            encoded_credentials = base64.b64encode(credentials.encode()).decode()
            auth_headers["Authorization"] = f"Basic {encoded_credentials}"
        
        auth_response = requests.post(auth_url, data=auth_data, headers=auth_headers)
        
        if auth_response.status_code != 200:
            print(f"Failed to get access token: {auth_response.text}")
            return None
        
        access_token = auth_response.json()["access_token"]
        
        # Get track info
        track_url = f"https://api.spotify.com/v1/tracks/{track_id}"
        track_headers = {"Authorization": f"Bearer {access_token}"}
        track_response = requests.get(track_url, headers=track_headers)
        
        if track_response.status_code != 200:
            print(f"Failed to get track info: {track_response.text}")
            return None
        
        track = track_response.json()
        
        return {
            "title": track["name"],
            "artists": [artist["name"] for artist in track["artists"]],
            "spotify_url": track["external_urls"]["spotify"]
        }
    except Exception as e:
        print(f"Error fetching track {track_id} with API: {e}")
        return None

def update_json_file(json_file_path):
    """Update JSON file with missing track information"""
    # Track IDs that need updating
    track_ids = [
        "4sOLtnLeIFwO4YUUSV5DM5",  # placement 43
        "0sCvjs8IVBhxXVhg7jQemt",  # placement 44
        "7MO7kNaCWOpslpUH0uIsvi",  # placement 45
        "0FIDCNYYjNvPVimz5icugS",  # placement 46
        "3ZdonRSIema00XbpqGWVLJ",  # placement 47
        "00N0RDX7n9W2LcgYQuECL3",  # placement 48
        "3rmo8F54jFF8OgYsqTxm5d",  # placement 49
        "5g7sDjBhZ4I3gcFIpkrLuI",  # placement 50
    ]
    
    # Read JSON file
    with open(json_file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    # Update entries
    updated_count = 0
    for entry in data["entries"]:
        if entry["track_id"] in track_ids and (entry["title"] == "—" or not entry["artists"]):
            print(f"Fetching info for track {entry['track_id']} (placement {entry['placement']})...")
            
            # Try to fetch track info
            if USE_SPOTIPY:
                track_info = fetch_track_info_spotipy(entry["track_id"])
            else:
                # Try to get credentials from environment
                client_id = os.getenv("SPOTIFY_CLIENT_ID")
                client_secret = os.getenv("SPOTIFY_CLIENT_SECRET")
                track_info = fetch_track_info_api(entry["track_id"], client_id, client_secret)
            
            if track_info:
                entry["title"] = track_info["title"]
                entry["artists"] = track_info["artists"]
                entry["spotify_url"] = track_info["spotify_url"]
                updated_count += 1
                print(f"  ✓ Updated: {track_info['title']} by {', '.join(track_info['artists'])}")
            else:
                print(f"  ✗ Failed to fetch info for track {entry['track_id']}")
    
    # Write updated JSON file
    if updated_count > 0:
        with open(json_file_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"\n✓ Updated {updated_count} tracks in {json_file_path}")
    else:
        print(f"\n✗ No tracks were updated")
    
    return updated_count

if __name__ == "__main__":
    # Default JSON file path
    json_file = Path(__file__).parent.parent / "data" / "Walter" / "2026-W04.json"
    
    if len(sys.argv) > 1:
        json_file = Path(sys.argv[1])
    
    if not json_file.exists():
        print(f"Error: JSON file not found: {json_file}")
        sys.exit(1)
    
    print(f"Updating tracks in: {json_file}")
    print("=" * 60)
    
    update_json_file(json_file)
