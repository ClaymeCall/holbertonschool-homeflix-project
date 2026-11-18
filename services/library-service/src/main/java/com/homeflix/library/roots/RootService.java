package com.homeflix.library.roots;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homeflix.library.web.NotFoundException;
import com.homeflix.library.web.RejectedRequestException;

/** Adds, lists and removes the authorized folders (LIB-01). Administrator-only, checked by the caller. */
@Service
public class RootService {

	private static final Logger log = LoggerFactory.getLogger(RootService.class);

	private final RootRepository roots;
	private final RootValidator validator;

	RootService(RootRepository roots, RootValidator validator) {
		this.roots = roots;
		this.validator = validator;
	}

	@Transactional(readOnly = true)
	public List<Root> list() {
		return roots.findAll();
	}

	/**
	 * Authorizes a folder. A folder already added, or one that contains or lies inside an existing
	 * root, is refused: the indexer would otherwise see the same file twice (LIB-05).
	 */
	@Transactional
	public Root add(String requestedPath) {
		RootValidator.Accepted folder = validator.validate(requestedPath);
		roots.lock();
		Path requested = Path.of(folder.path());
		for (Root existing : roots.findAll()) {
			Path existingPath = Path.of(existing.path());
			Path existingReal = realOrSelf(existingPath);
			if (existingPath.equals(requested) || existingReal.equals(folder.realPath())) {
				throw alreadyAdded();
			}
			if (overlaps(existingPath, requested) || overlaps(existingReal, folder.realPath())) {
				log.warn("Folder refused, it overlaps an authorized root: {} and {}", folder.path(), existing.path());
				throw new RejectedRequestException(HttpStatus.CONFLICT, "root_overlaps",
						"This folder overlaps a folder that is already added.");
			}
		}
		try {
			Root root = roots.insert(folder.path());
			log.info("Authorized folder added: {}", root.path());
			return root;
		}
		catch (DuplicateKeyException e) {
			throw alreadyAdded();
		}
	}

	/** Removes the folder from the list. The files on disk are never touched (LIB-07, R-03). */
	@Transactional
	public void remove(UUID id) {
		Root root = roots.find(id).orElseThrow(NotFoundException::new);
		int unavailable = roots.markTitlesUnavailable(id);
		roots.delete(id);
		log.info("Authorized folder removed: {} ({} titles now unavailable)", root.path(), unavailable);
	}

	private static boolean overlaps(Path a, Path b) {
		return !a.equals(b) && (a.startsWith(b) || b.startsWith(a));
	}

	private static Path realOrSelf(Path path) {
		try {
			return path.toRealPath();
		}
		catch (IOException e) {
			return path;
		}
	}

	private static RejectedRequestException alreadyAdded() {
		return new RejectedRequestException(HttpStatus.CONFLICT, "root_already_added", "This folder is already added.");
	}

}
