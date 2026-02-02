#!/usr/bin/env python3
"""
Script to fetch track information from Spotify API and update JSON file
Requires: pip install spotipy
Usage: python fetch_track_info.py <track_id>
"""

import sys
import json
import spotipy
from spotipy.oauth2 import SpotifyClientCredentials

# You'll need to set these environment variables or replace with your credentials
# export SPOTIPY_CLIENT_ID='your_client_id'
# export SPOTIPY_CLIENT_SECRET='your_client_secret'

def fetch_track_info(track_id):
    """Fetch track information from Spotify API"""
    try:
        sp = spotipy.Spotify(client_credentials_manager=SpotifyClientCredentials())
        track = sp.track(track_id)
        
        return {
            "title": track["name"],
            "artists": [artist["name"] for artist in track["artists"]],
            "spotify_url": track["external_urls"]["spotify"]
        }
    except Exception as e:
        print(f"Error fetching track {track_id}: {e}")
        return None

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python fetch_track_info.py <track_id>")
        sys.exit(1)
    
    track_id = sys.argv[1]
    info = fetch_track_info(track_id)
    
    if info:
        print(json.dumps(info, indent=2))
    else:
        sys.exit(1)
