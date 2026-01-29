package com.example.recordroom.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NetworkEventRepository extends Repository<NetworkEventEntity, Long> {

    NetworkEventEntity save(NetworkEventEntity e);

    @Query("select e from NetworkEventEntity e where e.recordId = :rid and (e.startedAtEpochMs > :ts or (e.startedAtEpochMs = :ts and e.seq > :seq)) order by e.startedAtEpochMs asc, e.seq asc")
    List<NetworkEventEntity> findAfter(@Param("rid") String recordId, @Param("ts") long ts, @Param("seq") long seq, Pageable pageable);

    @Query("select e from NetworkEventEntity e where e.recordId = :rid and e.status >= :statusMin and (e.startedAtEpochMs > :ts or (e.startedAtEpochMs = :ts and e.seq > :seq)) order by e.startedAtEpochMs asc, e.seq asc")
    List<NetworkEventEntity> findAfterWithStatusMin(@Param("rid") String recordId, @Param("statusMin") int statusMin, @Param("ts") long ts, @Param("seq") long seq, Pageable pageable);

    @Query("select e from NetworkEventEntity e where e.recordId = :rid order by e.startedAtEpochMs desc, e.seq desc")
    List<NetworkEventEntity> findLatest(@Param("rid") String recordId, Pageable pageable);

    @Query("select e from NetworkEventEntity e where e.recordId = :rid and e.eventId = :eid")
    NetworkEventEntity findByRecordIdAndEventId(@Param("rid") String recordId, @Param("eid") String eventId);

    @Query("select e from NetworkEventEntity e where e.recordId = :rid and (lower(e.url) like lower(concat('%', :q, '%')) or lower(e.method) like lower(concat('%', :q, '%'))) order by e.startedAtEpochMs desc, e.seq desc")
    List<NetworkEventEntity> search(@Param("rid") String recordId, @Param("q") String query, Pageable pageable);

    @Query("select count(e) from NetworkEventEntity e where e.recordId = :rid and e.status >= 400")
    long countHttpErrors(@Param("rid") String recordId);

    @Query("select count(e) from NetworkEventEntity e where e.recordId = :rid and e.durationMs > 2000")
    long countSlow(@Param("rid") String recordId);

    @Query("select coalesce(sum(length(coalesce(e.method,'')) + length(coalesce(e.url,'')) + length(coalesce(e.clientRequestId,'')) + length(coalesce(e.requestHeadersJson,'')) + length(coalesce(e.requestBody,'')) + length(coalesce(e.responseHeadersJson,'')) + length(coalesce(e.responseBody,'')) + length(coalesce(e.error,''))), 0) from NetworkEventEntity e where e.recordId = :rid")
    long sumApproxBytesByRecordId(@Param("rid") String recordId);

    @Query("select coalesce(count(e), 0) from NetworkEventEntity e where (:fromTs is null or e.startedAtEpochMs >= :fromTs) and (:toTs is null or e.startedAtEpochMs <= :toTs)")
    long countInRange(@Param("fromTs") Long fromTs, @Param("toTs") Long toTs);

    @Query("select coalesce(sum(length(coalesce(e.method,'')) + length(coalesce(e.url,'')) + length(coalesce(e.clientRequestId,'')) + length(coalesce(e.requestHeadersJson,'')) + length(coalesce(e.requestBody,'')) + length(coalesce(e.responseHeadersJson,'')) + length(coalesce(e.responseBody,'')) + length(coalesce(e.error,''))), 0) from NetworkEventEntity e where (:fromTs is null or e.startedAtEpochMs >= :fromTs) and (:toTs is null or e.startedAtEpochMs <= :toTs)")
    long sumApproxBytesInRange(@Param("fromTs") Long fromTs, @Param("toTs") Long toTs);

    @Query("select e from NetworkEventEntity e where (:fromTs is null or e.startedAtEpochMs >= :fromTs) and (:toTs is null or e.startedAtEpochMs <= :toTs) order by e.startedAtEpochMs desc, e.seq desc")
    List<NetworkEventEntity> findRecentInRange(@Param("fromTs") Long fromTs, @Param("toTs") Long toTs, Pageable pageable);
}
