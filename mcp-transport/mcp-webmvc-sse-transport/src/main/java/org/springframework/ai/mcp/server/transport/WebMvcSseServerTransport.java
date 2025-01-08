package org.springframework.ai.mcp.server.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.spec.McpError;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.ServerMcpTransport;
import org.springframework.ai.mcp.util.Assert;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Server-side implementation of the MCP HTTP with SSE transport specification using
 * Spring WebMVC. This implementation wraps synchronous WebMVC operations in reactive
 * types to maintain compatibility with the reactive transport interface.
 *
 * @author Christian Tzolov
 */
public class WebMvcSseServerTransport implements ServerMcpTransport {

	private final static Logger logger = LoggerFactory.getLogger(WebMvcSseServerTransport.class);

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

	private final RouterFunction<ServerResponse> routerFunction;

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
	 * Constructs a new FunctionalSseServerTransport.
	 * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
	 * @param messageEndpoint The endpoint URI where clients should send messages
	 */
	public WebMvcSseServerTransport(ObjectMapper objectMapper, String messageEndpoint) {
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
		return Mono.empty();
	}

	@Override
	public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
		return Mono.fromRunnable(() -> {
			if (sessions.isEmpty()) {
				logger.debug("No active sessions to broadcast message to");
				return;
			}

			try {
				String jsonText = objectMapper.writeValueAsString(message);
				logger.debug("Attempting to broadcast message to {} active sessions", sessions.size());

				sessions.values().forEach(session -> {
					try {
						session.messageSink.send(SseEmitter.event().name(MESSAGE_EVENT_TYPE).data(jsonText));
					}
					catch (IOException e) {
						logger.error("Failed to send message to session {}: {}", session.id, e.getMessage());
					}
				});
			}
			catch (IOException e) {
				logger.error("Failed to serialize message: {}", e.getMessage());
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

			sessions.values().forEach(session -> {
				String sessionId = session.id;
				session.close();
				sessions.remove(sessionId);
			});

			logger.info("Graceful shutdown completed");
		});
	}

	/**
	 * Get the router function for configuring the web server.
	 */
	public RouterFunction<ServerResponse> getRouterFunction() {
		return this.routerFunction;
	}

	/**
	 * Handles new SSE connection requests from clients.
	 */
	private ServerResponse handleSseConnection(ServerRequest request) {
		if (isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
		}

		String sessionId = UUID.randomUUID().toString();
		logger.debug("Creating new SSE connection for session: {}", sessionId);

		ClientSession session = new ClientSession(sessionId);
		this.sessions.put(sessionId, session);

		// Send initial endpoint event
		try {
			session.messageSink.send(SseEmitter.event().name(ENDPOINT_EVENT_TYPE).data(messageEndpoint));
		}
		catch (IOException e) {
			logger.error("Failed to send initial endpoint event to session {}: {}", sessionId, e.getMessage());
			sessions.remove(sessionId);
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}

		session.messageSink.onCompletion(() -> {
			logger.debug("Session {} completed", sessionId);
			sessions.remove(sessionId);
		});

		session.messageSink.onError(error -> {
			logger.error("Error in session {}: {}", sessionId, error.getMessage());
			sessions.remove(sessionId);
		});

		session.messageSink.onTimeout(() -> {
			logger.debug("Session {} timed out", sessionId);
			sessions.remove(sessionId);
		});

		return ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(session.messageSink);
	}

	/**
	 * Handles incoming messages from clients.
	 */
	private ServerResponse handleMessage(ServerRequest request) {
		if (isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
		}

		try {
			String body = request.body(String.class);
			McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, body);

			// Convert the message to a Mono, apply the handler, and block for the
			// response
			McpSchema.JSONRPCMessage response = Mono.just(message).transform(connectHandler).block();

			return ServerResponse.ok().build();
		}
		catch (IllegalArgumentException | IOException e) {
			logger.error("Failed to deserialize message: {}", e.getMessage());
			return ServerResponse.badRequest().body(new McpError("Invalid message format"));
		}
		catch (Exception e) {
			logger.error("Error handling message: {}", e.getMessage());
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new McpError(e.getMessage()));
		}
	}

	/**
	 * Represents an active client session.
	 */
	private static class ClientSession {

		private final String id;

		private final SseEmitter messageSink;

		ClientSession(String id) {
			this.id = id;
			this.messageSink = new SseEmitter(0L); // No timeout
			logger.debug("Session {} initialized with SSE emitter", id);
		}

		void close() {
			logger.debug("Closing session: {}", id);
			try {
				messageSink.complete();
				logger.debug("Successfully completed SSE emitter for session {}", id);
			}
			catch (Exception e) {
				logger.warn("Failed to complete SSE emitter for session {}: {}", id, e.getMessage());
			}
		}

	}

}
