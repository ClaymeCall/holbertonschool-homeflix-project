package com.homeflix.library.indexing;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.library.security.AccessGuard;

import jakarta.servlet.http.HttpServletRequest;

/** Manual indexing (docs/api/library.yaml, tag Scans). Administrator only (LIB-06). */
@RestController
@RequestMapping("/scans")
class ScanController {

	private final ScanService scans;
	private final AccessGuard admin;

	ScanController(ScanService scans, AccessGuard admin) {
		this.scans = scans;
		this.admin = admin;
	}

	@PostMapping
	ScanReport run(HttpServletRequest request) {
		admin.requireAdmin(request);
		return scans.run();
	}

}
