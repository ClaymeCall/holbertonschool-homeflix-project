package com.homeflix.library.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} used for every call to another service (ADR-0008), so each call
 * has timeouts and the same retry policy (ADR-0020, R-24). Defaults: connect 2 s, read 5 s, one
 * retry of a GET after 200 ms. They are configuration (homeflix.http-client.*).
 */
@Component
public class ServiceClientFactory {

	private final Duration connectTimeout;
	private final Duration readTimeout;
	private final int retries;
	private final Duration retryPause;

	public ServiceClientFactory(
			@Value("${homeflix.http-client.connect-timeout:2s}") Duration connectTimeout,
			@Value("${homeflix.http-client.read-timeout:5s}") Duration readTimeout,
			@Value("${homeflix.http-client.retries:1}") int retries,
			@Value("${homeflix.http-client.retry-pause:200ms}") Duration retryPause) {
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
		this.retries = retries;
		this.retryPause = retryPause;
	}

	public RestClient create(String baseUrl) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(connectTimeout).build());
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.requestInterceptor(new RetryingGetInterceptor(retries, retryPause))
				.build();
	}

}
