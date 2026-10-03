package com.vbforge.asknotes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Why it's written this way:
 *
 * Two timeouts. The connect timeout lives on the JDK HttpClient, and the read timeout on the request factory.
 * Together they give the fail-fast "Ollama is down" versus patient "model is still generating" behavior from the config.
 * Plain Spring Framework classes instead of Boot's helpers. Boot 4 reorganized its HTTP-client settings.
 * For example, it introduced HttpClientSettings for imperative or reactive HTTP clients, and
 * I couldn't confirm which settings class the request-factory builder expects in 4.1.
 * These plain classes behave the same in every Boot version, and they show exactly where each timeout is set.
 *
 * HTTP/1.1 is forced. The JDK client otherwise tries an HTTP/2 upgrade, which is unnecessary on plain http:// and sometimes problematic with local servers.
 * The injected RestClient.Builder comes from spring-boot-starter-restclient, so it keeps Boot's message-converter setup, including Jackson 3.
 * */

@Configuration
public class OllamaConfig {

    @Bean
    RestClient ollamaRestClient(RestClient.Builder builder, OllamaProperties props) {

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(props.readTimeout());

        return builder
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .build();

    }

}
