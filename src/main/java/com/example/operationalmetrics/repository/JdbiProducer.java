package com.example.operationalmetrics.repository;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.jackson2.Jackson2Plugin;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

@ApplicationScoped
public class JdbiProducer {

    /**
     * Produces a singleton {@link Jdbi} as an {@code @ApplicationScoped} bean
     * (a CDI normal scope). The normal scope is required so that
     * {@code @InjectMock Jdbi} works in {@code @QuarkusTest}-based tests.
     */
    @Produces
    @ApplicationScoped
    public Jdbi jdbi(AgroalDataSource dataSource) {
        return Jdbi.create(dataSource)
                .installPlugin(new SqlObjectPlugin())
                .installPlugin(new PostgresPlugin())
                .installPlugin(new Jackson2Plugin());
    }
}
