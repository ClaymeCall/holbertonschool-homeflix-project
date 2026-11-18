package com.homeflix.identity;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.homeflix.identity.session.TestKeys;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

	/**
	 * The service refuses to start without a signing key file. Tests use a key generated for
	 * this run only, in a temporary file, so no private key is ever committed (R-11).
	 */
	@Bean
	DynamicPropertyRegistrar sessionKeyProperty() {
		return registry -> registry.add("homeflix.session.private-key-file", () -> TestKeys.SESSION_PRIVATE_KEY_FILE);
	}

}
