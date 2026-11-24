package com.homeflix.library.progress;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.homeflix.library.LibraryServiceApplication;
import com.homeflix.library.security.Caller;
import com.homeflix.library.security.CallerProvider;

/**
 * Favourites and progress persist after a restart (PLY-10, OPS-08, TST-04, AS-08).
 *
 * <p>The test writes through the service, stops the service, restarts the PostgreSQL container itself (the
 * same operation as {@code docker compose restart postgres}), starts a new instance of the service on the same
 * database and reads the data back through the API, as the player does to resume.
 *
 * <p>Run it alone with, from {@code services/library-service}:
 * {@code ./mvnw test -Dtest=PersistenceAfterRestartTests}. It needs Docker, like the other database tests.
 */
class PersistenceAfterRestartTests {

	private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
	private static final UUID BOB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

	private static PostgreSQLContainer postgres;
	private static int hostPort;

	@BeforeAll
	static void startDatabase() throws IOException {
		// A fixed host port, so the address is the same after the container restarts (Docker may otherwise
		// publish a different random port).
		try (ServerSocket free = new ServerSocket(0)) {
			hostPort = free.getLocalPort();
		}
		postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
				.withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
						.withPortBindings(new PortBinding(Ports.Binding.bindPort(hostPort), new ExposedPort(5432))));
		postgres.start();
	}

	@AfterAll
	static void stopDatabase() {
		postgres.stop();
	}

	@Test
	void favouritesAndProgressSurviveARestartOfTheDatabaseAndOfTheService() throws Exception {
		UUID film;
		try (ConfigurableApplicationContext service = startService()) {
			JdbcTemplate jdbc = service.getBean(JdbcTemplate.class);
			film = jdbc.queryForObject(
					"insert into media_item (title, availability) values ('Film', 'UNAVAILABLE') returning id",
					UUID.class);
			MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) service).build();
			as(mvc, ALICE, put("/favourites/" + film)).andExpect(status().isNoContent());
			as(mvc, ALICE, put("/progress/" + film).contentType(MediaType.APPLICATION_JSON)
					.content("{\"positionSeconds\":321,\"durationSeconds\":1000}")).andExpect(status().isOk());
			as(mvc, BOB, put("/progress/" + film).contentType(MediaType.APPLICATION_JSON)
					.content("{\"positionSeconds\":7,\"durationSeconds\":1000}")).andExpect(status().isOk());
		}

		// docker restart of the database container, then wait until it accepts connections again.
		DockerClientFactory.lazyClient().restartContainerCmd(postgres.getContainerId()).withTimeout(10).exec();
		waitForDatabase();

		try (ConfigurableApplicationContext service = startService()) {
			MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) service).build();
			// The favourite is still there.
			as(mvc, ALICE, get("/favourites")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].id").value(film.toString()));
			// The title resumes from its saved position, and each profile keeps its own.
			as(mvc, ALICE, get("/progress/" + film)).andExpect(status().isOk())
					.andExpect(jsonPath("$.positionSeconds").value(321)).andExpect(jsonPath("$.durationSeconds").value(1000));
			as(mvc, BOB, get("/progress/" + film)).andExpect(status().isOk())
					.andExpect(jsonPath("$.positionSeconds").value(7));
			// And the other profile still has no favourite.
			as(mvc, BOB, get("/favourites")).andExpect(jsonPath("$.length()").value(0));
		}
	}

	private static ConfigurableApplicationContext startService() {
		return new SpringApplicationBuilder(LibraryServiceApplication.class, TestCallers.class)
				.web(WebApplicationType.SERVLET)
				.properties("server.port=0", "spring.datasource.url=" + postgres.getJdbcUrl(),
						"spring.datasource.username=" + postgres.getUsername(),
						"spring.datasource.password=" + postgres.getPassword())
				.run();
	}

	private static void waitForDatabase() throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (true) {
			try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
					postgres.getPassword())) {
				if (connection.isValid(2)) {
					return;
				}
			}
			catch (Exception e) {
				if (System.nanoTime() > deadline) {
					throw e;
				}
			}
			Thread.sleep(250);
		}
	}

	private static org.springframework.test.web.servlet.ResultActions as(MockMvc mvc, UUID profile,
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("X-Test-Caller", "user").header("X-Test-Profile", profile.toString()));
	}

	/**
	 * Same idea as {@code HeaderCallers}: the {@code X-Test-Profile} header names the profile of a signed-in
	 * user. It is declared here because the service is started by hand, not by the Spring test support.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class TestCallers {

		@Bean
		@Primary
		CallerProvider profileHeader() {
			return request -> Optional.of(new Caller(UUID.randomUUID(), false,
					UUID.fromString(request.getHeader("X-Test-Profile"))));
		}

	}

}
