package com.top50.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException e) {
        log.warn("User not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("USER_NOT_FOUND", e.getMessage()));
    }
    
    @ExceptionHandler(WeekNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWeekNotFound(WeekNotFoundException e) {
        log.warn("Week not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("WEEK_NOT_FOUND", e.getMessage()));
    }
    
    @ExceptionHandler(PlaylistNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlaylistNotFound(PlaylistNotFoundException e) {
        log.warn("Playlist not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PLAYLIST_NOT_FOUND", e.getMessage()));
    }
    
    @ExceptionHandler(DataDeletionException.class)
    public ResponseEntity<ErrorResponse> handleDataDeletion(DataDeletionException e) {
        log.error("Data deletion failed: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("DELETION_FAILED", e.getMessage()));
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INPUT", e.getMessage()));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
    
    public static class ErrorResponse {
        private String error;
        private String message;
        
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
        
        public String getError() {
            return error;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
