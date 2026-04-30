package com.example.operationalmetrics.repository;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.jackson2.Jackson2Plugin;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

@ApplicationScoped
public class JdbiProducer {

    @Produces
    @Singleton
    public Jdbi jdbi(AgroalDataSource dataSource) {
        return Jdbi.create(dataSource)
                .installPlugin(new SqlObjectPlugin())
                .installPlugin(new PostgresPlugin())
                .installPlugin(new Jackson2Plugin());
    }
}
