package com.homeflix.identity.session;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homeflix.identity.account.AccountRecord;
import com.homeflix.identity.account.AccountRepository;
import com.homeflix.identity.security.PasswordConfig;
import com.homeflix.identity.web.UnauthenticatedException;

/**
 * Sign-in, renewal and current account (ACC-03, ACC-10, R-10).
 */
@Service
public class SessionService {

	private final AccountRepository accounts;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokens;

	/**
	 * Compared against when the email is unknown, so a sign-in takes about the same time
	 * whether the account exists or not (no account enumeration by timing).
	 */
	private final String unknownAccountHash;

	public SessionService(AccountRepository accounts, PasswordEncoder passwordEncoder, TokenService tokens) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.unknownAccountHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional(readOnly = true)
	public SignedIn signIn(Credentials credentials) {
		Optional<AccountRecord> found = accounts.findByEmail(credentials.email());
		String hash = found.map(AccountRecord::passwordHash).orElse(unknownAccountHash);

		boolean withinBcryptLimit = credentials.password().getBytes(StandardCharsets.UTF_8).length
				<= PasswordConfig.BCRYPT_MAX_BYTES;
		boolean passwordMatches = withinBcryptLimit && passwordEncoder.matches(credentials.password(), hash);

		if (found.isEmpty() || !passwordMatches || found.get().disabled()) {
			throw new InvalidCredentialsException();
		}
		// A new session has no active profile yet (PRF-02).
		return issue(found.get(), null);
	}

	/**
	 * Renews a valid token. The account is read again, so a disabled or deleted account
	 * cannot renew, and its access ends when its current token expires (R-10).
	 * A change of the administrator flag also takes effect here.
	 */
	@Transactional(readOnly = true)
	public SignedIn refresh(String token) {
		SessionClaims claims = tokens.verify(token).orElseThrow(UnauthenticatedException::new);
		AccountRecord account = activeAccount(claims.accountId());
		return issue(account, claims.profileId());
	}

	@Transactional(readOnly = true)
	public AccountView currentAccount(String token) {
		SessionClaims claims = tokens.verify(token).orElseThrow(UnauthenticatedException::new);
		AccountRecord account = activeAccount(claims.accountId());
		return view(account, claims.profileId());
	}

	private AccountRecord activeAccount(UUID id) {
		return accounts.findById(id)
				.filter(account -> !account.disabled())
				.orElseThrow(UnauthenticatedException::new);
	}

	private SignedIn issue(AccountRecord account, UUID profileId) {
		String token = tokens.issue(new SessionClaims(account.id(), account.admin(), profileId));
		return new SignedIn(view(account, profileId), token);
	}

	private static AccountView view(AccountRecord account, UUID profileId) {
		return new AccountView(account.id(), account.email(), account.admin(), profileId);
	}

}
