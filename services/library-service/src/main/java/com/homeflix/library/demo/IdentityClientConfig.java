package com.homeflix.library.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.homeflix.library.client.ServiceClientFactory;

@Configuration(proxyBeanMethods = false)
public class IdentityClientConfig {

	@Bean
	IdentityClient identityClient(ServiceClientFactory clients, @Value("${homeflix.identity.base-url}") String baseUrl) {
		return new IdentityClient(clients.create(baseUrl));
	}

}
