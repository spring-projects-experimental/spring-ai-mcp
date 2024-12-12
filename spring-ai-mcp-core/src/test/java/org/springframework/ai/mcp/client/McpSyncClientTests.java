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

import org.springframework.ai.mcp.client.stdio.ServerParameters;
import org.springframework.ai.mcp.client.stdio.StdioServerTransport;
import org.springframework.ai.mcp.spec.McpSchema.CallToolRequest;
import org.springframework.ai.mcp.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.spec.McpSchema.ListResourcesResult;
import org.springframework.ai.mcp.spec.McpSchema.ListToolsResult;
import org.springframework.ai.mcp.spec.McpSchema.Resource;
import org.springframework.ai.mcp.spec.McpSchema.TextContent;
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
class McpSyncClientTests {

	private McpSyncClient mcpSyncClient;

	private ServerParameters stdioParams;

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private static final String TEST_MESSAGE = "Hello MCP Spring AI!";

	@BeforeEach
	void setUp() {
		stdioParams = ServerParameters.builder("npx")
			.args("-y", "@modelcontextprotocol/server-everything", "dir")
			.build();

		assertThatCode(() -> {
			mcpSyncClient = McpClient.sync(new StdioServerTransport(stdioParams), TIMEOUT, new ObjectMapper());
			mcpSyncClient.initialize();
		}).doesNotThrowAnyException();
	}

	@AfterEach
	void tearDown() {
		if (mcpSyncClient != null) {
			assertThatCode(() -> mcpSyncClient.close()).doesNotThrowAnyException();
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

			System.out.println(content);
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
