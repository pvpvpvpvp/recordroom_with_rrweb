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
}
