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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.spec.McpSchema.CallToolRequest;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptRequest;
import org.springframework.ai.mcp.spec.McpSchema.Prompt;
import org.springframework.ai.mcp.spec.McpSchema.Resource;
import org.springframework.ai.mcp.spec.McpSchema.Root;
import org.springframework.ai.mcp.spec.McpSchema.Tool;
import org.springframework.ai.mcp.spec.McpTransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test suite for the {@link McpAsyncClient} that can be used with different
 * {@link McpTransport} implementations.
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */
public abstract class AbstractMcpAsyncClientTests {

	private McpAsyncClient mcpAsyncClient;

	protected McpTransport mcpTransport;

	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	private static final String ECHO_TEST_MESSAGE = "Hello MCP Spring AI!";

	abstract protected McpTransport createMcpTransport();

	abstract protected void onStart();

	abstract protected void onClose();

	@BeforeEach
	void setUp() {
		onStart();
		this.mcpTransport = createMcpTransport();

		assertThatCode(() -> {
			mcpAsyncClient = McpClient.using(mcpTransport).requestTimeout(TIMEOUT).async();
			mcpAsyncClient.initialize().block(Duration.ofSeconds(10));
		}).doesNotThrowAnyException();
	}

	@AfterEach
	void tearDown() {
		if (mcpAsyncClient != null) {
			assertThatCode(() -> mcpAsyncClient.closeGracefully().block(Duration.ofSeconds(10)))
				.doesNotThrowAnyException();
		}
		onClose();
	}

	@Test
	void testConstructorWithInvalidArguments() {
		assertThatThrownBy(() -> McpClient.using(null).sync()).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Transport must not be null");

		assertThatThrownBy(() -> McpClient.using(mcpTransport).requestTimeout(null).sync())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Request timeout must not be null");
	}

	@Test
	void testListTools() {
		StepVerifier.create(mcpAsyncClient.listTools(null)).consumeNextWith(result -> {
			assertThat(result.tools()).isNotNull().isNotEmpty();

			Tool firstTool = result.tools().get(0);
			assertThat(firstTool.name()).isNotNull();
			assertThat(firstTool.description()).isNotNull();
		}).verifyComplete();
	}

	@Test
	void testPing() {
		assertThatCode(() -> mcpAsyncClient.ping().block()).doesNotThrowAnyException();
	}

	@Test
	void testCallTool() {
		CallToolRequest callToolRequest = new CallToolRequest("echo", Map.of("message", ECHO_TEST_MESSAGE));

		StepVerifier.create(mcpAsyncClient.callTool(callToolRequest)).consumeNextWith(callToolResult -> {
			assertThat(callToolResult).isNotNull().satisfies(result -> {
				assertThat(result.content()).isNotNull();
				assertThat(result.isError()).isNull();
			});
		}).verifyComplete();
	}

	@Test
	void testCallToolWithInvalidTool() {
		CallToolRequest invalidRequest = new CallToolRequest("nonexistent_tool", Map.of("message", ECHO_TEST_MESSAGE));

		assertThatThrownBy(() -> mcpAsyncClient.callTool(invalidRequest).block()).isInstanceOf(Exception.class);
	}

	@Test
	void testListResources() {
		StepVerifier.create(mcpAsyncClient.listResources(null)).consumeNextWith(resources -> {
			assertThat(resources).isNotNull().satisfies(result -> {
				assertThat(result.resources()).isNotNull();

				if (!result.resources().isEmpty()) {
					Resource firstResource = result.resources().get(0);
					assertThat(firstResource.uri()).isNotNull();
					assertThat(firstResource.name()).isNotNull();
				}
			});
		}).verifyComplete();
	}

	@Test
	void testMcpAsyncClientState() {
		assertThat(mcpAsyncClient).isNotNull();
	}

	@Test
	void testListPrompts() {
		StepVerifier.create(mcpAsyncClient.listPrompts(null)).consumeNextWith(prompts -> {
			assertThat(prompts).isNotNull().satisfies(result -> {
				assertThat(result.prompts()).isNotNull();

				if (!result.prompts().isEmpty()) {
					Prompt firstPrompt = result.prompts().get(0);
					assertThat(firstPrompt.name()).isNotNull();
					assertThat(firstPrompt.description()).isNotNull();
				}
			});
		}).verifyComplete();
	}

	@Test
	void testGetPrompt() {
		StepVerifier.create(mcpAsyncClient.getPrompt(new GetPromptRequest("simple_prompt", Map.of())))
			.consumeNextWith(prompt -> {
				assertThat(prompt).isNotNull().satisfies(result -> {
					assertThat(result.messages()).isNotEmpty();
					assertThat(result.messages()).hasSize(1);
				});
			})
			.verifyComplete();
	}

	@Test
	void testRootsListChanged() {
		assertThatCode(() -> mcpAsyncClient.sendRootsListChanged().block()).doesNotThrowAnyException();
	}

	@Test
	void testInitializeWithRootsListProviders() {
		var transport = createMcpTransport();
		List<Supplier<List<Root>>> providers = List.of(() -> List.of(new Root("file:///test/path", "test-root")));

		var client = new McpAsyncClient(transport, TIMEOUT, providers, true, List.of());

		assertThatCode(() -> client.initialize().block(Duration.ofSeconds(10))).doesNotThrowAnyException();

		assertThatCode(() -> client.closeGracefully().block(Duration.ofSeconds(10))).doesNotThrowAnyException();
	}

}
