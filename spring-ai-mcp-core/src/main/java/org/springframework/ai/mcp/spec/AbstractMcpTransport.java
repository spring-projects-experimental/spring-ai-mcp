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

package org.springframework.ai.mcp.spec;

import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import org.springframework.ai.mcp.client.util.Assert;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCMessage;

/**
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */
// TODO: The API is not finalized. Implementing SSE will dictate a more concrete
// structure and relationship between the base and the actual implementation including
// proper encapsulation and state management.
public abstract class AbstractMcpTransport implements McpTransport {

	protected final ObjectMapper objectMapper;

	private final Sinks.Many<String> errorSink;

	private final Sinks.Many<JSONRPCMessage> inboundSink;

	private final Sinks.Many<JSONRPCMessage> outboundSink;

	private Consumer<JSONRPCMessage> inboundMessageHandler = message -> System.out
		.println("Received message: " + message);

	private Consumer<String> errorHandler = error -> System.err.println("Received error: " + error);

	public AbstractMcpTransport() {
		this(new ObjectMapper());
	}

	public AbstractMcpTransport(ObjectMapper objectMapper) {

		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		this.objectMapper = objectMapper;

		this.errorSink = Sinks.many().unicast().onBackpressureBuffer();
		this.inboundSink = Sinks.many().unicast().onBackpressureBuffer();
		this.outboundSink = Sinks.many().unicast().onBackpressureBuffer();
	}

	public ObjectMapper getObjectMapper() {
		return this.objectMapper;
	}

	private void handleIncomingMessages() {
		this.inboundSink.asFlux().subscribe(message -> this.inboundMessageHandler.accept(message));
	}

	private void handleIncomingErrors() {
		this.errorSink.asFlux().subscribe(e -> {
			this.errorHandler.accept(e);
		});
	}

	protected Sinks.Many<JSONRPCMessage> getInboundSink() {
		return inboundSink;
	}

	protected Sinks.Many<JSONRPCMessage> getOutboundSink() {
		return outboundSink;
	}

	protected Sinks.Many<String> getErrorSink() {
		return errorSink;
	}

	public void setInboundMessageHandler(Consumer<JSONRPCMessage> inboundMessageHandler) {
		this.inboundMessageHandler = inboundMessageHandler;
	}

	public void setInboundErrorHandler(Consumer<String> errorHandler) {
		this.errorHandler = errorHandler;
	}

	// Start processing incoming messages
	public void start() {
		this.handleIncomingErrors();
		this.handleIncomingMessages();
	}

	@Override
	public Mono<Void> sendMessage(JSONRPCMessage message) {
		if (this.outboundSink.tryEmitNext(message).isSuccess()) {
			// TODO: essentially we could reschedule ourselves in some time and make
			// another attempt with the already read data but pause reading until
			// success
			// In this approach we delegate the retry and the backpressure onto the
			// caller. This might be enough for most cases.
			return Mono.empty();
		}
		else {
			return Mono.error(new RuntimeException("Failed to enqueue message"));
		}
	}

}
