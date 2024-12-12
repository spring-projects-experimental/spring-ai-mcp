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
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.client.stdio.ServerParameters;
import org.springframework.ai.mcp.client.stdio.StdioServerTransport;
import org.springframework.ai.mcp.spec.McpSchema.CallToolRequest;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptRequest;
import org.springframework.ai.mcp.spec.McpSchema.Prompt;
import org.springframework.ai.mcp.spec.McpSchema.Resource;
import org.springframework.ai.mcp.spec.McpSchema.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for MCP Client Session functionality.
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */
@Timeout(15) // Giving extra time beyond the client timeout
class McpAsyncClientTests {

	private McpAsyncClient mcpAsyncClient;

	private ServerParameters stdioParams;

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private static final String TEST_MESSAGE = "Hello MCP Spring AI!";

	@BeforeEach
	void setUp() {
		stdioParams = ServerParameters.builder("npx")
			.args("-y", "@modelcontextprotocol/server-everything", "dir")
			.build();

		assertThatCode(() -> {
			mcpAsyncClient = McpClient.async(new StdioServerTransport(stdioParams), TIMEOUT, new ObjectMapper());
			mcpAsyncClient.initialize().block(Duration.ofSeconds(10));
		}).doesNotThrowAnyException();
	}

	@AfterEach
	void tearDown() {
		if (mcpAsyncClient != null) {
			assertThatCode(() -> mcpAsyncClient.closeGracefully().block(Duration.ofSeconds(10)))
				.doesNotThrowAnyException();
		}
	}

	@Test
	void testConstructorWithInvalidArguments() {
		assertThatThrownBy(() -> McpClient.sync(null, TIMEOUT, new ObjectMapper()))
			.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> McpClient.sync(new StdioServerTransport(stdioParams), null, new ObjectMapper()))
			.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> McpClient.sync(new StdioServerTransport(stdioParams), TIMEOUT, null))
			.isInstanceOf(IllegalArgumentException.class);
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
		CallToolRequest callToolRequest = new CallToolRequest("echo", Map.of("message", TEST_MESSAGE));

		StepVerifier.create(mcpAsyncClient.callTool(callToolRequest)).consumeNextWith(callToolResult -> {
			assertThat(callToolResult).isNotNull().satisfies(result -> {
				assertThat(result.content()).isNotNull();
				assertThat(result.isError()).isNull();
			});
		}).verifyComplete();
	}

	@Test
	void testCallToolWithInvalidTool() {
		CallToolRequest invalidRequest = new CallToolRequest("nonexistent_tool", Map.of("message", TEST_MESSAGE));

		assertThatThrownBy(() -> mcpAsyncClient.callTool(invalidRequest).block()).isInstanceOf(Exception.class);
	}

	@Test
	void testRootsListChanged() {
		assertThatCode(() -> mcpAsyncClient.sendRootsListChanged().block()).doesNotThrowAnyException();
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

}
