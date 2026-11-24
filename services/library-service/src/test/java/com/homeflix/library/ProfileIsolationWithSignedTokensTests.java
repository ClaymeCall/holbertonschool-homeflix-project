package com.homeflix.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.library.security.TestTokens;

import jakarta.servlet.http.Cookie;

/**
 * Profile isolation in the library with real signed session tokens (TST-02 phase M part, G11:
 * PRF-04, R-06, AS-05). Unlike {@code ProfileDataEndpointTests}, which uses {@code HeaderCallers},
 * this goes through the real {@code TokenCallerProvider}: the only thing a user controls is the
 * request, and the profile can only come from the signed cookie.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProfileIsolationWithSignedTokensTests {

	/** The service checks tokens with the public half of the test identity key pair. */
	@DynamicPropertySource
	static void identityPublicKey(DynamicPropertyRegistry registry) {
		registry.add("homeflix.security.public-key-file",
				() -> TestTokens.writeTemporaryPem(TestTokens.toPem(TestTokens.identityPublicKey())));
	}

	private final UUID family = UUID.randomUUID();
	private final UUID kids = UUID.randomUUID();
	private final UUID parents = UUID.randomUUID();
	private final UUID neighbour = UUID.randomUUID();
	private final UUID neighbourProfile = UUID.randomUUID();

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;
	private UUID film;
	private UUID other;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		jdbc.update("delete from favourite");
		jdbc.update("delete from progress");
		jdbc.update("delete from media_item");
		film = title("Film");
		other = title("Other");
	}

	@Test
	void twoProfilesOfTheSameAccountKeepSeparateData() throws Exception {
		String kidsToken = TestTokens.user(family, kids);
		String parentsToken = TestTokens.user(family, parents);
		as(kidsToken, put("/favourites/" + film)).andExpect(status().isNoContent());
		saveProgress(kidsToken, film, 120);

		as(parentsToken, get("/favourites")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		as(parentsToken, get("/progress")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		as(parentsToken, get("/progress/" + film)).andExpect(status().isNotFound());

		// Removing or resetting the same title from the other profile only touches that profile.
		as(parentsToken, delete("/favourites/" + film)).andExpect(status().isNoContent());
		as(parentsToken, delete("/progress/" + film)).andExpect(status().isNoContent());
		as(kidsToken, get("/favourites")).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(film.toString()));
		as(kidsToken, get("/progress/" + film)).andExpect(status().isOk())
				.andExpect(jsonPath("$.positionSeconds").value(120));
	}

	@Test
	void anotherAccountSeesNothing() throws Exception {
		String kidsToken = TestTokens.user(family, kids);
		as(kidsToken, put("/favourites/" + film)).andExpect(status().isNoContent());
		saveProgress(kidsToken, film, 120);

		String neighbourToken = TestTokens.user(neighbour, neighbourProfile);
		as(neighbourToken, get("/favourites")).andExpect(jsonPath("$.length()").value(0));
		as(neighbourToken, get("/progress")).andExpect(jsonPath("$.length()").value(0));
		as(neighbourToken, get("/progress/" + film)).andExpect(status().isNotFound());
	}

	@Test
	void profileNamedInTheRequestInsteadOfTheTokenIsIgnored() throws Exception {
		// Changing an id: in a parameter, in the body, in a header, even in the header the other
		// tests use for fake callers. Only the signed cookie says whose data it is.
		String kidsToken = TestTokens.user(family, kids);
		as(kidsToken, put("/favourites/" + film)).andExpect(status().isNoContent());
		saveProgress(kidsToken, film, 120);

		String parentsToken = TestTokens.user(family, parents);
		as(parentsToken, get("/favourites?profileId=" + kids)
				.header("X-Profile-Id", kids.toString())
				.header("X-Test-Caller", "user")
				.header("X-Test-Profile", kids.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		as(parentsToken, put("/progress/" + film + "?profileId=" + kids)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"positionSeconds\":5,\"durationSeconds\":1000,\"profileId\":\"" + kids + "\"}"))
				.andExpect(status().isOk());

		as(kidsToken, get("/progress/" + film)).andExpect(jsonPath("$.positionSeconds").value(120));
	}

	@Test
	void tokenOfAnotherProfileInAHeaderDoesNotReplaceTheCookie() throws Exception {
		String kidsToken = TestTokens.user(family, kids);
		as(kidsToken, put("/favourites/" + film)).andExpect(status().isNoContent());

		as(TestTokens.user(family, parents), get("/favourites").header("Authorization", "Bearer " + kidsToken))
				.andExpect(jsonPath("$.length()").value(0));
		// Without the cookie, the header alone is no session at all.
		mvc.perform(get("/favourites").header("Authorization", "Bearer " + kidsToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"));
	}

	@Test
	void tokenWithoutProfileMustChooseOneFirst() throws Exception {
		// An administrator has no extra access to profile data either.
		for (String token : List.of(TestTokens.token(TestTokens.IDENTITY_KEYS, "homeflix-identity", family, false, null,
				Instant.now(), Duration.ofMinutes(15)), TestTokens.admin(family))) {
			for (MockHttpServletRequestBuilder request : List.of(get("/favourites"), get("/progress"),
					put("/favourites/" + film))) {
				as(token, request)
						.andExpect(status().isForbidden())
						.andExpect(jsonPath("$.error").value("profile_not_selected"));
			}
		}
		assertThat(jdbc.queryForObject("select count(*) from favourite", Integer.class)).isZero();
	}

	@Test
	void forgedOrAlteredTokensClaimingAProfileAreRefused() throws Exception {
		String kidsToken = TestTokens.user(family, kids);
		as(kidsToken, put("/favourites/" + film)).andExpect(status().isNoContent());

		// Signed with another key.
		String forged = TestTokens.token(TestTokens.newKeyPair(), "homeflix-identity", neighbour, false, kids,
				Instant.now(), Duration.ofMinutes(15));
		// A real token of the neighbour, with the profile changed in the payload.
		String[] parts = TestTokens.user(neighbour, neighbourProfile).split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
				.replace(neighbourProfile.toString(), kids.toString());
		String altered = parts[0] + "."
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
				+ "." + parts[2];
		// Expired.
		String expired = TestTokens.token(TestTokens.IDENTITY_KEYS, "homeflix-identity", family, false, kids,
				Instant.now().minus(Duration.ofMinutes(20)), Duration.ofMinutes(15));

		for (String token : List.of(forged, altered, expired)) {
			as(token, get("/favourites"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error").value("unauthenticated"));
			as(token, delete("/favourites/" + film)).andExpect(status().isUnauthorized());
		}
		as(kidsToken, get("/favourites")).andExpect(jsonPath("$.length()").value(1));
	}

	// ---- Helpers ----

	private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.cookie(new Cookie("homeflix_token", token)));
	}

	private void saveProgress(String token, UUID media, int position) throws Exception {
		as(token, put("/progress/" + media).contentType(MediaType.APPLICATION_JSON)
				.content("{\"positionSeconds\":" + position + ",\"durationSeconds\":1000}"))
				.andExpect(status().isOk());
	}

	private UUID title(String name) {
		return jdbc.queryForObject("insert into media_item (title, availability) values (?, 'UNAVAILABLE') returning id",
				UUID.class, name);
	}

}
