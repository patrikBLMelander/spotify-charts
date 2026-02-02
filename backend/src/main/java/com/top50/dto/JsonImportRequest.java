package com.top50.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class JsonImportRequest {
    @NotBlank(message = "Week is required")
    @Pattern(regexp = "\\d{4}-W\\d{2}", message = "Week must be in format YYYY-Www")
    private String week;

    @NotEmpty(message = "Entries are required")
    private List<ChartEntry> entries;

    @Data
    public static class ChartEntry {
        private Integer placement;
        
        @JsonProperty("track_id")
        private String trackId;
        
        private String title;
        private List<String> artists;
        
        @JsonProperty("spotify_url")
        private String spotifyUrl;
        
        @JsonProperty("image_url")
        private String imageUrl;
    }
}
