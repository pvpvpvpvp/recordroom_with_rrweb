package com.example.recordroom.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordRepository extends JpaRepository<RecordEntity, String> {
}
