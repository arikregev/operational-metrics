package com.example.operationalmetrics.service;

import com.example.operationalmetrics.config.HistoryConfig;
import com.example.operationalmetrics.repository.MetricsFetchLogDao;
import com.example.operationalmetrics.repository.MetricsHistoryDao;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryPurgeServiceTest {

    @Mock
    private Jdbi jdbi;

    @Mock
    private HistoryConfig historyConfig;

    @Mock
    private Handle handle;

    @Mock
    private MetricsHistoryDao historyDao;

    @Mock
    private MetricsFetchLogDao fetchLogDao;

    private HistoryPurgeService service;

    @BeforeEach
    void setUp() {
        service = new HistoryPurgeService(jdbi, historyConfig);
    }

    @Test
    void purge_callsDeleteOlderThanOnBothDaos() throws Exception {
        when(historyConfig.retentionDays()).thenReturn(30);

        when(handle.attach(MetricsHistoryDao.class)).thenReturn(historyDao);
        when(handle.attach(MetricsFetchLogDao.class)).thenReturn(fetchLogDao);
        when(historyDao.deleteOlderThan(30)).thenReturn(7);
        when(fetchLogDao.deleteOlderThan(30)).thenReturn(11);

        doAnswer(inv -> {
            HandleConsumer<RuntimeException> consumer = inv.getArgument(0);
            consumer.useHandle(handle);
            return null;
        }).when(jdbi).useTransaction(any(HandleConsumer.class));

        service.purge();

        verify(historyDao).deleteOlderThan(30);
        verify(fetchLogDao).deleteOlderThan(30);
    }

    @Test
    void purge_passesRetentionDaysFromConfig() throws Exception {
        when(historyConfig.retentionDays()).thenReturn(180);

        when(handle.attach(MetricsHistoryDao.class)).thenReturn(historyDao);
        when(handle.attach(MetricsFetchLogDao.class)).thenReturn(fetchLogDao);
        when(historyDao.deleteOlderThan(180)).thenReturn(0);
        when(fetchLogDao.deleteOlderThan(180)).thenReturn(0);

        doAnswer(inv -> {
            HandleConsumer<RuntimeException> consumer = inv.getArgument(0);
            consumer.useHandle(handle);
            return null;
        }).when(jdbi).useTransaction(any(HandleConsumer.class));

        service.purge();

        verify(historyDao).deleteOlderThan(180);
        verify(fetchLogDao).deleteOlderThan(180);
    }
}
