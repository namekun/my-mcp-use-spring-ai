package org.springframework.ai.mcp.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class LLMConfiguration {
    @Value("${spring.ai.ollama.base-url}")
    private String baseUrl;

    @Value("${spring.ai.ollama.chat.options.model}")
    private String model;

    // Duration 형식으로 지정 가능: "120s", "5m" 등. 값이 없으면 합리적 기본값 사용
    @Value("${spring.ai.ollama.http.connect-timeout:10s}")
    private Duration connectTimeout;

    @Value("${spring.ai.ollama.http.read-timeout:300s}")
    private Duration readTimeout;

    @Value("${spring.ai.ollama.http.write-timeout:120s}")
    private Duration writeTimeout;

    @Bean
    public OllamaChatModel ollamaChatModel() {
        // 1) 요청 옵션(모델) 설정
        OllamaOptions ollamaOptions = new OllamaOptions();
        ollamaOptions.setModel(model);
        // 일관성 향상
        ollamaOptions.setTemperature(0.2);

        // 2) RestClient용 ClientHttpRequestFactory 구성 (블로킹)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.min(connectTimeout.toMillis(), Integer.MAX_VALUE));
        factory.setReadTimeout((int) Math.min(readTimeout.toMillis(), Integer.MAX_VALUE));

        RestClient.Builder builder = RestClient.builder();
        builder
                .baseUrl(baseUrl)
                .requestFactory(factory);

        // 3) OllamaApi에 커스텀 RestClient 주입
        OllamaApi ollamaApi = new OllamaApi.Builder()
                .baseUrl(baseUrl)
                .restClientBuilder( builder)
                .build();

        // 4) 최종 ChatModel 생성
        return OllamaChatModel.builder()
                .defaultOptions(ollamaOptions)
                .ollamaApi(ollamaApi)
                .build();

    }

}