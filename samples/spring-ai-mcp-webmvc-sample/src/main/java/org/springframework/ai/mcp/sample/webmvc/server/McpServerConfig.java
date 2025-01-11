package org.springframework.ai.mcp.sample.webmvc.server;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mcp.server.McpAsyncServer;
import org.springframework.ai.mcp.server.McpServer;
import org.springframework.ai.mcp.server.McpServer.PromptRegistration;
import org.springframework.ai.mcp.server.McpServer.ResourceRegistration;
import org.springframework.ai.mcp.server.McpServer.ToolRegistration;
import org.springframework.ai.mcp.server.transport.StdioServerTransport;
import org.springframework.ai.mcp.server.transport.WebMvcSseServerTransport;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptResult;
import org.springframework.ai.mcp.spec.McpSchema.LoggingMessageNotification;
import org.springframework.ai.mcp.spec.McpSchema.PromptMessage;
import org.springframework.ai.mcp.spec.McpSchema.Role;
import org.springframework.ai.mcp.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.spec.ServerMcpTransport;
import org.springframework.ai.mcp.spring.ToolHelper;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
@EnableWebMvc
public class McpServerConfig implements WebMvcConfigurer {

	private static final Logger logger = LoggerFactory.getLogger(McpServerConfig.class);

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "sse")
	public WebMvcSseServerTransport webMvcSseServerTransport() {
		return new WebMvcSseServerTransport(new ObjectMapper(), "/mcp/message");
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "sse")
	public RouterFunction<ServerResponse> routerFunction(WebMvcSseServerTransport transport) {
		return transport.getRouterFunction();
	}

	// STDIO transport
	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "stdio")
	public StdioServerTransport stdioServerTransport() {
		return new StdioServerTransport();
	}

	@Bean
	public McpAsyncServer mcpServer(ServerMcpTransport transport, OpenLibrary openLibrary) { // @formatter:off

		// Configure server capabilities with resource support
		var capabilities = McpSchema.ServerCapabilities.builder()
			.resources(false, true) // No subscribe support, but list changes notifications
			.tools(true) // Tool support with list changes notifications
			.prompts(true) // Prompt support with list changes notifications
			.logging() // Logging support
			.build();

		// Create the server with both tool and resource capabilities
		var server = McpServer.using(transport)
			.serverInfo("MCP Demo Server", "1.0.0")
			.capabilities(capabilities)
			.resources(systemInfoResourceRegistration())
			.prompts(greetingPromptRegistration())
			.tools(calculatorToolRegistration(),
				ToolHelper.toToolRegistration(
					FunctionCallback.builder()
						.method("paymentTransactionStatus",String.class, String.class)
						.description("Get transaction payment status")
						.targetClass(McpServerConfig.class)
					.build()),
				ToolHelper.toToolRegistration(
					FunctionCallback.builder()
					.function("toUpperCase", new Function<String, String>() {
						@Override
						public String apply(String s) {
							return s.toUpperCase();
						}
					})
					.description("To upper case")
					.inputType(String.class)						
					.build()))
			.tools(openLibraryToolRegistrations(openLibrary))
			.async();
		
		server.addTool(weatherToolRegistration(server));
		return server; // @formatter:on
	} // @formatter:on

	public static List<ToolRegistration> openLibraryToolRegistrations(OpenLibrary openLibrary) {

		var books = FunctionCallback.builder()
			.method("getBooks", String.class)
			.description("Get list of Books by title")
			.targetObject(openLibrary)
			.build();

		var bookTitlesByAuthor = FunctionCallback.builder()
			.method("getBookTitlesByAuthor", String.class)
			.description("Get book titles by author")
			.targetObject(openLibrary)
			.build();

		return ToolHelper.toToolRegistration(books, bookTitlesByAuthor);
	}

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
		String emptyJsonSchema = """
				{
					"$schema": "http://json-schema.org/draft-07/schema#",
					"type": "object",
					"properties": {"city" : "string"}
				}
				""";
		return new ToolRegistration(new McpSchema.Tool("weather", "Weather forecast tool by location", emptyJsonSchema),
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

	public static String paymentTransactionStatus(String transactionId, String accountName) {
		return "The status for " + transactionId + ", by " + accountName + " is PENDING";
	}

	public Function<String, String> toUpperCase() {
		return String::toUpperCase;
	}

	@Bean
	public OpenLibrary openLibrary() {
		return new OpenLibrary(RestClient.builder());
	}

}
