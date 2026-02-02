package com.top50.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ImportRequest {
    @NotBlank(message = "Playlist URL is required")
    private String playlistUrl;

    @NotBlank(message = "Week is required")
    @Pattern(regexp = "\\d{4}-W\\d{2}", message = "Week must be in format YYYY-Www")
    private String week;
}
