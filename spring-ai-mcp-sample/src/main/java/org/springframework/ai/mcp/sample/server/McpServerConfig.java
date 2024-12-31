package org.springframework.ai.mcp.sample.server;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mcp.server.McpAsyncServer;
import org.springframework.ai.mcp.server.McpServer;
import org.springframework.ai.mcp.server.McpServer.PromptRegistration;
import org.springframework.ai.mcp.server.McpServer.ResourceRegistration;
import org.springframework.ai.mcp.server.McpServer.ToolRegistration;
import org.springframework.ai.mcp.server.transport.SseServerTransport;
import org.springframework.ai.mcp.server.transport.StdioServerTransport;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptResult;
import org.springframework.ai.mcp.spec.McpSchema.LoggingMessageNotification;
import org.springframework.ai.mcp.spec.McpSchema.PromptMessage;
import org.springframework.ai.mcp.spec.McpSchema.Role;
import org.springframework.ai.mcp.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.spec.McpTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;

@Configuration
public class McpServerConfig {

	private static final Logger logger = LoggerFactory.getLogger(McpServerConfig.class);

	// STDIO transport
	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "stdio")
	public StdioServerTransport stdioServerTransport() {
		return new StdioServerTransport();
	}

	// SSE transport
	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "sse")
	public SseServerTransport sseServerTransport() {
		return new SseServerTransport(new ObjectMapper(), "/mcp/message");
	}

	// Router function for SSE transport used by Spring WebFlux to start an HTTP
	// server.
	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "sse")
	public RouterFunction<?> mcpRouterFunction(SseServerTransport transport) {
		return transport.getRouterFunction();
	}

	@Bean
	public McpAsyncServer mcpServer(McpTransport transport) { // @formatter:off

		// Configure server capabilities with resource support
		var capabilities = McpSchema.ServerCapabilities.builder()
			.resources(false, true) // No subscribe support, but list changes notifications
			.tools(true) // Tool support with list changes notifications
			.prompts(true) // Prompt support with list changes notifications
			.logging() // Logging support
			.build();

		// Create the server with both tool and resource capabilities
		var server = McpServer.using(transport)
			.info("MCP Demo Server", "1.0.0")
			.capabilities(capabilities)
			.resources(systemInfoResourceRegistration())
			.prompts(greetingPromptRegistration())
			.tools(calculatorToolRegistration())
			.async();
		
		server.addTool(weatherToolRegistration(server));
		return server; // @formatter:on
	} // @formatter:on

	private static ResourceRegistration systemInfoResourceRegistration() {

		// Create a resource registration for system information
		var systemInfoResource = new McpSchema.Resource( // @formatter:off
			"system://info",
			"System Information",
			"Provides basic system information including Java version, OS, etc.",
			"application/json", null
		);

		var resourceRegistration = new ResourceRegistration(systemInfoResource, (request) -> {
			try {
				var systemInfo = Map.of(
					"javaVersion", System.getProperty("java.version"),
					"osName", System.getProperty("os.name"),
					"osVersion", System.getProperty("os.version"),
					"osArch", System.getProperty("os.arch"),
					"processors", Runtime.getRuntime().availableProcessors(),
					"timestamp", System.currentTimeMillis());

				String jsonContent = new ObjectMapper().writeValueAsString(systemInfo);

				return new McpSchema.ReadResourceResult(
						List.of(new McpSchema.TextResourceContents(request.uri(), "application/json", jsonContent)));
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to generate system info", e);
			}
		}); // @formatter:on

		return resourceRegistration;
	}

	private static PromptRegistration greetingPromptRegistration() {

		var prompt = new McpSchema.Prompt("greeting", "A friendly greeting prompt",
				List.of(new McpSchema.PromptArgument("name", "The name to greet", true)));

		return new PromptRegistration(prompt, getPromptRequest -> {

			String nameArgument = (String) getPromptRequest.arguments().get("name");
			if (nameArgument == null) {
				nameArgument = "friend";
			}

			var userMessage = new PromptMessage(Role.USER,
					new TextContent("Hello " + nameArgument + "! How can I assist you today?"));

			return new GetPromptResult("A personalized greeting message", List.of(userMessage));
		});
	}

	private static ToolRegistration weatherToolRegistration(McpAsyncServer server) {
		return new ToolRegistration(
				new McpSchema.Tool("weather", "Weather forecast tool by location", Map.of("city", "String")),
				(arguments) -> {
					String city = (String) arguments.get("city");

					// Create the result
					var result = new CallToolResult(
							List.of(new TextContent("Weather forecast for " + city + " is sunny")), false);

					// Send the logging notification and ignore its completion
					server
						.loggingNotification(LoggingMessageNotification.builder()
							.data("This is a log message from the weather tool")
							.build())
						.subscribe(null, error -> {
							// Log any errors but don't fail the operation
							logger.error("Failed to send logging notification", error);
						});

					return result;
				});
	}

	private static ToolRegistration calculatorToolRegistration() {
		return new ToolRegistration(new McpSchema.Tool("calculator",
				"Performs basic arithmetic operations (add, subtract, multiply, divide)", """
						{
							"type": "object",
							"properties": {
								"operation": {
									"type": "string",
									"enum": ["add", "subtract", "multiply", "divide"],
									"description": "The arithmetic operation to perform"
								},
								"a": {
									"type": "number",
									"description": "First operand"
								},
								"b": {
									"type": "number",
									"description": "Second operand"
								}
							},
							"required": ["operation", "a", "b"]
						}
						"""), arguments -> {
					String operation = (String) arguments.get("operation");
					double a = (Double) arguments.get("a");
					double b = (Double) arguments.get("b");

					double result;
					switch (operation) {
						case "add":
							result = a + b;
							break;
						case "subtract":
							result = a - b;
							break;
						case "multiply":
							result = a * b;
							break;
						case "divide":
							if (b == 0) {
								return new McpSchema.CallToolResult(
										java.util.List.of(new McpSchema.TextContent("Division by zero")), true);
							}
							result = a / b;
							break;
						default:
							return new McpSchema.CallToolResult(
									java.util.List.of(new McpSchema.TextContent("Unknown operation: " + operation)),
									true);
					}

					return new McpSchema.CallToolResult(
							java.util.List.of(new McpSchema.TextContent(String.valueOf(result))), false);
				});
	}

}
