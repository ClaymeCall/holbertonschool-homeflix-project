package com.homeflix.library.catalogue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether a poster address may be saved (CAT-11, SEC-12, ADR-0018, risks R-17 and R-33).
 *
 * <p>It must be an {@code https} address on the default port, with a host that is in the allowed list, written
 * exactly (no wildcard, so {@code evil.example.org} is not accepted because {@code example.org} is). It
 * carries no credentials. The check only reads the text: the server never opens the address, the viewer's
 * browser does.
 *
 * <p>The list comes from {@code POSTER_ALLOWED_HOSTS}, a comma-separated list of host names. The gateway reads the
 * same variable for its {@code img-src} policy (issue #59), so the two cannot drift apart.
 */
@Component
public class PosterPolicy {

	static final int MAX_LENGTH = 2048;
	private static final Pattern HOST_NAME = Pattern.compile("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+");

	private final Set<String> allowedHosts;

	PosterPolicy(@Value("${homeflix.library.poster-hosts:upload.wikimedia.org}") List<String> hosts) {
		this.allowedHosts = hosts.stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).filter(h -> !h.isEmpty())
				.peek(PosterPolicy::requireHostName).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	/** The hosts a poster can come from, lower case. */
	public Set<String> allowedHosts() {
		return allowedHosts;
	}

	/** Why this address is refused, as a message for the administrator, or empty when it is accepted. */
	Optional<String> refusal(String address) {
		if (address.length() > MAX_LENGTH) {
			return Optional.of("The address is too long.");
		}
		URI uri;
		try {
			uri = new URI(address);
		}
		catch (URISyntaxException e) {
			return Optional.of("This is not a valid address.");
		}
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			return Optional.of("The address must start with https://.");
		}
		String host = uri.getHost();
		if (host == null) {
			return Optional.of("This is not a valid address.");
		}
		if (uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
			return Optional.of("The address cannot contain a user name, a password or a port.");
		}
		if (!allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
			return Optional.of("Posters from " + host + " are not allowed. Use an image from an allowed host.");
		}
		return Optional.empty();
	}

	private static void requireHostName(String host) {
		if (!HOST_NAME.matcher(host).matches()) {
			throw new IllegalStateException("POSTER_ALLOWED_HOSTS must be a comma-separated list of host names, got: " + host);
		}
	}

}
