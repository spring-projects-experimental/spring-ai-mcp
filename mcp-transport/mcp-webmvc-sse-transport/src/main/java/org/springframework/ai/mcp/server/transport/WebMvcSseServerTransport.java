/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.server.transport;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

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
						var event = new SSEEvent(session.id, MESSAGE_EVENT_TYPE, jsonText);
						session.queue.put(event);
					}
					catch (Exception e) {
						logger.error("Failed to send message to session {}: {}", session.id, e.getMessage());
					}
				});
			}
			catch (IOException e) {
				logger.error("Failed to serialize message: {}", e.getMessage());
			}
		});
	}

	/**
	 * Handles new SSE connection requests from clients.
	 */
	private ServerResponse handleSseConnection(ServerRequest request) {
		if (this.isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
		}

		String sessionId = UUID.randomUUID().toString();
		logger.debug("Creating new SSE connection for session: {}", sessionId);

		ClientSession session = new ClientSession(sessionId);
		this.sessions.put(sessionId, session);

		// Send initial endpoint event
		try {
			session.queue.put(new SSEEvent(session.id, ENDPOINT_EVENT_TYPE, messageEndpoint));
			return ServerResponse.sse(sseBuilder -> {
				// new Thread(() -> {
				while (!this.isClosing) {
					try {
						SSEEvent sseEvent = session.queue.poll(100, TimeUnit.MILLISECONDS);
						if (sseEvent != null) {
							sseBuilder.id(sseEvent.id).event(sseEvent.type()).data(sseEvent.data());
						}
					}
					catch (Exception e) {
						logger.error("Failed to poll event from session queue: {}", e.getMessage());
						sseBuilder.error(e);
					}
				}
				// }).start();
			});
		}
		catch (Exception e) {
			logger.error("Failed to send initial endpoint event to session {}: {}", sessionId, e.getMessage());
			sessions.remove(sessionId);
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Handles incoming messages from clients.
	 */
	private ServerResponse handleMessage(ServerRequest request) {
		if (this.isClosing) {
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

	record SSEEvent(String id, String type, String data) {
	}

	/**
	 * Represents an active client session.
	 */
	private static class ClientSession {

		private final String id;

		private final BlockingQueue<SSEEvent> queue;

		ClientSession(String id) {
			this.id = id;
			this.queue = new LinkedBlockingQueue<>();
			logger.debug("Session {} initialized with SSE emitter", id);
		}

		void close() {
			logger.debug("Closing session: {}", id);
			try {
				queue.remove();
				logger.debug("Successfully completed SSE emitter for session {}", id);
			}
			catch (Exception e) {
				logger.warn("Failed to complete SSE emitter for session {}: {}", id, e.getMessage());
			}
		}

	}

	@Override
	public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
		return this.objectMapper.convertValue(data, typeRef);
	}

	@Override
	public Mono<Void> closeGracefully() {
		return Mono.fromRunnable(() -> {
			this.isClosing = true;
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

}
