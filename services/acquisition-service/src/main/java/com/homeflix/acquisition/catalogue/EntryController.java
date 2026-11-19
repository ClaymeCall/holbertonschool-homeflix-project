package com.homeflix.acquisition.catalogue;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.acquisition.security.AdminOnly;
import com.homeflix.acquisition.web.NotFoundException;

/**
 * The verified acquisition catalogue (docs/api/acquisition.yaml, tag Catalogue; ACQ-01, ACQ-02).
 * Administrator only (ACQ-05), checked before the id is read. Read only: there is no operation that adds an
 * entry or takes a URL.
 */
@AdminOnly
@RestController
@RequestMapping("/entries")
class EntryController {

	private final EntryRepository entries;

	EntryController(EntryRepository entries) {
		this.entries = entries;
	}

	@GetMapping
	List<Entry> list() {
		return entries.findAll();
	}

	@GetMapping("/{entryId}")
	Entry get(@PathVariable UUID entryId) {
		return entries.find(entryId).orElseThrow(NotFoundException::new);
	}

}
