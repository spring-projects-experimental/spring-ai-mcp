# Spring AI MCP Sample Project

This sample project demonstrates the usage of the Spring AI Model Context Protocol (MCP) implementation. It showcases how to create and use MCP servers and clients with different transport modes and capabilities.

## Overview

The sample provides:
- Two transport mode implementations: Stdio and SSE (Server-Sent Events)
- Server capabilities:
  - Tools support with list changes notifications
  - Resources support with list changes notifications (no subscribe support)
  - Prompts support with list changes notifications
- Sample implementations:
  - Two MCP tools: Weather and Calculator
  - One MCP resource: System Information
  - One MCP prompt: Greeting

## Building the Project

```bash
./mvnw clean package
```

## Running the Server

The server can be started in two transport modes, controlled by the `transport.mode` property:

### Stdio Mode (Default)

```bash
java -Dtransport.mode=stdio -jar target/spring-ai-mcp-sample-0.5.0-SNAPSHOT.jar
```

The Stdio mode server is automatically started by the client - no explicit server startup is needed.
But you have to build the server jar first: `./mvnw clean install -DskipTests`.

In Stdio mode the server must not emit any messages/logs to the console (e.g. standard out) but the JSON messages produced by the server.

### SSE Mode
```bash
java -Dtransport.mode=sse -jar target/spring-ai-mcp-sample-0.5.0-SNAPSHOT.jar
```

## Sample Clients

The project includes example clients for both transport modes:

### Stdio Client (ClientStdio.java)
```java
var stdioParams = ServerParameters.builder("java")
    .args("-Dtransport.mode=stdio", "-jar",
            "target/spring-ai-mcp-sample-0.5.0-SNAPSHOT.jar")
    .build();

var transport = new StdioClientTransport(stdioParams);
var client = McpClient.using(transport).sync();
```

### SSE Client (ClientSse.java)
```java
var transport = new SseClientTransport(WebClient.builder().baseUrl("http://localhost:8080"));
var client = McpClient.using(transport).sync();
```

## Available Tools

### Weather Tool
- Name: `weather`
- Description: Weather forecast tool by location
- Parameters:
  - `city`: String - The city to get weather for
- Example:
```java
CallToolResult response = client.callTool(
    new CallToolRequest("weather", Map.of("city", "Sofia"))
);
```

### Calculator Tool
- Name: `calculator`
- Description: Performs basic arithmetic operations
- Parameters (JSON Schema):
  ```json
  {
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
  }
  ```
- Example:
```java
CallToolResult response = client.callTool(
    new CallToolRequest("calculator", 
        Map.of("operation", "multiply", "a", 2.0, "b", 3.0))
);
```

## Available Resources

### System Information Resource
- URI: `system://info`
- Description: Provides basic system information including Java version, OS, etc.
- MIME Type: application/json
- Returns: JSON object containing: javaVersion, osName, osVersion, osArch, processors, timestamp

## Available Prompts

### Greeting Prompt
- Name: `greeting`
- Description: A friendly greeting prompt
- Parameters:
  - `name`: String (required) - The name to greet
- Returns: A personalized greeting message from an assistant

## Client Usage Example

```java
// Initialize client
client.initialize();

// Test connection
client.ping();

// List available tools
ListToolsResult tools = client.listTools();
System.out.println("Available tools: " + tools);

// Call weather tool
CallToolResult weather = client.callTool(
    new CallToolRequest("weather", Map.of("city", "Sofia"))
);
System.out.println("Weather: " + weather);

// Call calculator tool
CallToolResult calc = client.callTool(
    new CallToolRequest("calculator", 
        Map.of("operation", "multiply", "a", 2.0, "b", 3.0))
);
System.out.println("Calculation: " + calc);

// Access system info resource
ReadResourceResult sysInfo = client.readResource(
    new ReadResourceRequest("system://info")
);
System.out.println("System Info: " + sysInfo);

// Use greeting prompt
GetPromptResult greeting = client.getPrompt(
    new GetPromptRequest("greeting", Map.of("name", "John"))
);
System.out.println("Greeting: " + greeting);

// Close client
client.closeGracefully();
```

## Server Usage Example

```java
@Configuration
public class CustomMcpServerConfig {
    @Bean
    public McpAsyncServer mcpServer(McpTransport transport) {
        // Configure server capabilities
        var capabilities = McpSchema.ServerCapabilities.builder()
            .resources(false, true)  // Resource support with list changes notifications
            .tools(true)            // Tool support with list changes notifications
            .prompts(true)          // Prompt support with list changes notifications
            .build();

        // Create custom tool
        var customTool = new ToolRegistration(
            new McpSchema.Tool("custom-tool", "Description", Map.of("param", "String")),
            (arguments) -> {
                String param = (String) arguments.get("param");
                return new CallToolResult(
                    List.of(new TextContent("Result: " + param)),
                    false
                );
            }
        );

        // Create custom resource
        var customResource = new ResourceRegistration(
            new McpSchema.Resource(
                "custom://resource",
                "Custom Resource",
                "Description",
                "application/json",
                null
            ),
            (request) -> new ReadResourceResult(
                List.of(new TextResourceContents(
                    request.uri(),
                    "application/json",
                    "{\"data\": \"example\"}"
                ))
            )
        );

        // Create custom prompt
        var customPrompt = new PromptRegistration(
            new McpSchema.Prompt(
                "custom-prompt",
                "Description",
                List.of(new McpSchema.PromptArgument("input", "Description", true))
            ),
            request -> {
                String input = (String) request.arguments().get("input");
                var message = new PromptMessage(
                    Role.ASSISTANT,
                    new TextContent("Response to: " + input)
                );
                return new GetPromptResult("Result", List.of(message));
            }
        );

        // Create and configure the server
        return McpServer.using(transport)
            .info("Custom MCP Server", "1.0.0")
            .capabilities(capabilities)
            .tools(List.of(customTool))
            .resources(Map.of("custom://resource", customResource))
            .prompts(List.of(customPrompt))
            .async();
    }
}
```

## Configuration

The application can be configured through `application.properties`:

- `transport.mode`: Transport mode to use (stdio/sse)
- `server.port`: Server port for SSE mode (default: 8080)
- Various logging configurations are available for debugging

## Implementation Details

The sample demonstrates:
- Creating an MCP server with custom tools, resources, and prompts
- Configuring different transport modes
- Implementing tool handlers with JSON schema validation
- Resource implementations with dynamic content generation
- Prompt implementations with parameter handling
- Error handling and response formatting
- Synchronous client usage patterns
