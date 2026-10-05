package com.vbforge.asknotes;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Same image as docker-compose, so the migration is tested against the real thing. */
    @Bean
    @ServiceConnection
    PostgreSQLContainer pgvector() {
        DockerImageName image = DockerImageName.parse("pgvector/pgvector:0.8.2-pg17")
                .asCompatibleSubstituteFor("postgres");
        return new PostgreSQLContainer(image);
    }
}