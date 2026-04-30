package com.example.operationalmetrics.service;

import com.example.operationalmetrics.config.HistoryConfig;
import com.example.operationalmetrics.repository.MetricsFetchLogDao;
import com.example.operationalmetrics.repository.MetricsHistoryDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jdbi.v3.core.Jdbi;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HistoryPurgeService {

    private static final Logger LOG = Logger.getLogger(HistoryPurgeService.class);

    private final Jdbi jdbi;
    private final HistoryConfig historyConfig;

    @Inject
    public HistoryPurgeService(Jdbi jdbi, HistoryConfig historyConfig) {
        this.jdbi = jdbi;
        this.historyConfig = historyConfig;
    }

    public void purge() {
        int retentionDays = historyConfig.retentionDays();
        LOG.infov("Purging metrics history older than {0} days", retentionDays);

        jdbi.useTransaction(handle -> {
            int historyDeleted = handle.attach(MetricsHistoryDao.class).deleteOlderThan(retentionDays);
            int logsDeleted = handle.attach(MetricsFetchLogDao.class).deleteOlderThan(retentionDays);
            LOG.infov("Purged {0} history records and {1} fetch log records", historyDeleted, logsDeleted);
        });
    }
}
