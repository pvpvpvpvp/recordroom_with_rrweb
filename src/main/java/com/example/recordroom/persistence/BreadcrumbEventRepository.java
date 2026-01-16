package com.example.recordroom.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BreadcrumbEventRepository extends Repository<BreadcrumbEventEntity, Long> {

    BreadcrumbEventEntity save(BreadcrumbEventEntity e);

    @Query("select e from BreadcrumbEventEntity e where e.recordId = :rid and (e.ts > :ts or (e.ts = :ts and e.seq > :seq)) order by e.ts asc, e.seq asc")
    List<BreadcrumbEventEntity> findAfter(@Param("rid") String recordId, @Param("ts") long ts, @Param("seq") long seq, Pageable pageable);

    @Query("select e from BreadcrumbEventEntity e where e.recordId = :rid order by e.ts desc, e.seq desc")
    List<BreadcrumbEventEntity> findLatest(@Param("rid") String recordId, Pageable pageable);

    @Query("select e from BreadcrumbEventEntity e where e.recordId = :rid and e.name = :name and (e.ts > :ts or (e.ts = :ts and e.seq > :seq)) order by e.ts asc, e.seq asc")
    List<BreadcrumbEventEntity> findAfterWithName(@Param("rid") String recordId, @Param("name") String name, @Param("ts") long ts, @Param("seq") long seq, Pageable pageable);
}
