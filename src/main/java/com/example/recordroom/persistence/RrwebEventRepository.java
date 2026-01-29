package com.example.recordroom.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RrwebEventRepository extends Repository<RrwebEventEntity, Long> {

    RrwebEventEntity save(RrwebEventEntity e);

    @Query("select e from RrwebEventEntity e where e.recordId = :rid and (e.tsEpochMs > :ts or (e.tsEpochMs = :ts and e.seq > :seq)) order by e.tsEpochMs asc, e.seq asc")
    List<RrwebEventEntity> findAfter(@Param("rid") String recordId, @Param("ts") long ts, @Param("seq") long seq, Pageable pageable);

    @Query("select e from RrwebEventEntity e where e.recordId = :rid order by e.tsEpochMs asc, e.seq asc")
    List<RrwebEventEntity> findAllForRecord(@Param("rid") String recordId, Pageable pageable);

    @Query("select coalesce(sum(length(coalesce(e.payloadJson,''))), 0) from RrwebEventEntity e where e.recordId = :rid")
    long sumApproxBytesByRecordId(@Param("rid") String recordId);

    @Query("select coalesce(count(e), 0) from RrwebEventEntity e where (:fromTs is null or e.tsEpochMs >= :fromTs) and (:toTs is null or e.tsEpochMs <= :toTs)")
    long countInRange(@Param("fromTs") Long fromTs, @Param("toTs") Long toTs);

    @Query("select coalesce(sum(length(coalesce(e.payloadJson,''))), 0) from RrwebEventEntity e where (:fromTs is null or e.tsEpochMs >= :fromTs) and (:toTs is null or e.tsEpochMs <= :toTs)")
    long sumApproxBytesInRange(@Param("fromTs") Long fromTs, @Param("toTs") Long toTs);
}
