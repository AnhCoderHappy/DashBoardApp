package com.mdata.backend.service;

import com.mdata.backend.entity.SyncLog;
import com.mdata.backend.repository.SyncLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertService {

    private final SyncLogRepository syncLogRepository;
    private final String telegramBotToken;
    private final String telegramChatId;
    
    private final ConcurrentHashMap<String, Long> alertCache = new ConcurrentHashMap<>();
    private static final long THROTTLE_WINDOW_MS = 30 * 60 * 1000; // 30 minutes

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AlertService(
            SyncLogRepository syncLogRepository,
            @Value("${TELEGRAM_BOT_TOKEN:}") String telegramBotToken,
            @Value("${TELEGRAM_CHAT_ID:}") String telegramChatId
    ) {
        this.syncLogRepository = syncLogRepository;
        this.telegramBotToken = telegramBotToken;
        this.telegramChatId = telegramChatId;
    }

    public void sendAlert(String title, String message, boolean force) {
        String cacheKey = title + ":" + (message.length() > 50 ? message.substring(0, 50) : message);
        long now = System.currentTimeMillis();
        Long lastAlertTime = alertCache.get(cacheKey);

        if (!force && lastAlertTime != null && (now - lastAlertTime < THROTTLE_WINDOW_MS)) {
            System.out.println("[Alert Throttled] " + title + ": " + message);
            return;
        }

        alertCache.put(cacheKey, now);

        String fullMessage = "⚠️ [MData Alert]\n*" + title + "*\n\n" + message + "\n\nTimestamp: " + Instant.now();
        System.err.println("🚨 ALERT: " + title + " - " + message);

        // 1. Log alert to database
        try {
            SyncLog log = new SyncLog();
            log.setPlatform("system");
            log.setJobName("system_alert");
            log.setStatus("failed");
            log.setErrorMessage(title + ": " + message);
            log.setMetadata("{\"type\":\"alert\",\"title\":\"" + title + "\"}");
            log.setStartedAt(Instant.now());
            log.setFinishedAt(Instant.now());
            syncLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Failed to log alert to database: " + e.getMessage());
        }

        // 2. Send Telegram notification
        if (telegramBotToken != null && !telegramBotToken.isEmpty() &&
            telegramChatId != null && !telegramChatId.isEmpty()) {
            try {
                String url = "https://api.telegram.org/bot" + telegramBotToken + "/sendMessage";
                String jsonPayload = String.format("{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"Markdown\"}",
                        telegramChatId,
                        fullMessage.replace("\"", "\\\"").replace("\n", "\\n")
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .timeout(Duration.ofSeconds(5))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() >= 400) {
                                System.err.println("Telegram API error: " + response.statusCode() + " - " + response.body());
                            }
                        });
            } catch (Exception e) {
                System.err.println("Failed to send Telegram alert: " + e.getMessage());
            }
        }
    }
}
