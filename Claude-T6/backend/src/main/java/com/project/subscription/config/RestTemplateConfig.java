package com.project.subscription.config;

import com.project.subscription.service.SsrfValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;

@Configuration
@RequiredArgsConstructor
public class RestTemplateConfig {

    @Value("${webhook.pool.max-connections:200}")
    private int maxConnections;

    @Value("${webhook.pool.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${webhook.pool.read-timeout:10000}")
    private int readTimeout;

    @Bean
    public RestTemplate webhookRestTemplate(SsrfValidator ssrfValidator) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        RestTemplate rt = new RestTemplate(factory);
        rt.getInterceptors().add(new SsrfInterceptor(ssrfValidator));
        return rt;
    }

    /**
     * SSRF 拦截器 — 对 RestTemplate 发出的请求做二次校验
     */
    @RequiredArgsConstructor
    static class SsrfInterceptor implements ClientHttpRequestInterceptor {
        private final SsrfValidator validator;

        @Override
        public org.springframework.http.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request, byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) throws IOException {
            URI uri = request.getURI();
            var result = validator.validate(uri.toString());
            if (!result.valid()) {
                throw new IOException("SSRF 阻止: " + result.errorMessage());
            }
            return execution.execute(request, body);
        }
    }
}
