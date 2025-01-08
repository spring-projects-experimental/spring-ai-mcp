/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.mcp.client.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * @author Christian Tzolov
 */
public class FlowSseClient {

	private final HttpClient httpClient;

	private static final Pattern EVENT_DATA_PATTERN = Pattern.compile("^data:(.+)$", Pattern.MULTILINE);

	private static final Pattern EVENT_ID_PATTERN = Pattern.compile("^id:(.+)$", Pattern.MULTILINE);

	private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("^event:(.+)$", Pattern.MULTILINE);

	public FlowSseClient(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	public void subscribe(String url, SSEEventHandler eventHandler) {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.header("Accept", "text/event-stream")
			.header("Cache-Control", "no-cache")
			.GET()
			.build();

		StringBuilder eventBuilder = new StringBuilder();
		AtomicReference<String> currentEventId = new AtomicReference<>();
		AtomicReference<String> currentEventType = new AtomicReference<>("message");

		Flow.Subscriber<String> lineSubscriber = new Flow.Subscriber<>() {
			private Flow.Subscription subscription;

			@Override
			public void onSubscribe(Flow.Subscription subscription) {
				this.subscription = subscription;
				subscription.request(Long.MAX_VALUE);
			}

			@Override
			public void onNext(String line) {
				if (line.isEmpty()) {
					// Empty line means end of event
					if (eventBuilder.length() > 0) {
						String eventData = eventBuilder.toString();
						SSEEvent event = new SSEEvent(currentEventId.get(), currentEventType.get(), eventData.trim());
						eventHandler.onEvent(event);
						eventBuilder.setLength(0);
					}
				}
				else {
					if (line.startsWith("data:")) {
						var matcher = EVENT_DATA_PATTERN.matcher(line);
						if (matcher.find()) {
							eventBuilder.append(matcher.group(1).trim()).append("\n");
						}
					}
					else if (line.startsWith("id:")) {
						var matcher = EVENT_ID_PATTERN.matcher(line);
						if (matcher.find()) {
							currentEventId.set(matcher.group(1).trim());
						}
					}
					else if (line.startsWith("event:")) {
						var matcher = EVENT_TYPE_PATTERN.matcher(line);
						if (matcher.find()) {
							currentEventType.set(matcher.group(1).trim());
						}
					}
				}
				subscription.request(1);
			}

			@Override
			public void onError(Throwable throwable) {
				eventHandler.onError(throwable);
			}

			@Override
			public void onComplete() {
				// Handle any remaining event data
				if (eventBuilder.length() > 0) {
					String eventData = eventBuilder.toString();
					SSEEvent event = new SSEEvent(currentEventId.get(), currentEventType.get(), eventData.trim());
					eventHandler.onEvent(event);
				}
			}
		};

		Function<Flow.Subscriber<String>, HttpResponse.BodySubscriber<Void>> subscriberFactory = subscriber -> HttpResponse.BodySubscribers
			.fromLineSubscriber(subscriber);

		CompletableFuture<HttpResponse<Void>> future = this.httpClient.sendAsync(request,
				info -> subscriberFactory.apply(lineSubscriber));

		future.thenAccept(response -> {
			if (response.statusCode() != 200) {
				throw new RuntimeException("Failed to connect to SSE stream: " + response.statusCode());
			}
		}).exceptionally(throwable -> {
			eventHandler.onError(throwable);
			return null;
		});
	}

	// Event handler interface
	public interface SSEEventHandler {

		void onEvent(SSEEvent event);

		void onError(Throwable error);

	}

	// SSE Event class
	public static class SSEEvent {

		private final String id;

		private final String type;

		private final String data;

		public SSEEvent(String id, String type, String data) {
			this.id = id;
			this.type = type;
			this.data = data;
		}

		public String getId() {
			return id;
		}

		public String getType() {
			return type;
		}

		public String getData() {
			return data;
		}

		@Override
		public String toString() {
			return "SSEEvent{" + "id='" + id + '\'' + ", type='" + type + '\'' + ", data='" + data + '\'' + '}';
		}

	}

	public static void main(String[] args) {

		FlowSseClient sseClient = new FlowSseClient(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());

		var eventHandler = new SSEEventHandler() {
			@Override
			public void onEvent(SSEEvent event) {
				System.out.println("Received event: " + event);
			}

			@Override
			public void onError(Throwable error) {
				System.out.println("Error: " + error);
			}
		};
		sseClient.subscribe("http://localhost:8080/sse", eventHandler);

		try {
			Thread.sleep(100000);
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
