package com.top50.exception;

public class PlaylistNotFoundException extends RuntimeException {
    public PlaylistNotFoundException(String username) {
        super("No playlist found for user: " + username);
    }
}
