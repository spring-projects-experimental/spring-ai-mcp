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
package org.springframework.ai.mcp.server;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.client.McpClient;
import org.springframework.ai.mcp.client.transport.SseClientTransport;
import org.springframework.ai.mcp.server.transport.SseServerTransport;
import org.springframework.ai.mcp.spec.McpError;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.ClientCapabilities;
import org.springframework.ai.mcp.spec.McpSchema.CreateMessageRequest;
import org.springframework.ai.mcp.spec.McpSchema.CreateMessageResult;
import org.springframework.ai.mcp.spec.McpSchema.InitializeResult;
import org.springframework.ai.mcp.spec.McpSchema.Role;
import org.springframework.ai.mcp.spec.McpSchema.Root;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunctions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class SseAsyncIntegrationTests {

	private static final int PORT = 8181;

	private static final String MESSAGE_ENDPOINT = "/mcp/message";

	private DisposableServer httpServer;

	private SseServerTransport mcpServerTransport;

	McpClient.Builder clientBuilder;

	@BeforeEach
	public void before() {
		this.mcpServerTransport = new SseServerTransport(new ObjectMapper(), MESSAGE_ENDPOINT);

		HttpHandler httpHandler = RouterFunctions.toHttpHandler(mcpServerTransport.getRouterFunction());
		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		this.httpServer = HttpServer.create().port(PORT).handle(adapter).bindNow();

		this.clientBuilder = McpClient
			.using(new SseClientTransport(WebClient.builder().baseUrl("http://localhost:" + PORT)));
	}

	@AfterEach
	public void after() {
		if (httpServer != null) {
			httpServer.disposeNow();
		}
	}

	// ---------------------------------------
	// Sampling Tests
	// ---------------------------------------
	@Test
	void testCreateMessageWithoutInitialization() {
		var mcpAsyncServer = McpServer.using(mcpServerTransport).serverInfo("test-server", "1.0.0").async();

		var messages = List
			.of(new McpSchema.SamplingMessage(McpSchema.Role.USER, new McpSchema.TextContent("Test message")));
		var modelPrefs = new McpSchema.ModelPreferences(List.of(), 1.0, 1.0, 1.0);

		var request = new McpSchema.CreateMessageRequest(messages, modelPrefs, null,
				McpSchema.CreateMessageRequest.ContextInclusionStrategy.NONE, null, 100, List.of(), Map.of());

		StepVerifier.create(mcpAsyncServer.createMessage(request)).verifyErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(McpError.class)
				.hasMessage("Client must be initialized. Call the initialize method first!");
		});
	}

	@Test
	void testCreateMessageWithoutSamplingCapabilities() {

		var mcpAsyncServer = McpServer.using(mcpServerTransport).serverInfo("test-server", "1.0.0").async();

		var client = clientBuilder.clientInfo(new McpSchema.Implementation("Sample client", "0.0.0")).sync();

		InitializeResult initResult = client.initialize();
		assertThat(initResult).isNotNull();

		var messages = List
			.of(new McpSchema.SamplingMessage(McpSchema.Role.USER, new McpSchema.TextContent("Test message")));
		var modelPrefs = new McpSchema.ModelPreferences(List.of(), 1.0, 1.0, 1.0);

		var request = new McpSchema.CreateMessageRequest(messages, modelPrefs, null,
				McpSchema.CreateMessageRequest.ContextInclusionStrategy.NONE, null, 100, List.of(), Map.of());

		StepVerifier.create(mcpAsyncServer.createMessage(request)).verifyErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(McpError.class)
				.hasMessage("Client must be configured with sampling capabilities");
		});
	}

	@Test
	void testCreateMessageSuccess() throws InterruptedException {

		var mcpAsyncServer = McpServer.using(mcpServerTransport).serverInfo("test-server", "1.0.0").async();

		Function<CreateMessageRequest, CreateMessageResult> samplingHandler = request -> {
			assertThat(request.messages()).hasSize(1);
			assertThat(request.messages().get(0).content()).isInstanceOf(McpSchema.TextContent.class);

			return new CreateMessageResult(Role.USER, new McpSchema.TextContent("Test message"), "MockModelName",
					CreateMessageResult.StopReason.STOP_SEQUENCE);
		};

		var client = clientBuilder.clientInfo(new McpSchema.Implementation("Sample client", "0.0.0"))
			.capabilities(ClientCapabilities.builder().sampling().build())
			.sampling(samplingHandler)
			.sync();

		InitializeResult initResult = client.initialize();
		assertThat(initResult).isNotNull();

		var messages = List
			.of(new McpSchema.SamplingMessage(McpSchema.Role.USER, new McpSchema.TextContent("Test message")));
		var modelPrefs = new McpSchema.ModelPreferences(List.of(), 1.0, 1.0, 1.0);

		var request = new McpSchema.CreateMessageRequest(messages, modelPrefs, null,
				McpSchema.CreateMessageRequest.ContextInclusionStrategy.NONE, null, 100, List.of(), Map.of());

		StepVerifier.create(mcpAsyncServer.createMessage(request)).consumeNextWith(result -> {
			assertThat(result).isNotNull();
			assertThat(result.role()).isEqualTo(Role.USER);
			assertThat(result.content()).isInstanceOf(McpSchema.TextContent.class);
			assertThat(((McpSchema.TextContent) result.content()).text()).isEqualTo("Test message");
			assertThat(result.model()).isEqualTo("MockModelName");
			assertThat(result.stopReason()).isEqualTo(CreateMessageResult.StopReason.STOP_SEQUENCE);
		}).verifyComplete();
	}

	// ---------------------------------------
	// Roots Tests
	// ---------------------------------------
	@Test
	void testRootsSuccess() {

		List<Root> roots = List.of(new Root("uri1://", "root1"), new Root("uri2://", "root2"));

		AtomicReference<List<Root>> rootsRef = new AtomicReference<>();
		var mcpServer = McpServer.using(mcpServerTransport)
			.rootsChangeConsumer(rootsUpdate -> rootsRef.set(rootsUpdate))
			.sync();

		// HttpHandler httpHandler =
		// RouterFunctions.toHttpHandler(mcpServerTransport.getRouterFunction());
		// ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		// HttpServer httpServer = HttpServer.create().port(8080).handle(adapter);
		// DisposableServer d = httpServer.bindNow();

		var mcpClient = clientBuilder.capabilities(ClientCapabilities.builder().roots(true).build())
			.roots(roots)
			.sync();

		InitializeResult initResult = mcpClient.initialize();
		assertThat(initResult).isNotNull();

		assertThat(rootsRef.get()).isNull();

		assertThat(mcpServer.listRoots().roots()).containsAll(roots);

		mcpClient.rootsListChangedNotification();

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(rootsRef.get()).containsAll(roots);
		});

		// Remove a root
		mcpClient.removeRoot(roots.get(0).uri());

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(rootsRef.get()).containsAll(List.of(roots.get(1)));
		});

		// Add a new root
		var root3 = new Root("uri3://", "root3");
		mcpClient.addRoot(root3);

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(rootsRef.get()).containsAll(List.of(roots.get(1), root3));
		});

		mcpClient.close();

		mcpServer.close();
	}

}
