package com.homeflix.library.roots;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.library.security.AccessGuard;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** The authorized media folders (docs/api/library.yaml, tag Roots). Administrator only. */
@RestController
@RequestMapping("/roots")
class RootController {

	private final RootService service;
	private final AccessGuard admin;

	RootController(RootService service, AccessGuard admin) {
		this.service = service;
		this.admin = admin;
	}

	@GetMapping
	List<Root> list(HttpServletRequest request) {
		admin.requireAdmin(request);
		return service.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Root add(HttpServletRequest request, @Valid @RequestBody RootRequest body) {
		admin.requireAdmin(request);
		return service.add(body.path());
	}

	@DeleteMapping("/{rootId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(HttpServletRequest request, @PathVariable UUID rootId) {
		admin.requireAdmin(request);
		service.remove(rootId);
	}

}
