package com.top50.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartEntryDto {
    private String week;
    private Integer position;
    private TrackDto track;
    private Integer previousPosition; // Position from previous week, null if new entry
}
