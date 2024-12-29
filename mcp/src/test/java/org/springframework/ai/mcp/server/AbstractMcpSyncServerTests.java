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

package org.springframework.ai.mcp.server;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.server.McpServer.PromptRegistration;
import org.springframework.ai.mcp.server.McpServer.ResourceRegistration;
import org.springframework.ai.mcp.server.McpServer.ToolRegistration;
import org.springframework.ai.mcp.spec.McpError;
import org.springframework.ai.mcp.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptResult;
import org.springframework.ai.mcp.spec.McpSchema.Prompt;
import org.springframework.ai.mcp.spec.McpSchema.PromptMessage;
import org.springframework.ai.mcp.spec.McpSchema.ReadResourceResult;
import org.springframework.ai.mcp.spec.McpSchema.Resource;
import org.springframework.ai.mcp.spec.McpSchema.ServerCapabilities;
import org.springframework.ai.mcp.spec.McpSchema.Tool;
import org.springframework.ai.mcp.spec.McpTransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test suite for the {@link McpSyncServer} that can be used with different
 * {@link McpTransport} implementations.
 *
 * @author Christian Tzolov
 */
public abstract class AbstractMcpSyncServerTests {

	private static final String TEST_TOOL_NAME = "test-tool";

	private static final String TEST_RESOURCE_URI = "test://resource";

	private static final String TEST_PROMPT_NAME = "test-prompt";

	abstract protected McpTransport createMcpTransport();

	protected void onStart() {
	}

	protected void onClose() {
	}

	@BeforeEach
	void setUp() {
	}

	@AfterEach
	void tearDown() {
		onClose();
	}

	@Test
	void testConstructorWithInvalidArguments() {
		assertThatThrownBy(() -> McpServer.using(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Transport must not be null");

		assertThatThrownBy(() -> McpServer.using(createMcpTransport()).info((McpSchema.Implementation) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Server info must not be null");
	}

	@Test
	void testAddTool() {
		var mcpSyncServer = McpServer.using(createMcpTransport())
			.info("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().tools(true).build())
			.sync();

		Tool newTool = new McpSchema.Tool("new-tool", "New test tool", Map.of("input", "string"));
		assertThatCode(() -> mcpSyncServer
			.addTool(new ToolRegistration(newTool, args -> new CallToolResult(List.of(), false))))
			.doesNotThrowAnyException();

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testAddDuplicateTool() {
		Tool duplicateTool = new McpSchema.Tool(TEST_TOOL_NAME, "Duplicate tool", Map.of("input", "string"));

		var mcpSyncServer = McpServer.using(createMcpTransport())
			.info("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().tools(true).build())
			.tool(duplicateTool, args -> new CallToolResult(List.of(), false))
			.sync();

		assertThatThrownBy(() -> mcpSyncServer
			.addTool(new ToolRegistration(duplicateTool, args -> new CallToolResult(List.of(), false))))
			.isInstanceOf(McpError.class)
			.hasMessage("Tool with name '" + TEST_TOOL_NAME + "' already exists");

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testRemoveTool() {
		Tool tool = new McpSchema.Tool(TEST_TOOL_NAME, "Test tool", Map.of("input", "string"));

		var mcpSyncServer = McpServer.using(createMcpTransport())
			.info("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().tools(true).build())
			.tool(tool, args -> new CallToolResult(List.of(), false))
			.sync();

		assertThatCode(() -> mcpSyncServer.removeTool(TEST_TOOL_NAME)).doesNotThrowAnyException();

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testRemoveNonexistentTool() {
		var mcpSyncServer = McpServer.using(createMcpTransport())
			.info("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().tools(true).build())
			.sync();

		assertThatThrownBy(() -> mcpSyncServer.removeTool("nonexistent-tool")).isInstanceOf(McpError.class)
			.hasMessage("Tool with name 'nonexistent-tool' not found");

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testNotifyToolsListChanged() {
		var mcpSyncServer = McpServer.using(createMcpTransport()).info("test-server", "1.0.0").sync();

		assertThatCode(() -> mcpSyncServer.notifyToolsListChanged()).doesNotThrowAnyException();

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testNotifyResourcesListChanged() {
		var mcpSyncServer = McpServer.using(createMcpTransport()).info("test-server", "1.0.0").sync();

		assertThatCode(() -> mcpSyncServer.notifyResourcesListChanged()).doesNotThrowAnyException();

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testNotifyPromptsListChanged() {
		var mcpSyncServer = McpServer.using(createMcpTransport()).info("test-server", "1.0.0").sync();

		assertThatCode(() -> mcpSyncServer.notifyPromptsListChanged()).doesNotThrowAnyException();

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testGracefulShutdown() {
		var mcpSyncServer = McpServer.using(createMcpTransport()).info("test-server", "1.0.0").sync();

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testImmediateClose() {
		var mcpSyncServer = McpServer.using(createMcpTransport()).info("test-server", "1.0.0").sync();

		assertThatCode(() -> mcpSyncServer.close()).doesNotThrowAnyException();
	}

	@Test
	void testAddResource() {
		var mcpSyncServer = McpServer.using(createMcpTransport())
			.info("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().resources(true, false).build())
			.sync();

		Resource resource = new Resource(TEST_RESOURCE_URI, "Test Resource", "text/plain", "Test resource description",
				null);
		ResourceRegistration registration = new ResourceRegistration(resource,
				req -> new ReadResourceResult(List.of()));

		assertThatCode(() -> mcpSyncServer.addResource(registration)).doesNotThrowAnyException();

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testAddResourceWithNullRegistration() {
		var mcpSyncServer = McpServer.using(createMcpTransport())
			.info("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().resources(true, false).build())
			.sync();

		assertThatThrownBy(() -> mcpSyncServer.addResource(null)).isInstanceOf(McpError.class)
			.hasMessage("Resource must not be null");

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testAddPromptWithNullRegistration() {
		var mcpSyncServer = McpServer.using(createMcpTransport())
			.info("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().prompts(false).build())
			.sync();

		assertThatThrownBy(() -> mcpSyncServer.addPrompt(null)).isInstanceOf(McpError.class)
			.hasMessage("Prompt registration must not be null");
	}

	@Test
	void testAddResourceWithoutCapability() {
		var serverWithoutResources = McpServer.using(createMcpTransport()).info("test-server", "1.0.0").sync();

		Resource resource = new Resource(TEST_RESOURCE_URI, "Test Resource", "text/plain", "Test resource description",
				null);
		ResourceRegistration registration = new ResourceRegistration(resource,
				req -> new ReadResourceResult(List.of()));

		assertThatThrownBy(() -> serverWithoutResources.addResource(registration)).isInstanceOf(McpError.class)
			.hasMessage("Server must be configured with resource capabilities");
	}

	@Test
	void testAddPromptWithoutCapability() {
		var serverWithoutPrompts = McpServer.using(createMcpTransport()).info("test-server", "1.0.0").sync();

		Prompt prompt = new Prompt(TEST_PROMPT_NAME, "Test Prompt", List.of());
		PromptRegistration registration = new PromptRegistration(prompt, req -> new GetPromptResult(
				"Test prompt description",
				List.of(new PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent("Test content")))));

		assertThatThrownBy(() -> serverWithoutPrompts.addPrompt(registration)).isInstanceOf(McpError.class)
			.hasMessage("Server must be configured with prompt capabilities");
	}

	@Test
	void testRemoveResourceWithoutCapability() {
		var serverWithoutResources = McpServer.using(createMcpTransport()).info("test-server", "1.0.0").sync();

		assertThatThrownBy(() -> serverWithoutResources.removeResource(TEST_RESOURCE_URI)).isInstanceOf(McpError.class)
			.hasMessage("Server must be configured with resource capabilities");
	}

	@Test
	void testRemovePromptWithoutCapability() {
		var serverWithoutPrompts = McpServer.using(createMcpTransport()).info("test-server", "1.0.0").sync();

		assertThatThrownBy(() -> serverWithoutPrompts.removePrompt(TEST_PROMPT_NAME)).isInstanceOf(McpError.class)
			.hasMessage("Server must be configured with prompt capabilities");
	}

	@Test
	void testRemovePrompt() {

		Prompt prompt = new Prompt(TEST_PROMPT_NAME, "Test Prompt", List.of());
		PromptRegistration registration = new PromptRegistration(prompt, req -> new GetPromptResult(
				"Test prompt description",
				List.of(new PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent("Test content")))));

		var mcpSyncServer = McpServer.using(createMcpTransport())
			.info("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().prompts(true).build())
			.prompts(registration)
			.sync();
		// assertThatCode(() ->
		// mcpSyncServer.addPrompt(registration)).doesNotThrowAnyException();

		assertThatCode(() -> mcpSyncServer.removePrompt(TEST_PROMPT_NAME)).doesNotThrowAnyException();

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testRemoveNonexistentPrompt() {
		var mcpSyncServer = McpServer.using(createMcpTransport())
			.info("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().prompts(true).build())
			.sync();

		assertThatThrownBy(() -> mcpSyncServer.removePrompt("nonexistent-prompt")).isInstanceOf(McpError.class)
			.hasMessage("Prompt with name 'nonexistent-prompt' not found");

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

	@Test
	void testGetAsyncServer() {
		var mcpSyncServer = McpServer.using(createMcpTransport()).info("test-server", "1.0.0").sync();

		assertThat(mcpSyncServer.getAsyncServer()).isNotNull();

		assertThatCode(() -> mcpSyncServer.closeGracefully()).doesNotThrowAnyException();
	}

}