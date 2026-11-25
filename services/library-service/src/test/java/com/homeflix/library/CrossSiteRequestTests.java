package com.homeflix.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.homeflix.library.security.TestTokens;
import com.homeflix.library.web.CrossSiteRequestFilter;

import jakarta.servlet.http.Cookie;

/**
 * Every state-changing endpoint of the library refuses a request that the browser marks as coming from another
 * site, even with a valid session (K5; SEC-08; ADR-0009, R-09). The session cookie is {@code SameSite=Strict};
 * this is the second line of defence, {@code CrossSiteRequestFilter}. The endpoints are found by scanning the
 * application, so a new one is covered without being listed here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CrossSiteRequestTests {

	@DynamicPropertySource
	static void identityPublicKey(DynamicPropertyRegistry registry) {
		registry.add("homeflix.security.public-key-file",
				() -> TestTokens.writeTemporaryPem(TestTokens.toPem(TestTokens.identityPublicKey())));
	}

	private static final Set<RequestMethod> SAFE = Set.of(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS);

	/** A valid body for the endpoints that read one, so validation never answers first. */
	private static final Map<String, String> BODIES = Map.of(
			"POST /roots", "{\"path\":\"/media/library\"}",
			"PUT /media/{mediaId}", "{\"title\":\"A title\"}",
			"PUT /progress/{mediaId}", "{\"positionSeconds\":10,\"durationSeconds\":100}",
			"PUT /internal/downloads/{entryId}", "{\"title\":\"A title\"}",
			"POST /internal/downloads/{entryId}/completed", "{\"relativePath\":\"film.mp4\"}");

	@Autowired
	private WebApplicationContext context;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	@Autowired
	private CrossSiteRequestFilter crossSiteRequestFilter;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		// MockMvc runs no servlet filter unless it is added: the application registers this one on every request.
		mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(crossSiteRequestFilter).build();
	}

	@Test
	void theScanFindsTheStateChangingEndpoints() {
		assertThat(stateChangingEndpoints()).contains("POST /roots", "PUT /favourites/{mediaId}",
				"DELETE /media/{mediaId}", "DELETE /internal/profiles/{profileId}/data").hasSize(13);
	}

	@Test
	void aCrossSiteOrSameSiteRequestIsRefusedEvenWithAValidSession() throws Exception {
		// An administrator who has chosen a profile: no guard would refuse this caller with 403.
		String session = TestTokens.token(TestTokens.IDENTITY_KEYS, "homeflix-identity", UUID.randomUUID(), true,
				UUID.randomUUID(), Instant.now(), Duration.ofMinutes(15));
		List<String> problems = new ArrayList<>();
		for (String site : List.of("cross-site", "same-site")) {
			for (String endpoint : stateChangingEndpoints()) {
				MockHttpServletResponse response = mvc.perform(build(endpoint)
						.cookie(new Cookie("homeflix_token", session)).header("Sec-Fetch-Site", site))
						.andReturn().getResponse();
				if (response.getStatus() != 403 || !response.getContentAsString().contains("\"error\":\"forbidden\"")) {
					problems.add(site + " " + endpoint + " answered " + response.getStatus());
				}
			}
		}
		assertThat(problems).isEmpty();
	}

	@Test
	void requestsFromHomeFlixItselfAreLetThrough() throws Exception {
		// Without a session, the guard answers: the filter did not stop the request.
		List<String> problems = new ArrayList<>();
		for (String site : List.of("same-origin", "none")) {
			for (String endpoint : stateChangingEndpoints()) {
				int status = mvc.perform(build(endpoint).header("Sec-Fetch-Site", site)).andReturn().getResponse()
						.getStatus();
				if (status != 401) {
					problems.add(site + " " + endpoint + " answered " + status + " instead of 401");
				}
			}
		}
		assertThat(problems).isEmpty();
	}

	private static MockHttpServletRequestBuilder build(String endpoint) {
		String[] parts = endpoint.split(" ", 2);
		MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(parts[0]),
				parts[1].replaceAll("\\{[^}]+\\}", UUID.randomUUID().toString()));
		String body = BODIES.get(endpoint);
		return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	/** The HomeFlix endpoints whose method is not GET, HEAD or OPTIONS, as "METHOD /path". */
	private List<String> stateChangingEndpoints() {
		List<String> endpoints = new ArrayList<>();
		mappings.getHandlerMethods().forEach((info, handler) -> {
			if (!handler.getBeanType().getPackageName().startsWith("com.homeflix")) {
				return;
			}
			for (RequestMethod method : info.getMethodsCondition().getMethods()) {
				if (!SAFE.contains(method)) {
					info.getPatternValues().forEach(path -> endpoints.add(method.name() + " " + path));
				}
			}
		});
		return endpoints;
	}

}
