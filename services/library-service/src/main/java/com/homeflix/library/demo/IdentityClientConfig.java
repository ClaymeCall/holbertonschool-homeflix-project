package com.homeflix.library.demo;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class IdentityClientConfig {

	@Bean
	IdentityClient identityClient(
			@Value("${homeflix.identity.base-url}") String baseUrl,
			@Value("${homeflix.identity.connect-timeout:2s}") Duration connectTimeout,
			@Value("${homeflix.identity.read-timeout:5s}") Duration readTimeout) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(connectTimeout).build());
		factory.setReadTimeout(readTimeout);
		return new IdentityClient(RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build());
	}

}
