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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.server.McpServer.PromptRegistration;
import org.springframework.ai.mcp.server.McpServer.ResourceRegistration;
import org.springframework.ai.mcp.server.McpServer.ToolRegistration;
import org.springframework.ai.mcp.spec.McpError;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptResult;
import org.springframework.ai.mcp.spec.McpSchema.Prompt;
import org.springframework.ai.mcp.spec.McpSchema.PromptMessage;
import org.springframework.ai.mcp.spec.McpSchema.ReadResourceResult;
import org.springframework.ai.mcp.spec.McpSchema.Resource;
import org.springframework.ai.mcp.spec.McpSchema.ServerCapabilities;
import org.springframework.ai.mcp.spec.McpSchema.Tool;
import org.springframework.ai.mcp.spec.McpTransport;
import org.springframework.ai.mcp.spec.ServerMcpTransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test suite for the {@link McpAsyncServer} that can be used with different
 * {@link McpTransport} implementations.
 *
 * @author Christian Tzolov
 */
public abstract class BlaAbstractMcpAsyncServerTests {

	private static final String TEST_TOOL_NAME = "test-tool";

	private static final String TEST_RESOURCE_URI = "test://resource";

	private static final String TEST_PROMPT_NAME = "test-prompt";

	abstract protected ServerMcpTransport createMcpTransport();

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

	// ---------------------------------------
	// Server Lifecycle Tests
	// ---------------------------------------

	@Test
	void testConstructorWithInvalidArguments() {
		assertThatThrownBy(() -> McpServer.using(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Transport must not be null");

		assertThatThrownBy(() -> McpServer.using(createMcpTransport()).serverInfo((McpSchema.Implementation) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Server info must not be null");
	}

	@Test
	void testGracefulShutdown() {
		var mcpAsyncServer = McpServer.using(createMcpTransport()).serverInfo("test-server", "1.0.0").async();

		StepVerifier.create(mcpAsyncServer.closeGracefully()).verifyComplete();
	}

	@Test
	void testImmediateClose() {
		var mcpAsyncServer = McpServer.using(createMcpTransport()).serverInfo("test-server", "1.0.0").async();

		assertThatCode(() -> mcpAsyncServer.close()).doesNotThrowAnyException();
	}

	// ---------------------------------------
	// Tools Tests
	// ---------------------------------------
	String emptyJsonSchema = """
			{
				"$schema": "http://json-schema.org/draft-07/schema#",
				"type": "object",
				"properties": {}
			}
			""";

	@Test
	void testAddTool() {
		Tool newTool = new McpSchema.Tool("new-tool", "New test tool", emptyJsonSchema);
		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().tools(true).build())
			.async();

		StepVerifier
			.create(mcpAsyncServer.addTool(new ToolRegistration(newTool, args -> new CallToolResult(List.of(), false))))
			.verifyComplete();

		assertThatCode(() -> mcpAsyncServer.closeGracefully().block(Duration.ofSeconds(10)));
	}

	@Test
	void testAddDuplicateTool() {
		Tool duplicateTool = new McpSchema.Tool(TEST_TOOL_NAME, "Duplicate tool", emptyJsonSchema);

		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().tools(true).build())
			.tool(duplicateTool, args -> new CallToolResult(List.of(), false))
			.async();

		StepVerifier
			.create(mcpAsyncServer
				.addTool(new ToolRegistration(duplicateTool, args -> new CallToolResult(List.of(), false))))
			.verifyErrorSatisfies(error -> {
				assertThat(error).isInstanceOf(McpError.class)
					.hasMessage("Tool with name '" + TEST_TOOL_NAME + "' already exists");
			});

		assertThatCode(() -> mcpAsyncServer.closeGracefully().block(Duration.ofSeconds(10)));
	}

	@Test
	void testRemoveTool() {
		Tool too = new McpSchema.Tool(TEST_TOOL_NAME, "Duplicate tool", emptyJsonSchema);

		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().tools(true).build())
			.tool(too, args -> new CallToolResult(List.of(), false))
			.async();

		StepVerifier.create(mcpAsyncServer.removeTool(TEST_TOOL_NAME)).verifyComplete();

		assertThatCode(() -> mcpAsyncServer.closeGracefully().block(Duration.ofSeconds(10)));
	}

	@Test
	void testRemoveNonexistentTool() {
		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().tools(true).build())
			.async();

		StepVerifier.create(mcpAsyncServer.removeTool("nonexistent-tool")).verifyErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(McpError.class).hasMessage("Tool with name 'nonexistent-tool' not found");
		});

		assertThatCode(() -> mcpAsyncServer.closeGracefully().block(Duration.ofSeconds(10)));
	}

	@Test
	void testNotifyToolsListChanged() {
		Tool too = new McpSchema.Tool(TEST_TOOL_NAME, "Duplicate tool", emptyJsonSchema);

		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().tools(true).build())
			.tool(too, args -> new CallToolResult(List.of(), false))
			.async();

		StepVerifier.create(mcpAsyncServer.notifyToolsListChanged()).verifyComplete();

		assertThatCode(() -> mcpAsyncServer.closeGracefully().block(Duration.ofSeconds(10)));
	}

	// ---------------------------------------
	// Resources Tests
	// ---------------------------------------

	@Test
	void testNotifyResourcesListChanged() {
		var mcpAsyncServer = McpServer.using(createMcpTransport()).serverInfo("test-server", "1.0.0").async();

		StepVerifier.create(mcpAsyncServer.notifyResourcesListChanged()).verifyComplete();

		assertThatCode(() -> mcpAsyncServer.closeGracefully().block(Duration.ofSeconds(10)));
	}

	@Test
	void testAddResource() {
		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().resources(true, false).build())
			.async();

		Resource resource = new Resource(TEST_RESOURCE_URI, "Test Resource", "text/plain", "Test resource description",
				null);
		ResourceRegistration registration = new ResourceRegistration(resource,
				req -> new ReadResourceResult(List.of()));

		StepVerifier.create(mcpAsyncServer.addResource(registration)).verifyComplete();

		assertThatCode(() -> mcpAsyncServer.closeGracefully().block(Duration.ofSeconds(10))).doesNotThrowAnyException();
	}

	@Test
	void testAddResourceWithNullRegistration() {
		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().resources(true, false).build())
			.async();

		StepVerifier.create(mcpAsyncServer.addResource(null)).verifyErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(McpError.class).hasMessage("Resource must not be null");
		});

		assertThatCode(() -> mcpAsyncServer.closeGracefully().block(Duration.ofSeconds(10))).doesNotThrowAnyException();
	}

	@Test
	void testAddResourceWithoutCapability() {
		// Create a server without resource capabilities
		McpAsyncServer serverWithoutResources = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.async();

		Resource resource = new Resource(TEST_RESOURCE_URI, "Test Resource", "text/plain", "Test resource description",
				null);
		ResourceRegistration registration = new ResourceRegistration(resource,
				req -> new ReadResourceResult(List.of()));

		StepVerifier.create(serverWithoutResources.addResource(registration)).verifyErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(McpError.class)
				.hasMessage("Server must be configured with resource capabilities");
		});
	}

	@Test
	void testRemoveResourceWithoutCapability() {
		// Create a server without resource capabilities
		McpAsyncServer serverWithoutResources = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.async();

		StepVerifier.create(serverWithoutResources.removeResource(TEST_RESOURCE_URI)).verifyErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(McpError.class)
				.hasMessage("Server must be configured with resource capabilities");
		});
	}

	// ---------------------------------------
	// Prompts Tests
	// ---------------------------------------

	@Test
	void testNotifyPromptsListChanged() {
		var mcpAsyncServer = McpServer.using(createMcpTransport()).serverInfo("test-server", "1.0.0").async();

		StepVerifier.create(mcpAsyncServer.notifyPromptsListChanged()).verifyComplete();

		assertThatCode(() -> mcpAsyncServer.closeGracefully().block(Duration.ofSeconds(10)));
	}

	@Test
	void testAddPromptWithNullRegistration() {
		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().prompts(false).build())
			.async();

		StepVerifier.create(mcpAsyncServer.addPrompt(null)).verifyErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(McpError.class).hasMessage("Prompt registration must not be null");
		});
	}

	@Test
	void testAddPromptWithoutCapability() {
		// Create a server without prompt capabilities
		McpAsyncServer serverWithoutPrompts = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.async();

		Prompt prompt = new Prompt(TEST_PROMPT_NAME, "Test Prompt", List.of());
		PromptRegistration registration = new PromptRegistration(prompt, req -> new GetPromptResult(
				"Test prompt description",
				List.of(new PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent("Test content")))));

		StepVerifier.create(serverWithoutPrompts.addPrompt(registration)).verifyErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(McpError.class)
				.hasMessage("Server must be configured with prompt capabilities");
		});
	}

	@Test
	void testRemovePromptWithoutCapability() {
		// Create a server without prompt capabilities
		McpAsyncServer serverWithoutPrompts = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.async();

		StepVerifier.create(serverWithoutPrompts.removePrompt(TEST_PROMPT_NAME)).verifyErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(McpError.class)
				.hasMessage("Server must be configured with prompt capabilities");
		});
	}

	@Test
	void testRemovePrompt() {
		String TEST_PROMPT_NAME_TO_REMOVE = "TEST_PROMPT_NAME678";

		Prompt prompt = new Prompt(TEST_PROMPT_NAME_TO_REMOVE, "Test Prompt", List.of());
		PromptRegistration registration = new PromptRegistration(prompt, req -> new GetPromptResult(
				"Test prompt description",
				List.of(new PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent("Test content")))));

		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().prompts(true).build())
			.prompts(registration)
			.async();

		StepVerifier.create(mcpAsyncServer.removePrompt(TEST_PROMPT_NAME_TO_REMOVE)).verifyComplete();

		assertThatCode(() -> mcpAsyncServer.closeGracefully().block(Duration.ofSeconds(10))).doesNotThrowAnyException();
	}

	@Test
	void testRemoveNonexistentPrompt() {
		var mcpAsyncServer2 = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().prompts(true).build())
			.async();

		StepVerifier.create(mcpAsyncServer2.removePrompt("nonexistent-prompt")).verifyErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(McpError.class)
				.hasMessage("Prompt with name 'nonexistent-prompt' not found");
		});

		assertThatCode(() -> mcpAsyncServer2.closeGracefully().block(Duration.ofSeconds(10)))
			.doesNotThrowAnyException();
	}

	// ---------------------------------------
	// Roots Tests
	// ---------------------------------------

	@Test
	void testRootsChangeConsumers() {
		// Test with single consumer
		var rootsReceived = new McpSchema.Root[1];
		var consumerCalled = new boolean[1];

		var singleConsumerServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.rootsChangeConsumers(List.of(roots -> {
				consumerCalled[0] = true;
				if (!roots.isEmpty()) {
					rootsReceived[0] = roots.get(0);
				}
			}))
			.async();

		assertThat(singleConsumerServer).isNotNull();
		assertThatCode(() -> singleConsumerServer.closeGracefully().block(Duration.ofSeconds(10)))
			.doesNotThrowAnyException();
		onClose();

		// Test with multiple consumers
		var consumer1Called = new boolean[1];
		var consumer2Called = new boolean[1];
		var rootsContent = new List[1];

		var multipleConsumersServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.rootsChangeConsumers(List.of(roots -> {
				consumer1Called[0] = true;
				rootsContent[0] = roots;
			}, roots -> consumer2Called[0] = true))
			.async();

		assertThat(multipleConsumersServer).isNotNull();
		assertThatCode(() -> multipleConsumersServer.closeGracefully().block(Duration.ofSeconds(10)))
			.doesNotThrowAnyException();
		onClose();

		// Test error handling
		var errorHandlingServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.rootsChangeConsumers(List.of(roots -> {
				throw new RuntimeException("Test error");
			}))
			.async();

		assertThat(errorHandlingServer).isNotNull();
		assertThatCode(() -> errorHandlingServer.closeGracefully().block(Duration.ofSeconds(10)))
			.doesNotThrowAnyException();
		onClose();

		// Test without consumers
		var noConsumersServer = McpServer.using(createMcpTransport()).serverInfo("test-server", "1.0.0").async();

		assertThat(noConsumersServer).isNotNull();
		assertThatCode(() -> noConsumersServer.closeGracefully().block(Duration.ofSeconds(10)))
			.doesNotThrowAnyException();
	}

	// ---------------------------------------
	// Logging Tests
	// ---------------------------------------

	@Test
	void testLoggingLevels() {
		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().logging().build())
			.async();

		// Test all logging levels
		for (McpSchema.LoggingLevel level : McpSchema.LoggingLevel.values()) {
			var notification = McpSchema.LoggingMessageNotification.builder()
				.level(level)
				.logger("test-logger")
				.data("Test message with level " + level)
				.build();

			StepVerifier.create(mcpAsyncServer.loggingNotification(notification)).verifyComplete();
		}
	}

	@Test
	void testLoggingWithoutCapability() {
		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().build()) // No logging capability
			.async();

		var notification = McpSchema.LoggingMessageNotification.builder()
			.level(McpSchema.LoggingLevel.INFO)
			.logger("test-logger")
			.data("Test log message")
			.build();

		StepVerifier.create(mcpAsyncServer.loggingNotification(notification)).verifyComplete();
	}

	@Test
	void testLoggingWithNullNotification() {
		var mcpAsyncServer = McpServer.using(createMcpTransport())
			.serverInfo("test-server", "1.0.0")
			.capabilities(ServerCapabilities.builder().logging().build())
			.async();

		StepVerifier.create(mcpAsyncServer.loggingNotification(null)).verifyError(McpError.class);
	}

}
