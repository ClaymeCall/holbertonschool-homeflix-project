package com.homeflix.library.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.homeflix.library.storage.MediaAccessDeniedException;
import com.homeflix.library.storage.MediaAccessDeniedException.Reason;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

class ApiExceptionHandlerTest {

	record Body(@NotBlank(message = "Enter a title.") String title) {
	}

	@RestController
	static class Thrower {

		@PostMapping("/valid")
		String valid(@Valid @RequestBody Body body) {
			return body.title();
		}

		@GetMapping("/timeout")
		String timeout() {
			throw new ResourceAccessException("I/O error on GET http://identity-service:8081/ping",
					new SocketTimeoutException("secret-host:8081 timed out"));
		}

		@GetMapping("/dependency-500")
		String dependencyFailed() {
			throw HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "boom at com.acme.Secret", null,
					null, null);
		}

		@GetMapping("/database-down")
		String databaseDown() {
			throw new DataAccessResourceFailureException("Failed to obtain JDBC Connection to jdbc:postgresql://secret-db:5432/library");
		}

		@GetMapping("/database-slow")
		String databaseSlow() {
			throw new QueryTimeoutException("select * from media_file where path = '/secret/path'");
		}

		@GetMapping("/unavailable")
		String unavailable() {
			throw new ServiceUnavailableException("identity down", new RuntimeException("jdbc:postgresql://db:5432"));
		}

		@GetMapping("/missing-file")
		String missingFile() {
			throw new MediaAccessDeniedException(Reason.NOT_FOUND, null);
		}

		@GetMapping("/outside-roots")
		String outsideRoots() {
			throw new MediaAccessDeniedException(Reason.OUTSIDE_ROOTS, null);
		}

		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("NullPointerException at /srv/app/Secret.java:42");
		}

	}

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new Thrower()).setControllerAdvice(new ApiExceptionHandler()).build();
	}

	@Test
	void invalidInputGivesValidationFailedWithFields() throws Exception {
		mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.message").value("Some fields are invalid."))
				.andExpect(jsonPath("$.fields.title").value("Enter a title."));
	}

	@Test
	void malformedJsonGivesInvalidRequestWithoutDetails() throws Exception {
		mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"))
				.andExpect(jsonPath("$.fields").doesNotExist())
				.andExpect(content().string(not(containsString("Exception"))));
	}

	@Test
	void aTimeoutOrRefusedConnectionToAnotherServiceIs503() throws Exception {
		mvc.perform(get("/timeout"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("service_unavailable"))
				.andExpect(jsonPath("$.message").value(ApiExceptionHandler.SERVICE_UNAVAILABLE_MESSAGE))
				.andExpect(content().string(not(containsString("secret-host"))))
				.andExpect(content().string(not(containsString("identity-service"))));
	}

	@Test
	void aDatabaseThatIsDownOrTooSlowIs503WithoutDetails() throws Exception {
		for (String address : new String[] { "/database-down", "/database-slow" }) {
			mvc.perform(get(address))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.error").value("service_unavailable"))
					.andExpect(jsonPath("$.message").value(ApiExceptionHandler.SERVICE_UNAVAILABLE_MESSAGE))
					.andExpect(content().string(not(containsString("secret"))))
					.andExpect(content().string(not(containsString("jdbc"))))
					.andExpect(content().string(not(containsString("select"))));
		}
	}

	@Test
	void a5xxFromAnotherServiceIs503() throws Exception {
		mvc.perform(get("/dependency-500"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("service_unavailable"))
				.andExpect(content().string(not(containsString("boom"))))
				.andExpect(content().string(not(containsString("com.acme"))));
	}

	@Test
	void anExplicitServiceUnavailableExceptionIs503WithoutItsCause() throws Exception {
		mvc.perform(get("/unavailable"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("service_unavailable"))
				.andExpect(content().string(not(containsString("jdbc"))));
	}

	@Test
	void aMissingFileIs404FileNotFoundWithoutAPath() throws Exception {
		mvc.perform(get("/missing-file"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("file_not_found"))
				.andExpect(jsonPath("$.message").value(
						"The file for this title could not be found. It may have been moved or deleted from the library."));
	}

	@Test
	void aPathOutsideTheRootsIs403AccessDenied() throws Exception {
		mvc.perform(get("/outside-roots"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("access_denied"))
				.andExpect(jsonPath("$.message").value("You do not have access to this video."));
	}

	@Test
	void anUnexpectedErrorIs500WithoutTechnicalDetails() throws Exception {
		mvc.perform(get("/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("internal_error"))
				.andExpect(jsonPath("$.message").value("Something went wrong. Please try again."))
				.andExpect(content().string(not(containsString("Secret"))))
				.andExpect(content().string(not(containsString("NullPointer"))));
	}

	@Test
	void aFrameworkRejectionKeepsItsStatusWithAGenericMessage() throws Exception {
		mvc.perform(post("/timeout"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error").value("request_rejected"))
				.andExpect(jsonPath("$.message").value("This request cannot be processed."));
	}

}
