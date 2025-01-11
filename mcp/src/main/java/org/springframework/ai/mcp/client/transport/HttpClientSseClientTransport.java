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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mcp.client.transport.FlowSseClient.SSEEvent;
import org.springframework.ai.mcp.spec.ClientMcpTransport;
import org.springframework.ai.mcp.spec.McpError;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCMessage;
import org.springframework.ai.mcp.util.Assert;

import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Server-Sent Events (SSE) implementation of the
 * {@link org.springframework.ai.mcp.spec.McpTransport} that follows the MCP HTTP with SSE
 * transport specification, using Java's HttpClient.
 *
 * @author Christian Tzolov
 */
public class HttpClientSseClientTransport implements ClientMcpTransport {

	private final static Logger logger = LoggerFactory.getLogger(HttpClientSseClientTransport.class);

	private final static String MESSAGE_EVENT_TYPE = "message";

	private final static String ENDPOINT_EVENT_TYPE = "endpoint";

	private final static String SSE_ENDPOINT = "/sse";

	private final String baseUri;

	private final FlowSseClient sseClient;

	private final HttpClient httpClient;

	protected ObjectMapper objectMapper;

	private volatile boolean isClosing = false;

	private CountDownLatch closeLatch = new CountDownLatch(1);

	private final AtomicReference<String> messageEndpoint = new AtomicReference<>();

	private final AtomicReference<CompletableFuture<Void>> connectionFuture = new AtomicReference<>();

	public HttpClientSseClientTransport(String baseUri) {
		this(HttpClient.newBuilder(), baseUri, new ObjectMapper());
	}

	public HttpClientSseClientTransport(HttpClient.Builder clientBuilder, String baseUri, ObjectMapper objectMapper) {
		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		Assert.notNull(clientBuilder, "clientBuilder must not be null");
		this.baseUri = baseUri;
		this.objectMapper = objectMapper;
		this.httpClient = clientBuilder.connectTimeout(Duration.ofSeconds(10)).build();
		this.sseClient = new FlowSseClient(this.httpClient);
	}

	@Override
	public Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		connectionFuture.set(future);

		sseClient.subscribe(this.baseUri + SSE_ENDPOINT, new FlowSseClient.SSEEventHandler() {
			@Override
			public void onEvent(SSEEvent event) {
				if (isClosing) {
					return;
				}

				try {
					if (ENDPOINT_EVENT_TYPE.equals(event.getType())) {
						String endpoint = event.getData();
						messageEndpoint.set(endpoint);
						closeLatch.countDown();
						future.complete(null);
					}
					else if (MESSAGE_EVENT_TYPE.equals(event.getType())) {
						JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, event.getData());
						handler.apply(Mono.just(message)).subscribe();
					}
					else {
						logger.error("Received unrecognized SSE event type: " + event.getType());
					}
				}
				catch (IOException e) {
					logger.error("Error processing SSE event", e);
					future.completeExceptionally(e);
				}
			}

			@Override
			public void onError(Throwable error) {
				if (!isClosing) {
					logger.error("SSE connection error", error);
					future.completeExceptionally(error);
				}
			}
		});

		return Mono.fromFuture(future);
	}

	@Override
	public Mono<Void> sendMessage(JSONRPCMessage message) {
		if (isClosing) {
			return Mono.empty();
		}

		try {
			if (!closeLatch.await(10, TimeUnit.SECONDS)) {
				return Mono.error(new McpError("Failed to wait for the message endpoint"));
			}
		}
		catch (InterruptedException e) {
			return Mono.error(new McpError("Failed to wait for the message endpoint"));
		}

		String endpoint = messageEndpoint.get();
		if (endpoint == null) {
			return Mono.error(new McpError("No message endpoint available"));
		}

		try {
			String jsonText = this.objectMapper.writeValueAsString(message);
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(this.baseUri + endpoint))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(jsonText))
				.build();

			return Mono.fromFuture(
					httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenAccept(response -> {
						if (response.statusCode() != 200) {
							logger.error("Error sending message: {}", response.statusCode());
						}
					}));
		}
		catch (IOException e) {
			if (!isClosing) {
				return Mono.error(new RuntimeException("Failed to serialize message", e));
			}
			return Mono.empty();
		}
	}

	@Override
	public Mono<Void> closeGracefully() {
		return Mono.fromRunnable(() -> {
			isClosing = true;
			CompletableFuture<Void> future = connectionFuture.get();
			if (future != null && !future.isDone()) {
				future.cancel(true);
			}
		});
	}

	@Override
	public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
		return this.objectMapper.convertValue(data, typeRef);
	}

}
