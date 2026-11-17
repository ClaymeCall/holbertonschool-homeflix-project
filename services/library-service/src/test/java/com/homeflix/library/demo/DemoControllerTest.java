package com.homeflix.library.demo;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

class DemoControllerTest {

	private MockRestServiceServer identity;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://identity-service:8081");
		identity = MockRestServiceServer.bindTo(builder).build();
		mvc = MockMvcBuilders.standaloneSetup(new DemoController(new IdentityClient(builder.build()))).build();
	}

	@Test
	void returnsTheIdentityServiceAnswer() throws Exception {
		identity.expect(requestTo("http://identity-service:8081/ping")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("{\"service\":\"identity-service\",\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

		mvc.perform(get("/demo/identity"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caller").value("library-service"))
				.andExpect(jsonPath("$.callee.service").value("identity-service"))
				.andExpect(jsonPath("$.callee.status").value("ok"));
		identity.verify();
	}

	@Test
	void identityFailureGivesAPlainLanguage503WithoutDetails() throws Exception {
		identity.expect(requestTo("http://identity-service:8081/ping")).andRespond(withServerError());

		mvc.perform(get("/demo/identity"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("service_unavailable"))
				.andExpect(content().string(Matchers.not(Matchers.containsString("identity-service"))))
				.andExpect(content().string(Matchers.not(Matchers.containsString("500"))));
	}

}
