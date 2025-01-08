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

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import reactor.util.retry.Retry.RetrySignal;

import org.springframework.ai.mcp.spec.ClientMcpTransport;
import org.springframework.ai.mcp.spec.McpError;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCMessage;
import org.springframework.ai.mcp.util.Assert;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Server-Sent Events (SSE) implementation of the
 * {@link org.springframework.ai.mcp.spec.McpTransport} that follows the MCP HTTP with SSE
 * transport specification.
 *
 * <p>
 * This transport establishes a bidirectional communication channel where:
 * <ul>
 * <li>Inbound messages are received through an SSE connection from the server</li>
 * <li>Outbound messages are sent via HTTP POST requests to a server-provided
 * endpoint</li>
 * </ul>
 *
 * <p>
 * The message flow follows these steps:
 * <ol>
 * <li>The client establishes an SSE connection to the server's /sse endpoint</li>
 * <li>The server sends an 'endpoint' event containing the URI for sending messages</li>
 * </ol>
 * 
 * This implementation handles automatic reconnection for transient failures and provides
 * graceful shutdown capabilities. It uses {@link WebClient} for HTTP communications and
 * supports JSON serialization/deserialization of messages.
 *
 * @author Christian Tzolov
 * @see <a href=
 * "https://spec.modelcontextprotocol.io/specification/basic/transports/#http-with-sse">MCP
 * HTTP with SSE Transport Specification</a>
 */
public class WebFluxSseClientTransport implements ClientMcpTransport {

	private final static Logger logger = LoggerFactory.getLogger(WebFluxSseClientTransport.class);

	/**
	 * Event type for JSON-RPC messages received through the SSE connection. The server
	 * sends messages with this event type to transmit JSON-RPC protocol data.
	 */
	private final static String MESSAGE_EVENT_TYPE = "message";

	/**
	 * Event type for receiving the message endpoint URI from the server. The server MUST
	 * send this event when a client connects, providing the URI where the client should
	 * send its messages via HTTP POST.
	 */
	private final static String ENDPOINT_EVENT_TYPE = "endpoint";

	/**
	 * Default SSE endpoint path as specified by the MCP transport specification. This
	 * endpoint is used to establish the SSE connection with the server.
	 */
	private final static String SSE_ENDPOINT = "/sse";

	/**
	 * Type reference for parsing SSE events containing string data.
	 */
	private final static ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE = new ParameterizedTypeReference<>() {
	};

	/**
	 * WebClient instance for handling both SSE connections and HTTP POST requests. Used
	 * for establishing the SSE connection and sending outbound messages.
	 */
	private final WebClient webClient;

	/**
	 * ObjectMapper for serializing outbound messages and deserializing inbound messages.
	 * Handles conversion between JSON-RPC messages and their string representation.
	 */
	protected ObjectMapper objectMapper;

	/**
	 * Subscription for the SSE connection handling inbound messages. Used for cleanup
	 * during transport shutdown.
	 */
	private Disposable inboundSubscription;

	/**
	 * Flag indicating if the transport is in the process of shutting down. Used to
	 * prevent new operations during shutdown and handle cleanup gracefully.
	 */
	private volatile boolean isClosing = false;

	/**
	 * Sink for managing the message endpoint URI provided by the server. Stores the most
	 * recent endpoint URI and makes it available for outbound message processing.
	 */
	protected final Sinks.One<String> messageEndpointSink = Sinks.one();

	/**
	 * Constructs a new SseClientTransport with the specified WebClient builder. Uses a
	 * default ObjectMapper instance for JSON processing.
	 * @param webClientBuilder the WebClient.Builder to use for creating the WebClient
	 * instance
	 * @throws IllegalArgumentException if webClientBuilder is null
	 */
	public WebFluxSseClientTransport(WebClient.Builder webClientBuilder) {
		this(webClientBuilder, new ObjectMapper());
	}

	/**
	 * Constructs a new SseClientTransport with the specified WebClient builder and
	 * ObjectMapper. Initializes both inbound and outbound message processing pipelines.
	 * @param webClientBuilder the WebClient.Builder to use for creating the WebClient
	 * instance
	 * @param objectMapper the ObjectMapper to use for JSON processing
	 * @throws IllegalArgumentException if either parameter is null
	 */
	public WebFluxSseClientTransport(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		Assert.notNull(webClientBuilder, "WebClient.Builder must not be null");

		this.objectMapper = objectMapper;
		this.webClient = webClientBuilder.build();
	}

	@Override
	public Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
		Flux<ServerSentEvent<String>> events = eventStream();
		this.inboundSubscription = events.concatMap(event -> Mono.just(event).<JSONRPCMessage>handle((e, s) -> {
			if (ENDPOINT_EVENT_TYPE.equals(event.event())) {
				String messageEndpointUri = event.data();
				if (messageEndpointSink.tryEmitValue(messageEndpointUri).isSuccess()) {
					s.complete();
				}
				else {
					// TODO: clarify with the spec if multiple events can be
					// received
					s.error(new McpError("Failed to handle SSE endpoint event"));
				}
			}
			else if (MESSAGE_EVENT_TYPE.equals(event.event())) {
				try {
					JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.objectMapper, event.data());
					s.next(message);
				}
				catch (IOException ioException) {
					s.error(ioException);
				}
			}
			else {
				s.error(new McpError("Received unrecognized SSE event type: " + event.event()));
			}
		}).transform(handler)).subscribe();

		// The connection is established once the server sends the endpoint event
		return messageEndpointSink.asMono().then();
	}

	@Override
	public Mono<Void> sendMessage(JSONRPCMessage message) {
		// The messageEndpoint is the endpoint URI to send the messages
		// It is provided by the server as part of the endpoint event
		return messageEndpointSink.asMono().flatMap(messageEndpointUri -> {
			if (isClosing) {
				return Mono.empty();
			}
			try {
				String jsonText = this.objectMapper.writeValueAsString(message);
				return webClient.post()
					.uri(messageEndpointUri)
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(jsonText)
					.retrieve()
					.toBodilessEntity()
					.doOnSuccess(response -> {
						logger.debug("Message sent successfully");
					})
					.doOnError(error -> {
						if (!isClosing) {
							logger.error("Error sending message: {}", error.getMessage());
						}
					});
			}
			catch (IOException e) {
				if (!isClosing) {
					return Mono.error(new RuntimeException("Failed to serialize message", e));
				}
				return Mono.empty();
			}
		}).then(); // TODO: Consider non-200-ok response
	}

	/**
	 * Initializes and starts the inbound SSE event processing. Establishes the SSE
	 * connection and sets up event handling for both message and endpoint events.
	 * Includes automatic retry logic for handling transient connection failures.
	 */
	// visible for tests
	protected Flux<ServerSentEvent<String>> eventStream() {// @formatter:off
		return this.webClient
			.get()
			.uri(SSE_ENDPOINT)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.retrieve()
			.bodyToFlux(SSE_TYPE)
			.retryWhen(Retry.from(retrySignal -> retrySignal.handle(inboundRetryHandler)));
	} // @formatter:on

	/**
	 * Retry handler for the inbound SSE stream. Implements the retry logic for handling
	 * connection failures and other errors.
	 */
	private BiConsumer<RetrySignal, SynchronousSink<Object>> inboundRetryHandler = (retrySpec, sink) -> {
		if (isClosing) {
			logger.debug("SSE connection closed during shutdown");
			sink.error(retrySpec.failure());
			return;
		}
		if (retrySpec.failure() instanceof IOException) {
			logger.debug("Retrying SSE connection after IO error");
			sink.next(retrySpec);
			return;
		}
		logger.error("Fatal SSE error, not retrying: {}", retrySpec.failure().getMessage());
		sink.error(retrySpec.failure());
	};

	/**
	 * Implements graceful shutdown of the transport. Cleans up all resources including
	 * subscriptions and schedulers. Ensures orderly shutdown of both inbound and outbound
	 * message processing.
	 * @return a Mono that completes when shutdown is finished
	 */
	@Override
	public Mono<Void> closeGracefully() { // @formatter:off
		return Mono.fromRunnable(() -> {
			isClosing = true;
			
			// Dispose of subscriptions
			
			if (inboundSubscription != null) {
				inboundSubscription.dispose();
			}

		})
		.then()
		.subscribeOn(Schedulers.boundedElastic());
	} // @formatter:on

	@Override
	public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
		return this.objectMapper.convertValue(data, typeRef);
	}

}
