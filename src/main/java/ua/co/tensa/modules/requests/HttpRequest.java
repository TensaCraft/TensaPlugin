package ua.co.tensa.modules.requests;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import ua.co.tensa.Message;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HttpRequest {

	private static final String GET = "GET";
	private static final String POST = "POST";
	private static final String USER_AGENT = "Tensa-Requests/1.0";

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(20);
	private static final Object HTTP_LOCK = new Object();
	private static volatile ExecutorService httpExecutor;
	private static volatile HttpClient client;

	private static final int MAX_ATTEMPTS = 2;

	private final String url;
	private final String method;
	private final Map<String, String> parameters;

	public record Result(int statusCode, String body, JsonElement json) {
		public boolean isSuccess() {
			return statusCode >= 200 && statusCode < 300;
		}
	}

	public HttpRequest(String url, String method, Map<String, String> parameters) {
		this.url = url;
		this.method = method;
		this.parameters = parameters;
	}

	public CompletableFuture<Result> sendAsync() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return send();
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		}, executor());
	}

	public Result send() throws Exception {
		String type = getMethod();
		int maxAttempts = shouldRetry(type) ? MAX_ATTEMPTS : 1;
		IOException lastError = null;

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				return send(type);
			} catch (IOException e) {
				lastError = e;
				if (attempt < maxAttempts) {
					Message.warn("HTTP " + type + " retry " + (attempt + 1) + "/" + maxAttempts + " for " + url + " -> " + e.getMessage());
				}
			}
		}

		if (lastError != null) {
			throw lastError;
		}

		throw new IllegalStateException("HTTP request failed without a specific exception.");
	}

	private Result send(String type) throws IOException {
		return switch (type) {
			case POST -> sendPost();
			case GET -> sendGet();
			default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
		};
	}

	private Result sendPost() throws IOException {
		java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(URI.create(url))
				.timeout(RESPONSE_TIMEOUT)
				.header("Accept", "application/json")
				.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
				.header("User-Agent", USER_AGENT)
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString(getFormBody()))
				.build();

		return processResponse(execute(request));
	}

	private Result sendGet() throws IOException {
		java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(URI.create(getUrlWithParams()))
				.timeout(RESPONSE_TIMEOUT)
				.header("Accept", "application/json")
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();

		return processResponse(execute(request));
	}

	private HttpResponse<String> execute(java.net.http.HttpRequest request) throws IOException {
		try {
			return client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("HTTP request interrupted.", e);
		}
	}

	private String getMethod() {
		if (method == null) {
			return "";
		}

		return method.trim().toUpperCase(Locale.ROOT);
	}

	private String getFormBody() {
		if (parameters == null || parameters.isEmpty()) {
			return "";
		}

		StringJoiner joiner = new StringJoiner("&");
		for (Map.Entry<String, String> entry : parameters.entrySet()) {
			joiner.add(encode(entry.getKey()) + "=" + encode(entry.getValue()));
		}
		return joiner.toString();
	}

	private String getUrlWithParams() {
		if (parameters == null || parameters.isEmpty()) {
			return url;
		}

		return url + (url.contains("?") ? "&" : "?") + getFormBody();
	}

	private Result processResponse(HttpResponse<String> response) {
		int status = response.statusCode();
		String body = response.body() == null ? "" : response.body();

		if (body.isBlank()) {
			logEmptyResponse(status);
			return new Result(status, body, null);
		}

		try {
			JsonElement json = JsonParser.parseString(body);
			logResponse(status, json, body);
			return new Result(status, body, json);
		} catch (JsonSyntaxException e) {
			logResponse(status, null, body);
			return new Result(status, body, null);
		}
	}

	private void logResponse(int status, JsonElement json, String body) {
		if (isSuccess(status)) {
			if (json == null) {
				Message.warn("HTTP " + getMethod() + " -> Non-JSON response from " + url + " [" + status + "]");
			} else {
				Message.info("HTTP " + getMethod() + " -> " + url + " [" + status + "]");
			}
			return;
		}

		Message.error("HTTP " + getMethod() + " -> Failed [" + status + "] " + url + "\nResponse: " + summarizeForLog(json, body));
	}

	private String summarizeForLog(JsonElement json, String body) {
		String summary = json == null ? body : json.toString();
		if (summary == null) {
			return "";
		}
		return summary.length() > 1000 ? summary.substring(0, 1000) + "..." : summary;
	}

	private void logEmptyResponse(int status) {
		if (isSuccess(status)) {
			Message.warn("HTTP " + getMethod() + " -> Empty response from " + url + " [" + status + "]");
		} else {
			Message.error("HTTP " + getMethod() + " -> Empty failed response [" + status + "] " + url);
		}
	}

	private boolean isSuccess(int status) {
		return status >= 200 && status < 300;
	}

	private boolean shouldRetry(String type) {
		return GET.equals(type);
	}

	private String encode(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	public static void shutdown() {
		ExecutorService executorToShutdown;
		synchronized (HTTP_LOCK) {
			executorToShutdown = httpExecutor;
			httpExecutor = null;
			client = null;
		}
		if (executorToShutdown == null) {
			return;
		}
		executorToShutdown.shutdown();
		try {
			if (!executorToShutdown.awaitTermination(5, TimeUnit.SECONDS)) {
				executorToShutdown.shutdownNow();
			}
		} catch (InterruptedException e) {
			executorToShutdown.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private static ExecutorService executor() {
		synchronized (HTTP_LOCK) {
			if (httpExecutor == null || httpExecutor.isShutdown() || httpExecutor.isTerminated()) {
				httpExecutor = Executors.newFixedThreadPool(
						Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())),
						runnable -> {
							Thread thread = new Thread(runnable);
							thread.setName("tensa-http-" + thread.threadId());
							thread.setDaemon(true);
							return thread;
						}
				);
				client = null;
			}
			return httpExecutor;
		}
	}

	private static HttpClient client() {
		synchronized (HTTP_LOCK) {
			if (client == null) {
				client = HttpClient.newBuilder()
						.connectTimeout(CONNECT_TIMEOUT)
						.executor(executor())
						.followRedirects(HttpClient.Redirect.NORMAL)
						.build();
			}
			return client;
		}
	}
}
