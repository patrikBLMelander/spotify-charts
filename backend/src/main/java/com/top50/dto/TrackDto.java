package com.top50.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackDto {
    private String id;
    private String title;
    private List<String> artists;
    private String spotifyUrl;
    private String imageUrl; // Album cover image URL
}
