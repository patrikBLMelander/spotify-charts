package com.top50.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackHistoryDto {
    private TrackDto track;
    private List<PositionPoint> history;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionPoint {
        private String week;
        private Integer position;
    }
}
