package com.top50.repository;

import com.top50.entity.Week;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WeekRepository extends JpaRepository<Week, String> {
    Optional<Week> findByIsoFormat(String isoFormat);
}
