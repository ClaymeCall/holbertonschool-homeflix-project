package com.homeflix.identity.profile;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homeflix.identity.client.LibraryClient;
import com.homeflix.identity.security.Caller;
import com.homeflix.identity.web.NotFoundException;
import com.homeflix.identity.web.UnauthenticatedException;

/**
 * Viewing profiles of the signed-in account (PRF-01, PRF-04). The account always comes from the
 * token; a profile of another account is answered like one that does not exist.
 */
@Service
public class ProfileService {

	private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

	/** At most 5 profiles per account (contract, {@code profile_limit_reached}). */
	static final int MAX_PROFILES = 5;

	private final ProfileRepository profiles;
	private final LibraryClient library;

	public ProfileService(ProfileRepository profiles, LibraryClient library) {
		this.profiles = profiles;
		this.library = library;
	}

	@Transactional(readOnly = true)
	public List<Profile> list(Caller caller) {
		return profiles.list(caller.accountId());
	}

	@Transactional
	public Profile create(Caller caller, ProfileRequest request) {
		// Two creations at once for the same account wait for each other here.
		if (!profiles.lockAccount(caller.accountId())) {
			throw new UnauthenticatedException();
		}
		if (profiles.count(caller.accountId()) >= MAX_PROFILES) {
			throw new ProfileLimitReachedException();
		}
		try {
			return profiles.insert(caller.accountId(), request.name());
		}
		catch (DuplicateKeyException ex) {
			throw new ProfileNameTakenException();
		}
	}

	@Transactional
	public Profile rename(Caller caller, UUID profileId, ProfileRequest request) {
		Profile current = profiles.find(profileId, caller.accountId()).orElseThrow(NotFoundException::new);
		if (current.name().equals(request.name())) {
			return current;
		}
		try {
			return profiles.rename(profileId, caller.accountId(), request.name()).orElseThrow(NotFoundException::new);
		}
		catch (DuplicateKeyException ex) {
			throw new ProfileNameTakenException();
		}
	}

	/**
	 * Library first, then identity (consistency rules, "Deleting a profile"). If the library does
	 * not confirm, the profile is kept and the caller gets a 503: repeating the request is safe.
	 * No database transaction is held during the call.
	 */
	public void delete(Caller caller, UUID profileId) {
		profiles.find(profileId, caller.accountId()).orElseThrow(NotFoundException::new);
		library.deleteProfileData(profileId);
		profiles.delete(profileId, caller.accountId());
		log.info("Profile {} of account {} deleted", profileId, caller.accountId());
	}

}
