package ua.co.tensa.modules.requests;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestTest {

    private HttpServer server;
    private java.util.concurrent.ExecutorService serverExecutor;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        serverExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(serverExecutor);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        HttpRequest.shutdown();
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void concurrentRequestsDoNotStarveHttpClientTransport() throws Exception {
        int workers = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch completed = new CountDownLatch(workers);
        server.createContext("/parallel", exchange -> {
            ready.countDown();
            try {
                if (!ready.await(2, TimeUnit.SECONDS)) throw new IOException("Requests did not arrive together");
                respond(exchange, 200, "{\"status\":\"ok\"}");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
        });
        for (int index = 0; index < workers; index++) {
            new HttpRequest(baseUrl + "/parallel", "GET", Map.of()).sendAsync().thenAccept(result -> {
                if (result.isSuccess()) completed.countDown();
            });
        }
        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(completed.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void sendReturnsJsonForSuccessfulGet() throws Exception {
        server.createContext("/ok", exchange -> respond(exchange, 200, "{\"status\":\"ok\"}"));

        HttpRequest.Result response = new HttpRequest(baseUrl + "/ok", "GET", Map.of()).send();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.json()).isNotNull();
        assertThat(response.json().getAsJsonObject().get("status").getAsString()).isEqualTo("ok");
    }

    @Test
    void sendReturnsJsonForNonSuccessStatusWhenBodyIsJson() throws Exception {
        server.createContext("/fail", exchange -> respond(exchange, 500, "{\"error\":\"boom\"}"));

        HttpRequest.Result response = new HttpRequest(baseUrl + "/fail", "GET", Map.of()).send();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.json()).isNotNull();
        assertThat(response.json().getAsJsonObject().get("error").getAsString()).isEqualTo("boom");
    }

    @Test
    void sendEncodesGetParameters() throws Exception {
        server.createContext("/query", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            respond(exchange, 200, "{\"query\":\"" + escapeJson(query) + "\"}");
        });

        HttpRequest.Result response = new HttpRequest(baseUrl + "/query", "GET", Map.of("player name", "A B")).send();

        assertThat(response).isNotNull();
        assertThat(response.json()).isNotNull();
        assertThat(response.json().getAsJsonObject().get("query").getAsString()).isEqualTo("player+name=A+B");
    }

    @Test
    void shutdownCompletesQueuedRequestFutures() throws Exception {
        java.util.concurrent.ExecutorService blocked = java.util.concurrent.Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        var field = HttpRequest.class.getDeclaredField("httpExecutor");
        field.setAccessible(true);
        field.set(null, blocked);
        try {
            for (int index = 0; index < 2; index++) {
                blocked.submit(() -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            var queued = new HttpRequest(baseUrl + "/queued", "GET", Map.of()).sendAsync();

            HttpRequest.shutdown();

            assertThat(queued).isDone();
        } finally {
            release.countDown();
            blocked.shutdownNow();
        }
    }

    @Test
    void shutdownClosesTheOwnedHttpTransport() throws Exception {
        server.createContext("/close", exchange -> respond(exchange, 200, "{}"));
        new HttpRequest(baseUrl + "/close", "GET", Map.of()).send();
        var field = HttpRequest.class.getDeclaredField("client");
        field.setAccessible(true);
        java.net.http.HttpClient transport = (java.net.http.HttpClient) field.get(null);
        new HttpRequest(baseUrl + "/close", "GET", Map.of()).sendAsync().get(2, TimeUnit.SECONDS);

        HttpRequest.shutdown();

        assertThat(transport.awaitTermination(java.time.Duration.ofSeconds(1))).isTrue();
    }

    @Test
    void sendWorksAfterShutdownReinitializesClient() throws Exception {
        server.createContext("/restart", exchange -> respond(exchange, 200, "{\"status\":\"ok\"}"));

        HttpRequest.Result first = new HttpRequest(baseUrl + "/restart", "GET", Map.of()).send();
        HttpRequest.shutdown();
        HttpRequest.Result second = new HttpRequest(baseUrl + "/restart", "GET", Map.of()).send();

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isTrue();
        assertThat(second.json()).isNotNull();
        assertThat(second.json().getAsJsonObject().get("status").getAsString()).isEqualTo("ok");
    }

    @Test
    void logEndpointDropsQueriesAndRedactsWebhookCredentials() {
        String endpoint = "https://discord." + "com/api/webhooks/123456789/sensitive-value?token=also-sensitive";

        assertThat(HttpRequest.redactUrlForLog(endpoint))
                .isEqualTo("https://discord." + "com/api/webhooks/[redacted]")
                .doesNotContain("123456789", "sensitive-value", "also-sensitive");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
