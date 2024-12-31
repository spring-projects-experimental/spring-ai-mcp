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

package org.springframework.ai.mcp.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.ClientCapabilities;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCNotification;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCRequest;
import org.springframework.ai.mcp.spec.McpSchema.Root;
import org.springframework.ai.mcp.spec.McpTransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class McpAsyncClientResponseHandlerTests {

	@SuppressWarnings("unused")
	private static class MockMcpTransport implements McpTransport {

		private final AtomicInteger inboundMessageCount = new AtomicInteger(0);

		private final Sinks.Many<McpSchema.JSONRPCMessage> outgoing = Sinks.many().multicast().onBackpressureBuffer();

		private final Sinks.Many<McpSchema.JSONRPCMessage> inbound = Sinks.many().unicast().onBackpressureBuffer();

		private final Flux<McpSchema.JSONRPCMessage> outboundView = outgoing.asFlux().cache(1);

		public void simulateIncomingMessage(McpSchema.JSONRPCMessage message) {
			if (inbound.tryEmitNext(message).isFailure()) {
				throw new RuntimeException("Failed to emit message " + message);
			}
			inboundMessageCount.incrementAndGet();
		}

		@Override
		public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
			if (outgoing.tryEmitNext(message).isFailure()) {
				return Mono.error(new RuntimeException("Can't emit outgoing message " + message));
			}
			return Mono.empty();
		}

		public McpSchema.JSONRPCRequest getLastSentMessageAsRequest() {
			return (JSONRPCRequest) outboundView.blockFirst();
		}

		public McpSchema.JSONRPCNotification getLastSentMessageAsNotifiation() {
			return (JSONRPCNotification) outboundView.blockFirst();
		}

		public McpSchema.JSONRPCMessage getLastSentMessage() {
			return outboundView.blockFirst();
		}

		private volatile boolean connected = false;

		@Override
		public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
			if (connected) {
				return Mono.error(new IllegalStateException("Already connected"));
			}
			connected = true;
			return inbound.asFlux()
				.publishOn(Schedulers.boundedElastic())
				.flatMap(message -> Mono.just(message).transform(handler))
				.doFinally(signal -> connected = false)
				.then();
		}

		@Override
		public Mono<Void> closeGracefully() {
			return Mono.defer(() -> {
				connected = false;
				outgoing.tryEmitComplete();
				inbound.tryEmitComplete();
				return Mono.empty();
			});
		}

		@Override
		public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
			return new ObjectMapper().convertValue(data, typeRef);
		}

	}

	@Test
	void testToolsChangeNotificationHandling() {
		MockMcpTransport transport = new MockMcpTransport();

		// Create a list to store received tools for verification
		List<McpSchema.Tool> receivedTools = new ArrayList<>();

		// Create a consumer that will be called when tools change
		Consumer<List<McpSchema.Tool>> toolsChangeConsumer = tools -> {
			receivedTools.addAll(tools);
		};

		// Create client with tools change consumer
		McpAsyncClient asyncMcpClient = McpClient.using(transport).toolsChangeConsumer(toolsChangeConsumer).async();

		// Create a mock tools list that the server will return
		Map<String, Object> inputSchema = Map.of("type", "object", "properties", Map.of(), "required", List.of());
		McpSchema.Tool mockTool = new McpSchema.Tool("test-tool", "Test Tool Description", inputSchema);
		McpSchema.ListToolsResult mockToolsResult = new McpSchema.ListToolsResult(List.of(mockTool), null);

		// Simulate server sending tools/list_changed notification
		McpSchema.JSONRPCNotification notification = new McpSchema.JSONRPCNotification(McpSchema.JSONRPC_VERSION,
				McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED, null);
		transport.simulateIncomingMessage(notification);

		// Simulate server response to tools/list request
		McpSchema.JSONRPCRequest toolsListRequest = transport.getLastSentMessageAsRequest();
		assertThat(toolsListRequest.method()).isEqualTo(McpSchema.METHOD_TOOLS_LIST);

		McpSchema.JSONRPCResponse toolsListResponse = new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION,
				toolsListRequest.id(), mockToolsResult, null);
		transport.simulateIncomingMessage(toolsListResponse);

		// Verify the consumer received the expected tools
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(receivedTools).hasSize(1);
			assertThat(receivedTools.get(0).name()).isEqualTo("test-tool");
			assertThat(receivedTools.get(0).description()).isEqualTo("Test Tool Description");
		});

		asyncMcpClient.closeGracefully();
	}

	@Test
	void testRootsListRequestHandling() {
		MockMcpTransport transport = new MockMcpTransport();

		McpAsyncClient asyncMcpClient = McpClient.using(transport)
			.roots(new Root("file:///test/path", "test-root"))
			.async();

		// Simulate incoming request
		McpSchema.JSONRPCRequest request = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION,
				McpSchema.METHOD_ROOTS_LIST, "test-id", null);
		transport.simulateIncomingMessage(request);

		// Verify response
		McpSchema.JSONRPCMessage sentMessage = transport.getLastSentMessage();
		assertThat(sentMessage).isInstanceOf(McpSchema.JSONRPCResponse.class);

		McpSchema.JSONRPCResponse response = (McpSchema.JSONRPCResponse) sentMessage;
		assertThat(response.id()).isEqualTo("test-id");
		assertThat(response.result()).isEqualTo(List.of(new Root("file:///test/path", "test-root")));
		assertThat(response.error()).isNull();

		asyncMcpClient.closeGracefully();
	}

	@Test
	void testResourcesChangeNotificationHandling() {
		MockMcpTransport transport = new MockMcpTransport();

		// Create a list to store received resources for verification
		List<McpSchema.Resource> receivedResources = new ArrayList<>();

		// Create a consumer that will be called when resources change
		Consumer<List<McpSchema.Resource>> resourcesChangeConsumer = resources -> {
			receivedResources.addAll(resources);
		};

		// Create client with resources change consumer
		McpAsyncClient asyncMcpClient = McpClient.using(transport)
			.resourcesChangeConsumer(resourcesChangeConsumer)
			.async();

		// Create a mock resources list that the server will return
		McpSchema.Resource mockResource = new McpSchema.Resource("test://resource", "Test Resource", "A test resource",
				"text/plain", null);
		McpSchema.ListResourcesResult mockResourcesResult = new McpSchema.ListResourcesResult(List.of(mockResource),
				null);

		// Simulate server sending resources/list_changed notification
		McpSchema.JSONRPCNotification notification = new McpSchema.JSONRPCNotification(McpSchema.JSONRPC_VERSION,
				McpSchema.METHOD_NOTIFICATION_RESOURCES_LIST_CHANGED, null);
		transport.simulateIncomingMessage(notification);

		// Simulate server response to resources/list request
		McpSchema.JSONRPCRequest resourcesListRequest = transport.getLastSentMessageAsRequest();
		assertThat(resourcesListRequest.method()).isEqualTo(McpSchema.METHOD_RESOURCES_LIST);

		McpSchema.JSONRPCResponse resourcesListResponse = new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION,
				resourcesListRequest.id(), mockResourcesResult, null);
		transport.simulateIncomingMessage(resourcesListResponse);

		// Verify the consumer received the expected resources
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(receivedResources).hasSize(1);
			assertThat(receivedResources.get(0).uri()).isEqualTo("test://resource");
			assertThat(receivedResources.get(0).name()).isEqualTo("Test Resource");
			assertThat(receivedResources.get(0).description()).isEqualTo("A test resource");
		});

		asyncMcpClient.closeGracefully();
	}

	@Test
	void testPromptsChangeNotificationHandling() {
		MockMcpTransport transport = new MockMcpTransport();

		// Create a list to store received prompts for verification
		List<McpSchema.Prompt> receivedPrompts = new ArrayList<>();

		// Create a consumer that will be called when prompts change
		Consumer<List<McpSchema.Prompt>> promptsChangeConsumer = prompts -> {
			receivedPrompts.addAll(prompts);
		};

		// Create client with prompts change consumer
		McpAsyncClient asyncMcpClient = McpClient.using(transport).promptsChangeConsumer(promptsChangeConsumer).async();

		// Create a mock prompts list that the server will return
		McpSchema.Prompt mockPrompt = new McpSchema.Prompt("test-prompt", "Test Prompt Description",
				List.of(new McpSchema.PromptArgument("arg1", "Test argument", true)));
		McpSchema.ListPromptsResult mockPromptsResult = new McpSchema.ListPromptsResult(List.of(mockPrompt), null);

		// Simulate server sending prompts/list_changed notification
		McpSchema.JSONRPCNotification notification = new McpSchema.JSONRPCNotification(McpSchema.JSONRPC_VERSION,
				McpSchema.METHOD_NOTIFICATION_PROMPTS_LIST_CHANGED, null);
		transport.simulateIncomingMessage(notification);

		// Simulate server response to prompts/list request
		McpSchema.JSONRPCRequest promptsListRequest = transport.getLastSentMessageAsRequest();
		assertThat(promptsListRequest.method()).isEqualTo(McpSchema.METHOD_PROMPT_LIST);

		McpSchema.JSONRPCResponse promptsListResponse = new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION,
				promptsListRequest.id(), mockPromptsResult, null);
		transport.simulateIncomingMessage(promptsListResponse);

		// Verify the consumer received the expected prompts
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(receivedPrompts).hasSize(1);
			assertThat(receivedPrompts.get(0).name()).isEqualTo("test-prompt");
			assertThat(receivedPrompts.get(0).description()).isEqualTo("Test Prompt Description");
			assertThat(receivedPrompts.get(0).arguments()).hasSize(1);
			assertThat(receivedPrompts.get(0).arguments().get(0).name()).isEqualTo("arg1");
		});

		asyncMcpClient.closeGracefully();
	}

	@Test
	void testSamplingCreateMessageRequestHandling() {
		MockMcpTransport transport = new MockMcpTransport();

		// Create a test sampling handler that echoes back the input
		Function<McpSchema.CreateMessageRequest, McpSchema.CreateMessageResult> samplingHandler = request -> {
			var content = request.messages().get(0).content();
			return new McpSchema.CreateMessageResult(McpSchema.Role.ASSISTANT, content, "test-model",
					McpSchema.CreateMessageResult.StopReason.END_TURN);
		};

		// Create client with sampling capability and handler
		McpAsyncClient asyncMcpClient = McpClient.using(transport)
			.capabilities(ClientCapabilities.builder().sampling().build())
			.sampling(samplingHandler)
			.async();

		// Create a mock create message request
		var messageRequest = new McpSchema.CreateMessageRequest(
				List.of(new McpSchema.SamplingMessage(McpSchema.Role.USER, new McpSchema.TextContent("Test message"))),
				null, // modelPreferences
				"Test system prompt", McpSchema.CreateMessageRequest.ContextInclusionStrategy.NONE, 0.7, // temperature
				100, // maxTokens
				null, // stopSequences
				null // metadata
		);

		// Simulate incoming request
		McpSchema.JSONRPCRequest request = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION,
				McpSchema.METHOD_SAMPLING_CREATE_MESSAGE, "test-id", messageRequest);
		transport.simulateIncomingMessage(request);

		// Verify response
		McpSchema.JSONRPCMessage sentMessage = transport.getLastSentMessage();
		assertThat(sentMessage).isInstanceOf(McpSchema.JSONRPCResponse.class);

		McpSchema.JSONRPCResponse response = (McpSchema.JSONRPCResponse) sentMessage;
		assertThat(response.id()).isEqualTo("test-id");
		assertThat(response.error()).isNull();

		McpSchema.CreateMessageResult result = transport.unmarshalFrom(response.result(),
				new TypeReference<McpSchema.CreateMessageResult>() {
				});
		assertThat(result).isNotNull();
		assertThat(result.role()).isEqualTo(McpSchema.Role.ASSISTANT);
		assertThat(result.content()).isNotNull();
		assertThat(result.model()).isEqualTo("test-model");
		assertThat(result.stopReason()).isEqualTo(McpSchema.CreateMessageResult.StopReason.END_TURN);

		asyncMcpClient.closeGracefully();
	}

	@Test
	void testSamplingCreateMessageRequestHandlingWithoutCapability() {
		MockMcpTransport transport = new MockMcpTransport();

		// Create client without sampling capability
		McpAsyncClient asyncMcpClient = McpClient.using(transport)
			.capabilities(ClientCapabilities.builder().build()) // No sampling capability
			.async();

		// Create a mock create message request
		var messageRequest = new McpSchema.CreateMessageRequest(
				List.of(new McpSchema.SamplingMessage(McpSchema.Role.USER, new McpSchema.TextContent("Test message"))),
				null, null, null, null, 0, null, null);

		// Simulate incoming request
		McpSchema.JSONRPCRequest request = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION,
				McpSchema.METHOD_SAMPLING_CREATE_MESSAGE, "test-id", messageRequest);
		transport.simulateIncomingMessage(request);

		// Verify error response
		McpSchema.JSONRPCMessage sentMessage = transport.getLastSentMessage();
		assertThat(sentMessage).isInstanceOf(McpSchema.JSONRPCResponse.class);

		McpSchema.JSONRPCResponse response = (McpSchema.JSONRPCResponse) sentMessage;
		assertThat(response.id()).isEqualTo("test-id");
		assertThat(response.result()).isNull();
		assertThat(response.error()).isNotNull();
		assertThat(response.error().message()).contains("Method not found: sampling/createMessage");

		asyncMcpClient.closeGracefully();
	}

	@Test
	void testSamplingCreateMessageRequestHandlingWithNullHandler() {
		MockMcpTransport transport = new MockMcpTransport();

		// Create client with sampling capability but null handler
		McpAsyncClient asyncMcpClient = McpClient.using(transport)
			.capabilities(ClientCapabilities.builder().sampling().build())
			.async();

		// Create a mock create message request
		var messageRequest = new McpSchema.CreateMessageRequest(
				List.of(new McpSchema.SamplingMessage(McpSchema.Role.USER, new McpSchema.TextContent("Test message"))),
				null, null, null, null, 0, null, null);

		// Simulate incoming request
		McpSchema.JSONRPCRequest request = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION,
				McpSchema.METHOD_SAMPLING_CREATE_MESSAGE, "test-id", messageRequest);
		transport.simulateIncomingMessage(request);

		// Verify error response
		McpSchema.JSONRPCMessage sentMessage = transport.getLastSentMessage();
		assertThat(sentMessage).isInstanceOf(McpSchema.JSONRPCResponse.class);

		McpSchema.JSONRPCResponse response = (McpSchema.JSONRPCResponse) sentMessage;
		assertThat(response.id()).isEqualTo("test-id");
		assertThat(response.result()).isNull();
		assertThat(response.error()).isNotNull();
		assertThat(response.error().message()).contains("Method not found: sampling/createMessage");

		asyncMcpClient.closeGracefully();
	}

}
