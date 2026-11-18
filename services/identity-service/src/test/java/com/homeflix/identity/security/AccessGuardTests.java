package com.homeflix.identity.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.identity.session.SessionClaims;
import com.homeflix.identity.session.SessionKeys;
import com.homeflix.identity.session.TestKeys;
import com.homeflix.identity.session.TokenService;
import com.homeflix.identity.web.ApiExceptionHandler;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Server-side role checks (G5, SEC-02, TST-02): anonymous, user on an administrator operation,
 * valid administrator. A probe controller stands for the real endpoints, which all start with
 * the same {@link AccessGuard} calls.
 */
class AccessGuardTests {

	private static final SessionKeys KEYS = SessionKeys.fromKeyText(TestKeys.newPrivateKey(2048));
	private static final TokenService TOKENS = new TokenService(KEYS, Clock.systemUTC(), Duration.ofMinutes(15));

	private final UUID accountId = UUID.randomUUID();
	private final MockMvc mvc = MockMvcBuilders
			.standaloneSetup(new ProbeController(new AccessGuard(new TokenCallerProvider(TOKENS))))
			.setControllerAdvice(new ApiExceptionHandler())
			.build();

	@RestController
	static class ProbeController {

		private final AccessGuard guard;

		ProbeController(AccessGuard guard) {
			this.guard = guard;
		}

		@GetMapping("/probe/account")
		Map<String, Object> accountOperation(HttpServletRequest request) {
			Caller caller = guard.requireAccount(request);
			return Map.of("accountId", caller.accountId().toString(), "admin", caller.admin());
		}

		@PostMapping("/probe/admin")
		Map<String, Object> adminOperation(HttpServletRequest request) {
			Caller caller = guard.requireAdmin(request);
			return Map.of("accountId", caller.accountId().toString());
		}

	}

	@Test
	void anonymousRequestIsRefusedEverywhere() throws Exception {
		expectUnauthenticated(mvc.perform(get("/probe/account")));
		expectUnauthenticated(mvc.perform(post("/probe/admin")));
	}

	@Test
	void userCanDoAccountOperations() throws Exception {
		mvc.perform(withToken(get("/probe/account"), TOKENS.issue(new SessionClaims(accountId, false, null))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value(accountId.toString()))
				.andExpect(jsonPath("$.admin").value(false));
	}

	@Test
	void userOnAnAdministratorOperationIsForbidden() throws Exception {
		mvc.perform(withToken(post("/probe/admin"), TOKENS.issue(new SessionClaims(accountId, false, null))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"))
				.andExpect(jsonPath("$.message").value("You do not have permission to do this."));
	}

	@Test
	void validAdministratorIsAllowed() throws Exception {
		mvc.perform(withToken(post("/probe/admin"), TOKENS.issue(new SessionClaims(accountId, true, null))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value(accountId.toString()));
	}

	@Test
	void forgedAdministratorTokenIsRefused() throws Exception {
		expectUnauthenticated(mvc.perform(withToken(post("/probe/admin"), new TokenService(SessionKeys.fromKeyText(TestKeys.newPrivateKey(2048)), Clock.systemUTC(),
				Duration.ofMinutes(15)).issue(new SessionClaims(accountId, true, null)))));
	}

	@Test
	void expiredAdministratorTokenIsRefused() throws Exception {
		expectUnauthenticated(mvc.perform(withToken(post("/probe/admin"), new TokenService(KEYS, Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-20)),
				Duration.ofMinutes(15)).issue(new SessionClaims(accountId, true, null)))));
	}

	@Test
	void identityClaimedInHeadersOrParametersIsIgnored() throws Exception {
		// R-06, R-12: a caller cannot say who they are; only the signed cookie counts.
		expectUnauthenticated(mvc.perform(post("/probe/admin")
				.header("Authorization", "Bearer " + TOKENS.issue(new SessionClaims(accountId, true, null)))
				.header("X-Account-Id", accountId.toString())
				.header("X-Admin", "true")
				.param("accountId", accountId.toString())));
	}

	private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
		return request.cookie(new Cookie("homeflix_token", token));
	}

	private static void expectUnauthenticated(ResultActions actions) throws Exception {
		actions.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"))
				.andExpect(jsonPath("$.message").value("Please sign in."));
	}


}
