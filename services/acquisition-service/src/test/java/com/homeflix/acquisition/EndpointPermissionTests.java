package com.homeflix.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.homeflix.acquisition.security.TestTokens;

import jakarta.servlet.http.Cookie;

/**
 * Every endpoint of the acquisition service is for administrators only, and refuses everybody else, with real
 * signed session tokens (K4; SEC-02, SEC-10, ACQ-05; R-12). The endpoints are found by scanning the application,
 * so a new endpoint that is missing from {@link #ENDPOINTS}, or not covered by {@code @AdminOnly}, fails this
 * test. Each request carries a valid body, so a refusal comes from the guard and not from validation.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EndpointPermissionTests {

	@DynamicPropertySource
	static void identityPublicKey(DynamicPropertyRegistry registry) {
		registry.add("homeflix.security.public-key-file",
				() -> TestTokens.writeTemporaryPem(TestTokens.toPem(TestTokens.identityPublicKey())));
	}

	/** Every endpoint, all "admin" in {@code docs/api/acquisition.yaml}. */
	private static final List<String> ENDPOINTS = List.of(
			"GET /entries",
			"GET /entries/{entryId}",
			"GET /downloads",
			"POST /downloads",
			"GET /downloads/{downloadId}",
			"POST /downloads/{downloadId}/cancel");

	private static final Map<String, String> BODIES = Map.of(
			"POST /downloads", "{\"entryId\":\"" + UUID.randomUUID() + "\"}");

	@Autowired
	private WebApplicationContext context;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void everyEndpointIsListed() {
		assertThat(new TreeSet<>(applicationEndpoints())).isEqualTo(new TreeSet<>(ENDPOINTS));
	}

	@Test
	void anonymousCallersAreRefusedEverywhere() throws Exception {
		expect(request -> request, 401);
		expect(request -> request.cookie(new Cookie("homeflix_token", "not-a-token")), 401);
	}

	@Test
	void aSignedInUserIsRefusedEverywhere() throws Exception {
		String user = TestTokens.user(UUID.randomUUID(), UUID.randomUUID());
		expect(request -> request.cookie(new Cookie("homeflix_token", user)), 403);
	}

	private void expect(Function<MockHttpServletRequestBuilder, MockHttpServletRequestBuilder> caller, int status)
			throws Exception {
		List<String> problems = new ArrayList<>();
		for (String endpoint : ENDPOINTS) {
			String[] parts = endpoint.split(" ", 2);
			String path = parts[1].replaceAll("\\{[^}]+\\}", UUID.randomUUID().toString());
			MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(parts[0]), path);
			String body = BODIES.get(endpoint);
			if (body != null) {
				request.contentType(MediaType.APPLICATION_JSON).content(body);
			}
			int answered = mvc.perform(caller.apply(request)).andReturn().getResponse().getStatus();
			if (answered != status) {
				problems.add(endpoint + " answered " + answered + " instead of " + status);
			}
		}
		assertThat(problems).isEmpty();
	}

	/** The endpoints of HomeFlix code, as "METHOD /path". Framework ones such as /error are left out. */
	private List<String> applicationEndpoints() {
		List<String> endpoints = new ArrayList<>();
		mappings.getHandlerMethods().forEach((info, handler) -> {
			if (!handler.getBeanType().getPackageName().startsWith("com.homeflix")) {
				return;
			}
			Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
			for (String path : info.getPatternValues()) {
				for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
					endpoints.add(method.name() + " " + path);
				}
			}
		});
		return endpoints;
	}

}
