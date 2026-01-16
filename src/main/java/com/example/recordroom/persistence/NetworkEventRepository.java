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
}
