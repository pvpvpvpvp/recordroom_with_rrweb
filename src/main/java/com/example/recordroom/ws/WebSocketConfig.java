package com.example.recordroom.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.config.annotation.*;
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final IngestWebSocketHandler ingestWebSocketHandler;
    private final CdpReplayWebSocketHandler cdpReplayWebSocketHandler;
    private final ClockWebSocketHandler clockWebSocketHandler;
    private final AdminWebSocketHandler adminWebSocketHandler;

    public WebSocketConfig(IngestWebSocketHandler ingestWebSocketHandler,
                           CdpReplayWebSocketHandler cdpReplayWebSocketHandler,
                           ClockWebSocketHandler clockWebSocketHandler,
                           AdminWebSocketHandler adminWebSocketHandler) {

        this.ingestWebSocketHandler = ingestWebSocketHandler;
        this.cdpReplayWebSocketHandler = cdpReplayWebSocketHandler;
        this.clockWebSocketHandler = clockWebSocketHandler;
        this.adminWebSocketHandler = adminWebSocketHandler;
    }


    /**
     * Increase WS message buffer sizes.
     * - Fixes CloseStatus 1009: "decoded text message was too big for the output buffer..."
     * - DevTools Frontend can send/receive larger CDP payloads than Tomcat defaults.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // Tomcat default is small (~8KB). For replay/CDP, bump for safety (demo only).
        container.setMaxTextMessageBufferSize(2 * 1024 * 1024);   // 2MB
        container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024); // 2MB
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(ingestWebSocketHandler, "/ws/ingest")
                .setAllowedOriginPatterns("*");

        registry.addHandler(cdpReplayWebSocketHandler, "/ws/cdp")
                .setAllowedOriginPatterns("*");

        // rrweb -> CDP sync clock (gated mode)
        registry.addHandler(clockWebSocketHandler, "/ws/clock")
                .setAllowedOrigins("*"); // demo only

        // QA admin live feed (demo only, no auth)
        registry.addHandler(adminWebSocketHandler, "/ws/admin")
                .setAllowedOriginPatterns("*");
    }
}