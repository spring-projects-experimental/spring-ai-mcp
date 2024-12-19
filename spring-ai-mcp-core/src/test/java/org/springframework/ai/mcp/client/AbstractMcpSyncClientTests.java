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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.spec.McpSchema.CallToolRequest;
import org.springframework.ai.mcp.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.spec.McpSchema.ListResourcesResult;
import org.springframework.ai.mcp.spec.McpSchema.ListToolsResult;
import org.springframework.ai.mcp.spec.McpSchema.Resource;
import org.springframework.ai.mcp.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.spec.McpSchema.Tool;
import org.springframework.ai.mcp.spec.McpTransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for MCP Client Session functionality.
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */
public abstract class AbstractMcpSyncClientTests {

	private McpSyncClient mcpSyncClient;

	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	private static final String TEST_MESSAGE = "Hello MCP Spring AI!";

	protected McpTransport mcpTransport;

	abstract protected McpTransport createMcpTransport();

	abstract protected void onStart();

	abstract protected void onClose();

	@BeforeEach
	void setUp() {
		onStart();
		this.mcpTransport = createMcpTransport();

		assertThatCode(() -> {
			mcpSyncClient = McpClient.using(mcpTransport).requestTimeout(TIMEOUT).sync();
			mcpSyncClient.initialize();
		}).doesNotThrowAnyException();
	}

	@AfterEach
	void tearDown() {
		if (mcpSyncClient != null) {
			assertThatCode(() -> mcpSyncClient.close()).doesNotThrowAnyException();
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
		ListToolsResult tools = mcpSyncClient.listTools(null);

		assertThat(tools).isNotNull().satisfies(result -> {
			assertThat(result.tools()).isNotNull().isNotEmpty();

			Tool firstTool = result.tools().get(0);
			assertThat(firstTool.name()).isNotNull();
			assertThat(firstTool.description()).isNotNull();
		});
	}

	@Test
	void testCallTools() {
		CallToolResult toolResult = mcpSyncClient.callTool(new CallToolRequest("add", Map.of("a", 3, "b", 4)));

		assertThat(toolResult).isNotNull().satisfies(result -> {

			assertThat(result.content()).hasSize(1);

			TextContent content = (TextContent) result.content().get(0);

			assertThat(content).isNotNull();
			assertThat(content.text()).isNotNull();
			assertThat(content.text()).contains("7");
		});
	}

	@Test
	void testPing() {
		assertThatCode(() -> mcpSyncClient.ping()).doesNotThrowAnyException();
	}

	@Test
	void testCallTool() {
		CallToolRequest callToolRequest = new CallToolRequest("echo", Map.of("message", TEST_MESSAGE));

		CallToolResult callToolResult = mcpSyncClient.callTool(callToolRequest);

		assertThat(callToolResult).isNotNull().satisfies(result -> {
			assertThat(result.content()).isNotNull();
			assertThat(result.isError()).isNull();
		});
	}

	@Test
	void testCallToolWithInvalidTool() {
		CallToolRequest invalidRequest = new CallToolRequest("nonexistent_tool", Map.of("message", TEST_MESSAGE));

		assertThatThrownBy(() -> mcpSyncClient.callTool(invalidRequest)).isInstanceOf(Exception.class);
	}

	@Test
	void testRootsListChanged() {
		assertThatCode(() -> mcpSyncClient.sendRootsListChanged()).doesNotThrowAnyException();
	}

	@Test
	void testListResources() {
		ListResourcesResult resources = mcpSyncClient.listResources(null);

		assertThat(resources).isNotNull().satisfies(result -> {
			assertThat(result.resources()).isNotNull();

			if (!result.resources().isEmpty()) {
				Resource firstResource = result.resources().get(0);
				assertThat(firstResource.uri()).isNotNull();
				assertThat(firstResource.name()).isNotNull();
			}
		});
	}

	@Test
	void testClientSessionState() {
		assertThat(mcpSyncClient).isNotNull();
	}

}
