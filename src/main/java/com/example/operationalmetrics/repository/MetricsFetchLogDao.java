package com.example.operationalmetrics.repository;

import com.example.operationalmetrics.model.MetricsFetchLog;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface MetricsFetchLogDao {

    @SqlUpdate("""
            INSERT INTO metrics_fetch_log (package_id, source, status, http_status, error_message, duration_ms, fetched_at)
            VALUES (:packageId, :source, :status, :httpStatus, :errorMessage, :durationMs, :fetchedAt)
            """)
    void insert(@BindBean MetricsFetchLog log);

    @SqlUpdate("DELETE FROM metrics_fetch_log WHERE fetched_at < now() - make_interval(days => :days)")
    int deleteOlderThan(@Bind("days") int retentionDays);
}
