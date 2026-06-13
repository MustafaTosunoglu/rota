package com.rota.proxy.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;

/** The outbound HTTP client for Try It: virtual threads, capped connect timeout, no redirects. */
@Configuration
class ProxyHttpClientConfig {

    @Bean
    HttpClient proxyHttpClient(ProxyProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                // Redirects are NOT followed: a 30x to a private address would bypass the SSRF
                // check performed on the original URL (plan §13.1 security).
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }
}
