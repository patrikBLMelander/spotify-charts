package com.top50.service;

import com.top50.entity.Week;
import com.top50.repository.WeekRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WeekService {
    private final WeekRepository weekRepository;
    
    private static final WeekFields WEEK_FIELDS = WeekFields.ISO;
    private static final DateTimeFormatter ISO_WEEK_FORMATTER = DateTimeFormatter.ofPattern("yyyy-'W'ww");
    
    @Transactional
    public Week getOrCreateWeek(String isoFormat) {
        Optional<Week> existing = weekRepository.findByIsoFormat(isoFormat);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Parse ISO format (e.g., "2026-W05")
        String[] parts = isoFormat.split("-W");
        int year = Integer.parseInt(parts[0]);
        int weekNumber = Integer.parseInt(parts[1]);
        
        // Calculate start and end dates for the week
        // ISO week: Week 1 is the first week with at least 4 days in the year
        LocalDate jan4 = LocalDate.of(year, 1, 4);
        int dayOfWeek = jan4.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday
        LocalDate week1Start = jan4.minusDays(dayOfWeek - 1);
        
        LocalDate startDate = week1Start.plusWeeks(weekNumber - 1);
        LocalDate endDate = startDate.plusDays(6);
        
        Week week = new Week();
        week.setId(UUID.randomUUID().toString());
        week.setWeekYear(year);
        week.setWeekNumber(weekNumber);
        week.setStartDate(startDate);
        week.setEndDate(endDate);
        week.setIsoFormat(isoFormat);
        week.setCreatedAt(java.time.LocalDateTime.now());
        
        return weekRepository.save(week);
    }
    
    public Optional<Week> findByIsoFormat(String isoFormat) {
        return weekRepository.findByIsoFormat(isoFormat);
    }
}
