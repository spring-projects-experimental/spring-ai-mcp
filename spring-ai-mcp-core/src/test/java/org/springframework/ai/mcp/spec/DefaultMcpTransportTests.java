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

import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.spec.McpSchema.JSONRPCMessage;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultMcpTransportTests {

	private AbstractMcpTransport transport;

	@BeforeEach
	void setUp() {
		transport = new AbstractMcpTransport() {
			@Override
			public Mono<Void> closeGracefully() {
				return Mono.empty();
			}
		};
	}

	@Test
	void sendMessageShouldEmitToOutboundSink() {
		JSONRPCRequest message = new JSONRPCRequest("2.0", "test", 1, null);

		AtomicReference<JSONRPCMessage> receivedMessage = new AtomicReference<>();
		transport.getOutboundSink().asFlux().subscribe(receivedMessage::set);

		Mono<Void> result = transport.sendMessage(message);

		StepVerifier.create(result).verifyComplete();

		assertThat(receivedMessage.get()).isNotNull().satisfies(msg -> {
			assertThat(((JSONRPCRequest) msg).jsonrpc()).isEqualTo("2.0");
			assertThat(((JSONRPCRequest) msg).method()).isEqualTo("test");
		});
	}

	@Test
	void customInboundMessageHandlerShouldReceiveMessages() {
		AtomicReference<JSONRPCMessage> receivedMessage = new AtomicReference<>();
		transport.setInboundMessageHandler(receivedMessage::set);
		transport.start();

		JSONRPCRequest message = new JSONRPCRequest("2.0", "test", 1, null);

		transport.getInboundSink().tryEmitNext(message);

		assertThat(receivedMessage.get()).isNotNull().satisfies(msg -> {
			assertThat(((JSONRPCRequest) msg).jsonrpc()).isEqualTo("2.0");
			assertThat(((JSONRPCRequest) msg).method()).isEqualTo("test");
		});
	}

	@Test
	void startShouldInitializeMessage() {
		AtomicReference<JSONRPCMessage> receivedMessage = new AtomicReference<>();

		transport.setInboundMessageHandler(receivedMessage::set);
		transport.start();

		JSONRPCRequest message = new JSONRPCRequest("2.0", "test", 1, null);

		String errorMessage = "Test error";

		transport.getInboundSink().tryEmitNext(message);

		assertThat(receivedMessage.get()).isNotNull();
	}

	@Test
	void sendMessageShouldReturnErrorWhenSinkFails() {
		JSONRPCRequest message = new JSONRPCRequest("2.0", "test", 1, null);
		// Close the sink to simulate failure
		transport.getOutboundSink().tryEmitComplete();

		Mono<Void> result = transport.sendMessage(message);

		StepVerifier.create(result)
			.verifyErrorSatisfies(error -> assertThat(error).isInstanceOf(RuntimeException.class)
				.hasMessage("Failed to enqueue message"));
	}

}
