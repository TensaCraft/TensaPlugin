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

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        HttpRequest.shutdown();
        server.stop(0);
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
