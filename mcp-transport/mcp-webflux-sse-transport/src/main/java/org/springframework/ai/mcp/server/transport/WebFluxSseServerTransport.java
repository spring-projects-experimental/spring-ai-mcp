package org.springframework.ai.mcp.server.transport;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import org.springframework.ai.mcp.spec.McpError;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.ServerMcpTransport;
import org.springframework.ai.mcp.util.Assert;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Server-side implementation of the MCP HTTP with SSE transport specification. Provides
 * endpoints for SSE connections and message handling.
 *
 * @author Christian Tzolov
 */
public class WebFluxSseServerTransport implements ServerMcpTransport {

	private final static Logger logger = LoggerFactory.getLogger(WebFluxSseServerTransport.class);

	/**
	 * Event type for JSON-RPC messages sent through the SSE connection.
	 */
	public final static String MESSAGE_EVENT_TYPE = "message";

	/**
	 * Event type for sending the message endpoint URI to clients.
	 */
	public final static String ENDPOINT_EVENT_TYPE = "endpoint";

	/**
	 * Default SSE endpoint path as specified by the MCP transport specification.
	 */
	public final static String SSE_ENDPOINT = "/sse";

	private final ObjectMapper objectMapper;

	private final String messageEndpoint;

	private final RouterFunction<?> routerFunction;

	/**
	 * Map of active client sessions, keyed by session ID.
	 */
	private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

	/**
	 * Flag indicating if the transport is shutting down.
	 */
	private volatile boolean isClosing = false;

	private Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> connectHandler;

	/**
	 * Constructs a new SseServerTransport.
	 * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
	 * @param messageEndpoint The endpoint URI where clients should send messages
	 */
	public WebFluxSseServerTransport(ObjectMapper objectMapper, String messageEndpoint) {
		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		Assert.notNull(messageEndpoint, "Message endpoint must not be null");

		this.objectMapper = objectMapper;
		this.messageEndpoint = messageEndpoint;
		this.routerFunction = RouterFunctions.route()
			.GET(SSE_ENDPOINT, this::handleSseConnection)
			.POST(messageEndpoint, this::handleMessage)
			.build();
	}

	@Override
	public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
		this.connectHandler = handler;
		// Server-side transport doesn't initiate connections
		return Mono.empty().then();
	}

	@Override
	public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
		if (sessions.isEmpty()) {
			logger.debug("No active sessions to broadcast message to");
			return Mono.empty();
		}

		return Mono.<Void>create(sink -> {
			try {// @formatter:off
				String jsonText = objectMapper.writeValueAsString(message);
				ServerSentEvent<Object> event = ServerSentEvent.builder()
					.event(MESSAGE_EVENT_TYPE)
					.data(jsonText)
					.build();

				logger.debug("Attempting to broadcast message to {} active sessions", sessions.size());

				List<String> failedSessions = sessions.values().stream()
					.filter(session -> session.messageSink.tryEmitNext(event).isFailure())
					.map(session -> session.id)
					.toList();

				if (failedSessions.isEmpty()) {
					logger.debug("Successfully broadcast message to all sessions");
					sink.success();
				}
				else {
					String error = "Failed to broadcast message to sessions: " + String.join(", ", failedSessions);
					logger.error(error);
					sink.error(new RuntimeException(error));
				} // @formatter:on
			}
			catch (IOException e) {
				logger.error("Failed to serialize message: {}", e.getMessage());
				sink.error(e);
			}
		});
	}

	@Override
	public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
		return this.objectMapper.convertValue(data, typeRef);
	}

	@Override
	public Mono<Void> closeGracefully() {
		return Mono.fromRunnable(() -> {
			isClosing = true;
			logger.debug("Initiating graceful shutdown with {} active sessions", sessions.size());
		}).then(Mono.when(sessions.values().stream().map(session -> {
			String sessionId = session.id;
			return Mono.fromRunnable(() -> session.close())
				.then(Mono.delay(Duration.ofMillis(100)))
				.then(Mono.fromRunnable(() -> sessions.remove(sessionId)));
		}).toList()))
			.timeout(Duration.ofSeconds(5))
			.doOnSuccess(v -> logger.info("Graceful shutdown completed"))
			.doOnError(e -> logger.error("Error during graceful shutdown: {}", e.getMessage()));
	}

	/**
	 * Get the router function for configuring the web server.
	 */
	public RouterFunction<?> getRouterFunction() {
		return this.routerFunction;
	}

	/**
	 * Handles new SSE connection requests from clients.
	 */
	private Mono<ServerResponse> handleSseConnection(ServerRequest request) {
		if (isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).bodyValue("Server is shutting down");
		}
		String sessionId = UUID.randomUUID().toString();
		logger.debug("Creating new SSE connection for session: {}", sessionId);
		ClientSession session = new ClientSession(sessionId);
		this.sessions.put(sessionId, session);

		return ServerResponse.ok()
			.contentType(MediaType.TEXT_EVENT_STREAM)
			.body(Flux.<ServerSentEvent<?>>create(sink -> {
				// Send initial endpoint event
				logger.debug("Sending initial endpoint event to session: {}", sessionId);
				sink.next(ServerSentEvent.builder().event(ENDPOINT_EVENT_TYPE).data(messageEndpoint).build());

				// Subscribe to session messages
				session.messageSink.asFlux()
					.doOnSubscribe(s -> logger.debug("Session {} subscribed to message sink", sessionId))
					.doOnComplete(() -> {
						logger.debug("Session {} completed", sessionId);
						sessions.remove(sessionId);
					})
					.doOnError(error -> {
						logger.error("Error in session {}: {}", sessionId, error.getMessage());
						sessions.remove(sessionId);
					})
					.doOnCancel(() -> {
						logger.debug("Session {} cancelled", sessionId);
						sessions.remove(sessionId);
					})
					.subscribe(event -> {
						logger.debug("Forwarding event to session {}: {}", sessionId, event);
						sink.next(event);
					}, sink::error, sink::complete);

				sink.onCancel(() -> {
					logger.debug("Session {} cancelled", sessionId);
					sessions.remove(sessionId);
				});
			}), ServerSentEvent.class);
	}

	/**
	 * Handles incoming messages from clients.
	 */
	private Mono<ServerResponse> handleMessage(ServerRequest request) {
		if (isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).bodyValue("Server is shutting down");
		}

		return request.bodyToMono(String.class).flatMap(body -> {
			try {
				McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, body);
				return Mono.just(message)
					.transform(this.connectHandler)
					.flatMap(response -> ServerResponse.ok().build())
					.onErrorResume(error -> {
						logger.error("Error processing message: {}", error.getMessage());
						return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
							.bodyValue(new McpError(error.getMessage()));
					});
			}
			catch (IllegalArgumentException e) {
				logger.error("Failed to deserialize message: {}", e.getMessage());
				return ServerResponse.badRequest().bodyValue(new McpError("Invalid message format"));
			}
			catch (IOException e) {
				logger.error("Failed to deserialize message: {}", e.getMessage());
				return ServerResponse.badRequest().bodyValue(new McpError("Invalid message format"));
			}
		});
	}

	/**
	 * Represents an active client session.
	 */
	private static class ClientSession {

		private final String id;

		private final Sinks.Many<ServerSentEvent<?>> messageSink;

		ClientSession(String id) {
			this.id = id;
			logger.debug("Creating new session: {}", id);
			this.messageSink = Sinks.many().replay().latest();
			logger.debug("Session {} initialized with replay sink", id);
		}

		void close() {
			logger.debug("Closing session: {}", id);
			Sinks.EmitResult result = messageSink.tryEmitComplete();
			if (result.isFailure()) {
				logger.warn("Failed to complete message sink for session {}: {}", id, result);
			}
			else {
				logger.debug("Successfully completed message sink for session {}", id);
			}
		}

	}

}
