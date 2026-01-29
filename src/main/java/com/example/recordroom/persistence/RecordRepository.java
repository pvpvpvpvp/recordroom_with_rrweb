package com.example.recordroom.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecordRepository extends JpaRepository<RecordEntity, String> {

    @Query("select r from RecordEntity r where r.sessionId = :sid order by r.createdAtEpochMs asc")
    List<RecordEntity> findBySessionId(@Param("sid") String sessionId);

    @Query("select r from RecordEntity r order by r.createdAtEpochMs desc")
    List<RecordEntity> findLatest(Pageable pageable);

    @Query("select count(distinct r.sessionId) from RecordEntity r")
    long countDistinctSessionIds();

    // Find a record whose previousRecordId points to the given recordId (used to infer "next session")
    RecordEntity findFirstByPreviousRecordId(String previousRecordId);
}
