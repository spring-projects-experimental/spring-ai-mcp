# MCP Server Reference

## McpServer Factory

`org.springframework.ai.mcp.server.McpServer`

The `McpServer` class serves as a factory for creating Model Context Protocol (MCP) servers. It provides the main entry point for establishing MCP servers, offering both synchronous and asynchronous server implementations.

### Overview

The server factory uses a builder pattern for flexible configuration, allowing you to customize various aspects of the server including server information, capabilities, tools, resource providers, and prompt providers.

### Server Types

The factory can create two types of servers:
- `McpAsyncServer` for non-blocking operations
- `McpSyncServer` for blocking operations

### Builder Configuration

#### Basic Usage
```java
McpServer.using(transport)
    .serverInfo("my-server", "1.0.0")
    .addTool(new MyToolHandler())
    .async(); // or .sync()
```

#### Builder Methods

##### using(McpTransport transport)
```java
public static Builder using(McpTransport transport)
```
Start building an MCP server with the specified transport.

##### serverInfo(Implementation serverInfo)
```java
public Builder serverInfo(McpSchema.Implementation serverInfo)
```
Set the server implementation information.

##### serverInfo(String name, String version)
```java
public Builder serverInfo(String name, String version)
```
Set the server implementation information using name and version.

##### serverCapabilities(ServerCapabilities capabilities)
```java
public Builder serverCapabilities(McpSchema.ServerCapabilities serverCapabilities)
```
Set the server capabilities configuration.

##### addTool(ToolHandler handler)
```java
public <T extends McpAsyncServer.ToolHandler> Builder addTool(T toolHandler)
```
Add a tool handler to the server.

##### tool(Tool tool, Function<Map<String, Object>, CallToolResult> handler)
```java
public Builder tool(McpSchema.Tool tool, Function<Map<String, Object>, McpSchema.CallToolResult> handler)
```
Add a tool with its handler function.

##### addTools(List<ToolHandler> handlers)
```java
public Builder addTools(List<ToolHandler> toolHandlers)
```
Add multiple tool handlers to the server.

##### resourcesProvider(Function<String, List<Resource>> provider)
```java
public Builder resourcesProvider(Function<String, List<McpSchema.Resource>> resourcesProvider)
```
Set the resources provider function.

##### promptsProvider(Function<String, List<Prompt>> provider)
```java
public Builder promptsProvider(Function<String, List<McpSchema.Prompt>> promptsProvider)
```
Set the prompts provider function.

##### sync()
```java
public McpSyncServer sync()
```
Build and return a synchronous MCP server.

##### async()
```java
public AsyncServerBuilder async()
```
Build and return an asynchronous server builder.

### Usage Examples

#### Creating a Basic Server
```java
McpTransport transport = // obtain transport instance
McpServer.using(transport)
    .serverInfo("my-server", "1.0.0")
    .sync();
```

#### Creating a Server with Tools
```java
McpServer.using(transport)
    .serverInfo("calculation-server", "1.0.0")
    .tool(
        new McpSchema.Tool("calculate", "Performs calculations", Map.of(
            "operation", Map.of("type", "string"),
            "numbers", Map.of("type", "array")
        )),
        args -> {
            // Handle calculation
            return new McpSchema.CallToolResult(/* result */);
        }
    )
    .async();
```

#### Creating a Server with Resources and Prompts
```java
McpServer.using(transport)
    .serverInfo("content-server", "1.0.0")
    .resourcesProvider(cursor -> {
        // Return list of resources based on cursor
        return List.of(/* resources */);
    })
    .promptsProvider(cursor -> {
        // Return list of prompts based on cursor
        return List.of(/* prompts */);
    })
    .sync();
```

- Tool handlers must be thread-safe as they may be called concurrently
- The synchronous server is implemented as a wrapper around the asynchronous server
- Resource and prompt providers use cursor-based pagination
- The AsyncServerBuilder allows adding tools after server creation

### Tool Handler Interface

```java
public interface ToolHandler {
    String getName();
    String getDescription();
    Map<String, Object> getInputSchema();
    McpSchema.CallToolResult call(Map<String, Object> arguments);
}
```

Tool handlers implement this interface to define:
- Tool name and description
- Input schema for validation
- Execution logic in the call method

### Logging Capabilities

The MCP Server supports logging functionality that allows sending log messages with different severity levels to the Client. This feature can be enabled through server capabilities configuration.

#### Enabling Logging

```java
McpServer.using(transport)
    .serverInfo("my-server", "1.0.0")
    .capabilities(ServerCapabilities.builder().logging().build())
    .sync(); // or .async()
```

#### Logging Levels

The server supports the following logging levels:
- TRACE
- DEBUG
- INFO
- WARN
- ERROR
- FATAL

#### Sending Log Messages

For synchronous servers:
```java
LoggingMessageNotification notification = McpSchema.LoggingMessageNotification.builder()
    .level(McpSchema.LoggingLevel.INFO)
    .logger("my-logger")
    .data("Log message")
    .build();

mcpSyncServer.loggingNotification(notification);
```

For asynchronous servers:
```java
LoggingMessageNotification notification = McpSchema.LoggingMessageNotification.builder()
    .level(McpSchema.LoggingLevel.INFO)
    .logger("my-logger")
    .data("Log message")
    .build();

mcpAsyncServer.loggingNotification(notification)
    .subscribe();
```

- Logging works even if the logging capability is not enabled
- Null notifications are rejected with a McpError
- Log messages include:
  - Level: The severity level of the message
  - Logger: The name of the logger (typically identifies the source)
  - Data: The actual log message content
- The server implementation is transport-agnostic, allowing different transport mechanisms to handle the log messages
