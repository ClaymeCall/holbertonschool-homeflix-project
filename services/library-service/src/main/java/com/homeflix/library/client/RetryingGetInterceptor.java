package com.homeflix.library.client;

import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Retry policy for service-to-service calls (ADR-0020, R-24): only idempotent GET requests are
 * retried, a limited number of times after a short pause, and only after a connection failure
 * or timeout, or a 502, 503 or 504 answer. POST, PUT, PATCH and DELETE are never retried, and
 * neither is any 4xx answer.
 */
public class RetryingGetInterceptor implements ClientHttpRequestInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RetryingGetInterceptor.class);

	private final int maxRetries;
	private final Duration pause;

	public RetryingGetInterceptor(int maxRetries, Duration pause) {
		this.maxRetries = maxRetries;
		this.pause = pause;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		if (!HttpMethod.GET.equals(request.getMethod())) {
			return execution.execute(request, body);
		}
		int retries = 0;
		while (true) {
			try {
				ClientHttpResponse response = execution.execute(request, body);
				if (retries >= maxRetries || !isRetryable(response.getStatusCode())) {
					return response;
				}
				response.close();
			}
			catch (IOException e) {
				if (retries >= maxRetries) {
					throw e;
				}
			}
			retries++;
			log.debug("Retrying GET {} (retry {} of {})", request.getURI(), retries, maxRetries);
			pause();
		}
	}

	private static boolean isRetryable(HttpStatusCode status) {
		int code = status.value();
		return code == 502 || code == 503 || code == 504;
	}

	private void pause() throws IOException {
		try {
			Thread.sleep(pause);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting to retry", e);
		}
	}

}
