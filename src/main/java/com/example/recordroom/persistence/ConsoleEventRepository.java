package com.example.recordroom.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConsoleEventRepository extends Repository<ConsoleEventEntity, Long> {

    ConsoleEventEntity save(ConsoleEventEntity e);

    @Query("select e from ConsoleEventEntity e where e.recordId = :rid and (e.ts > :ts or (e.ts = :ts and e.seq > :seq)) order by e.ts asc, e.seq asc")
    List<ConsoleEventEntity> findAfter(@Param("rid") String recordId, @Param("ts") long ts, @Param("seq") long seq, Pageable pageable);

    @Query("select e from ConsoleEventEntity e where e.recordId = :rid order by e.ts desc, e.seq desc")
    List<ConsoleEventEntity> findLatest(@Param("rid") String recordId, Pageable pageable);

    @Query("select e from ConsoleEventEntity e where e.recordId = :rid and e.level = :level and (e.ts > :ts or (e.ts = :ts and e.seq > :seq)) order by e.ts asc, e.seq asc")
    List<ConsoleEventEntity> findAfterWithLevel(@Param("rid") String recordId, @Param("level") String level, @Param("ts") long ts, @Param("seq") long seq, Pageable pageable);

    @Query("select e from ConsoleEventEntity e where e.recordId = :rid and (lower(e.message) like lower(concat('%', :q, '%')) or (e.stack is not null and lower(e.stack) like lower(concat('%', :q, '%')))) order by e.ts desc, e.seq desc")
    List<ConsoleEventEntity> search(@Param("rid") String recordId, @Param("q") String query, Pageable pageable);

    @Query("select count(e) from ConsoleEventEntity e where e.recordId = :rid and lower(e.level) = 'error'")
    long countErrors(@Param("rid") String recordId);

    @Query("select count(e) from ConsoleEventEntity e where e.recordId = :rid and lower(e.level) = 'warn'")
    long countWarns(@Param("rid") String recordId);

    @Query("select coalesce(sum(length(coalesce(e.message,'')) + length(coalesce(e.stack,'')) + length(coalesce(e.level,''))), 0) from ConsoleEventEntity e where e.recordId = :rid")
    long sumApproxBytesByRecordId(@Param("rid") String recordId);

    @Query("select coalesce(count(e), 0) from ConsoleEventEntity e where (:fromTs is null or e.ts >= :fromTs) and (:toTs is null or e.ts <= :toTs)")
    long countInRange(@Param("fromTs") Long fromTs, @Param("toTs") Long toTs);

    @Query("select coalesce(sum(length(coalesce(e.message,'')) + length(coalesce(e.stack,'')) + length(coalesce(e.level,''))), 0) from ConsoleEventEntity e where (:fromTs is null or e.ts >= :fromTs) and (:toTs is null or e.ts <= :toTs)")
    long sumApproxBytesInRange(@Param("fromTs") Long fromTs, @Param("toTs") Long toTs);
}
