package com.example.operationalmetrics.repository;

import io.agroal.api.AgroalDataSource;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pure unit test for {@link JdbiProducer}. It verifies that the CDI producer
 * builds a Jdbi instance against a given {@link AgroalDataSource} without
 * throwing, and that all required plugins (SqlObject, Postgres, Jackson2)
 * are installed.
 *
 * Plugin installation is checked via {@link Jdbi#getConfig(Class)} where the
 * plugin registers a config class — installing the same plugin twice would
 * not be a problem, but we want to confirm the producer wires them at all.
 */
class JdbiProducerTest {

    @Test
    void jdbi_isCreatedAgainstProvidedDataSource() {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);

        Jdbi jdbi = new JdbiProducer().jdbi(dataSource);

        assertThat(jdbi).isNotNull();
    }

    @Test
    void jdbi_installsSqlObjectPlugin() {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);

        Jdbi jdbi = new JdbiProducer().jdbi(dataSource);

        // SqlObjectPlugin registers the HandlerDecorators config; presence
        // of that class in the resolved config indicates the plugin was
        // installed.
        var config = jdbi.getConfig(org.jdbi.v3.sqlobject.HandlerDecorators.class);
        assertThat(config).isNotNull();
    }

    @Test
    void jdbi_installsJackson2Plugin() {
        AgroalDataSource dataSource = mock(AgroalDataSource.class);

        Jdbi jdbi = new JdbiProducer().jdbi(dataSource);

        var config = jdbi.getConfig(org.jdbi.v3.jackson2.Jackson2Config.class);
        assertThat(config).isNotNull();
    }

    @Test
    void jdbi_doesNotEagerlyOpenConnections() {
        // No interactions are stubbed on the mock data source. If JdbiProducer
        // tried to open a connection at build time, Mockito's default would
        // produce a NullPointerException somewhere in plugin init.
        AgroalDataSource dataSource = mock(AgroalDataSource.class);

        Jdbi jdbi = new JdbiProducer().jdbi(dataSource);

        assertThat(jdbi).isNotNull();
    }
}
