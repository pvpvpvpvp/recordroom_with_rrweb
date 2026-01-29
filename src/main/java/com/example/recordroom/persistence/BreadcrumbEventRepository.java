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

    @Query("select e from BreadcrumbEventEntity e where e.recordId = :rid and (lower(e.message) like lower(concat('%', :q, '%')) or lower(e.name) like lower(concat('%', :q, '%'))) order by e.ts desc, e.seq desc")
    List<BreadcrumbEventEntity> search(@Param("rid") String recordId, @Param("q") String query, Pageable pageable);

    @Query("select coalesce(sum(length(coalesce(e.name,'')) + length(coalesce(e.message,'')) + length(coalesce(e.dataJson,''))), 0) from BreadcrumbEventEntity e where e.recordId = :rid")
    long sumApproxBytesByRecordId(@Param("rid") String recordId);

    @Query("select coalesce(count(e), 0) from BreadcrumbEventEntity e where (:fromTs is null or e.ts >= :fromTs) and (:toTs is null or e.ts <= :toTs)")
    long countInRange(@Param("fromTs") Long fromTs, @Param("toTs") Long toTs);

    @Query("select coalesce(sum(length(coalesce(e.name,'')) + length(coalesce(e.message,'')) + length(coalesce(e.dataJson,''))), 0) from BreadcrumbEventEntity e where (:fromTs is null or e.ts >= :fromTs) and (:toTs is null or e.ts <= :toTs)")
    long sumApproxBytesInRange(@Param("fromTs") Long fromTs, @Param("toTs") Long toTs);
}
