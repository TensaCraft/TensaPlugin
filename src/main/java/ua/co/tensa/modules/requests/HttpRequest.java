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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
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
	private static final int MAX_PENDING_REQUESTS = 256;

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
		try {
			return CompletableFuture.supplyAsync(() -> {
				try {
					return send();
				} catch (Exception e) {
					throw new CompletionException(e);
				}
			}, executor());
		} catch (RejectedExecutionException rejected) {
			return CompletableFuture.failedFuture(
					new IllegalStateException("HTTP request queue is full", rejected)
			);
		}
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
					Message.warn("HTTP " + type + " retry " + (attempt + 1) + "/" + maxAttempts
							+ " for " + endpointForLog() + " after " + e.getClass().getSimpleName());
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
				Message.warn("HTTP " + getMethod() + " -> Non-JSON response from " + endpointForLog() + " [" + status + "]");
			} else {
				Message.info("HTTP " + getMethod() + " -> " + endpointForLog() + " [" + status + "]");
			}
			return;
		}

		Message.error("HTTP " + getMethod() + " -> Failed [" + status + "] " + endpointForLog()
				+ " (response type=" + (json == null ? "text" : "json")
				+ ", characters=" + body.length() + ")");
	}

	private void logEmptyResponse(int status) {
		if (isSuccess(status)) {
			Message.warn("HTTP " + getMethod() + " -> Empty response from " + endpointForLog() + " [" + status + "]");
		} else {
			Message.error("HTTP " + getMethod() + " -> Empty failed response [" + status + "] " + endpointForLog());
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
				int workers = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
				httpExecutor = new ThreadPoolExecutor(
						workers,
						workers,
						0L,
						TimeUnit.MILLISECONDS,
						new ArrayBlockingQueue<>(MAX_PENDING_REQUESTS),
						runnable -> {
							Thread thread = new Thread(runnable);
							thread.setName("tensa-http-" + thread.threadId());
							thread.setDaemon(true);
							return thread;
						},
						new ThreadPoolExecutor.AbortPolicy()
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

	private String endpointForLog() {
		return redactUrlForLog(url);
	}

	static String redactUrlForLog(String value) {
		try {
			URI uri = URI.create(value);
			String path = uri.getPath() == null ? "" : uri.getPath();
			int webhook = path.indexOf("/api/webhooks/");
			if (webhook >= 0) {
				path = path.substring(0, webhook) + "/api/webhooks/[redacted]";
			}
			int port = uri.getPort();
			return uri.getScheme() + "://" + uri.getHost() + (port < 0 ? "" : ":" + port) + path;
		} catch (RuntimeException ignored) {
			return "[invalid endpoint]";
		}
	}
}
