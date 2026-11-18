package com.homeflix.library.indexing;

import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Component;

/** Lets one scan run at a time (docs/api/library.yaml, {@code scan_in_progress}). */
@Component
public class ScanGate {

	private final Semaphore permit = new Semaphore(1);

	/** False when another scan is running. */
	public boolean tryEnter() {
		return permit.tryAcquire();
	}

	public void leave() {
		permit.release();
	}

}
