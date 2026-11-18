package com.homeflix.acquisition.catalogue;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.acquisition.security.AccessGuard;
import com.homeflix.acquisition.web.NotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The verified acquisition catalogue (docs/api/acquisition.yaml, tag Catalogue; ACQ-01, ACQ-02).
 * Administrator only (ACQ-05). Read only: there is no operation that adds an entry or takes a URL.
 */
@RestController
@RequestMapping("/entries")
class EntryController {

	private final EntryRepository entries;
	private final AccessGuard access;

	EntryController(EntryRepository entries, AccessGuard access) {
		this.entries = entries;
		this.access = access;
	}

	@GetMapping
	List<Entry> list(HttpServletRequest request) {
		access.requireAdmin(request);
		return entries.findAll();
	}

	@GetMapping("/{entryId}")
	Entry get(HttpServletRequest request, @PathVariable UUID entryId) {
		access.requireAdmin(request);
		return entries.find(entryId).orElseThrow(NotFoundException::new);
	}

}
