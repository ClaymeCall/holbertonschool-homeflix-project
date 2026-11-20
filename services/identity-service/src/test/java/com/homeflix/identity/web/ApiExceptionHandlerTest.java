package com.homeflix.identity.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The outage answers of the error model ("When a service is down or slow"), without technical details (SEC-04). */
class ApiExceptionHandlerTest {

	private static final String MESSAGE = "This part of HomeFlix is temporarily unavailable. Please try again in a moment.";

	@RestController
	static class Thrower {

		@GetMapping("/library-down")
		String libraryDown() {
			throw new ServiceUnavailableException("library down", new RuntimeException("http://library-service:8082"));
		}

		@GetMapping("/database-down")
		String databaseDown() {
			throw new DataAccessResourceFailureException("Failed to obtain JDBC Connection to jdbc:postgresql://secret-db:5432/identity");
		}

		@GetMapping("/database-slow")
		String databaseSlow() {
			throw new QueryTimeoutException("select * from account where email = 'secret@example.com'");
		}

	}

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new Thrower()).setControllerAdvice(new ApiExceptionHandler()).build();
	}

	@Test
	void aServiceThatIsDownOrADatabaseThatIsDownOrSlowIs503WithoutDetails() throws Exception {
		for (String address : new String[] { "/library-down", "/database-down", "/database-slow" }) {
			mvc.perform(get(address))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.error").value("service_unavailable"))
					.andExpect(jsonPath("$.message").value(MESSAGE))
					.andExpect(content().string(not(containsString("secret"))))
					.andExpect(content().string(not(containsString("jdbc"))))
					.andExpect(content().string(not(containsString("library-service"))))
					.andExpect(content().string(not(containsString("select"))));
		}
	}

}
