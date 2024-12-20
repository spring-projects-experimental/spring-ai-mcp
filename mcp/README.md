# Model Context Protocol (MCP) Java SDK

A Java implementation of the [Model Context Protocol](https://modelcontextprotocol.org/docs/concepts/architecture) specification, providing both synchronous and asynchronous clients for MCP server interactions.

## Overview

This SDK implements the Model Context Protocol, enabling seamless integration with AI models and tools through a standardized interface. It supports both synchronous and asynchronous communication patterns, making it suitable for various use cases and integration scenarios.

## Features

- Synchronous and Asynchronous MCP Client implementations
- Synchronous and Asynchronous MCP Server implementations (WIP: comming with 0.3.0)
- Standard MCP operations support:
  - Protocol version compatibility negotiation
  - Client-server capability exchange
  - Tool discovery and execution with change notifications
  - Tool list change notifications with non-blocking consumer support
  - Resource management with URI templates
  - Resource subscription system
  - Roots list management and notifications
  - Prompt handling and management
  - Server initialization and ping
- Multiple transport implementations:
  - Stdio-based transport for process-based communication
  - SSE-based transport for HTTP streaming
- Configurable request timeouts
- Customizable JSON serialization/deserialization

## Installation

Add the following dependency to your Maven project:

```xml
<dependency>
    <groupId>org.springframework.experimental</groupId>
    <artifactId>mcp</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Transport Layer Options

The SDK provides two transport implementations:

#### StdioClientTransport
Standard I/O based transport for process-based communication with MCP servers:

```java
ServerParameters params = ServerParameters.builder("npx")
    .args("-y", "@modelcontextprotocol/server-everything", "dir")
    .build();
McpTransport transport = new StdioClientTransport(params);
```

#### SseClientTransport
Server-Sent Events (SSE) based transport following the MCP HTTP with SSE transport specification:

```java
WebClient.Builder webClientBuilder = WebClient.builder()
    .baseUrl("http://your-mcp-server");
McpTransport transport = new SseClientTransport(webClientBuilder);
```

The SSE transport provides:
- Bidirectional communication over HTTP
- Automatic reconnection for transient failures
- Inbound message streaming via SSE
- Outbound message delivery via HTTP POST
- Graceful shutdown handling
- Configurable JSON serialization


### Sync MCP Client Example

```java
// Create and initialize sync client
McpTransport mcpTransport = ...

try (McpSyncClient client = McpClient.using(mcpTransport).sync()) {
    // Initialize connection with protocol version and capabilities
    McpSchema.InitializeResult initResult = client.initialize();

    // List available tools
    McpSchema.ListToolsResult tools = client.listTools();

    // Execute a tool
    McpSchema.CallToolResult result = client.callTool(
        new McpSchema.CallToolRequest("echo", Map.of("message", "Hello!"))
    );

    // Resource management
    McpSchema.ListResourcesResult resources = client.listResources();
    McpSchema.ReadResourceResult resource = client.readResource(
        new McpSchema.ReadResourceRequest("resource-uri")
    );

    // List and retrieve prompts
    ListPromptsResult prompts = client.listPrompts();
    GetPromptResult prompt = client.getPrompt(
        new McpSchema.GetPromptRequest("prompt-id", Map.of())
    );
}
```

### Async MCP Client Example with Custom Configuration

```java
McpTransport mcpTransport = ...

// Initialize async client with custom timeout and object mapper
McpAsyncClient client = McpClient.using(mcpTransport)
    .requestTimeout(Duration.ofSeconds(30))
    .async();

// Initialize connection and chain operations
var result = client.initialize()
    .flatMap(initResult -> {
        // Connection initialized with protocol version compatibility
        return client.listTools();
    })
    .flatMap(tools -> {
        // Process available tools
        return client.callTool(new McpSchema.CallToolRequest("echo",
            Map.of("message", "Hello MCP!")));
    })
    .flatMap(toolResult -> {
        // Handle tool execution result
        return client.listPrompts();
    })
    .flatMap(prompts -> {
        // Process available prompts
        return client.getPrompt(new McpSchema.GetPromptRequest("prompt-id", Map.of()));
    });

// Subscribe to resource changes
var subscription = client.listResources()
    .flatMap(resources -> {
        return client.subscribeResource(new McpSchema.SubscribeRequest("resource-uri"));
    });

// Set up tools change notification handling
List<Consumer<List<McpSchema.Tool>>> toolsChangeConsumers = List.of(
    tools -> {
        // Handle tools list changes reactively
        tools.forEach(tool -> {
            System.out.println("Tool updated: " + tool.name());
        });
    }
);

McpAsyncClient clientWithToolsNotifications = McpClient.using(transport)
    .toolsChangeConsumer(toolsChangeConsumers)
    .async();

// The client will now automatically handle tools/list_changed notifications
// and invoke the consumers on the boundedElastic scheduler to avoid blocking

// Handle results reactively or block if needed
McpSchema.GetPromptResult promptResult = result.block();
subscription.block();

// Cleanup
client.closeGracefully().block();
```

## Architecture

The SDK follows a layered architecture with clear separation of concerns:

### Core Components

- **McpClient**: Factory class for creating sync and async clients with optional custom configuration
- **McpAsyncClient**: Primary async implementation using Project Reactor for non-blocking operations
- **McpSyncClient**: Synchronous wrapper around the async client for blocking operations
- **McpSession**: Core session interface defining communication patterns
- **McpTransport**: Transport layer interface for client/server communication
- **McpSchema**: Comprehensive protocol schema definitions
- **DefaultMcpSession**: Default implementation of the session management
- **StdioClientTransport**: Standard I/O based client to server communication
- **SseClientTransport**: HTTP-based transport using Server-Sent Events for bidirectional client-sever communication

<img src="../docs/spring-ai-mcp-uml-classdiagram.svg" width="600"/>

### Key Interactions

1. Client Initialization
   - Transport setup and connection establishment
   - Protocol version compatibility check
   - Capability negotiation
   - Implementation details exchange

2. Message Flow
   - JSON-RPC message creation and validation
   - Transport layer handling
   - Response processing with type safety
   - Error handling with specific error codes

3. Resource Management
   - Resource discovery and listing
   - URI template-based resource access
   - Subscription system for change notifications
   - Resource content retrieval

4. Prompt System
   - Prompt discovery and listing
   - Parameter-based prompt retrieval
   - Change notifications support
   - Prompt content management

5. Tool Execution
   - Tool discovery and capability checking
   - Parameter validation and processing
   - Execution handling with timeout support
   - Result processing with error handling

### Roots List Support

The SDK supports the MCP roots list capability, which allows servers to understand which directories and files they have access to. Clients can provide a list of root directories/files and notify servers when this list changes.

#### Features
- Define root providers that supply filesystem access boundaries
- Support for roots list changed notifications
- Automatic roots list request handling

#### Example with Roots List Configuration

```java
// Create root providers
List<Supplier<List<Root>>> rootProviders = List.of(
    () -> List.of(new Root("file:///workspace/project", "Project Root")),
    () -> List.of(new Root("file:///workspace/docs", "Documentation"))
);

// Create async client with roots list support
McpAsyncClient client = new McpAsyncClient(
    new StdioClientTransport(params),
    Duration.ofSeconds(30),
    rootProviders,  // Configure root providers
    true           // Enable roots list changed notifications
);

// Initialize connection
client.initialize()
    .doOnSuccess(result -> {
        // Connection initialized with roots list capability
        
        // Notify server when roots list changes
        return client.sendRootsListChanged();
    })
    .subscribe();
```

### Change Notifications Support

The SDK supports automatic handling of changes through a non-blocking notification system for tools, resources, and prompts:

#### Features
- Register multiple consumers for tools, resources, and prompts changes
- Non-blocking execution using Project Reactor's boundedElastic scheduler
- Automatic list request handling when notifications are received
- Error resilient with proper error handling and logging

#### Example with Change Notifications

```java
// Create tool change consumers
List<Consumer<List<McpSchema.Tool>>> toolsChangeConsumers = List.of(
    tools -> {
        // First consumer - e.g., update UI
        tools.forEach(tool -> updateToolsUI(tool));
    },
    tools -> {
        // Second consumer - e.g., update cache
        toolsCache.updateTools(tools);
    }
);

// Create resource change consumers
List<Consumer<List<McpSchema.Resource>>> resourcesChangeConsumers = List.of(
    resources -> {
        // Handle resource changes
        resources.forEach(resource -> {
            System.out.println("Resource updated: " + resource.uri());
            updateResourcesUI(resource);
        });
    }
);

// Create prompt change consumers
List<Consumer<List<McpSchema.Prompt>>> promptsChangeConsumers = List.of(
    prompts -> {
        // Handle prompt changes
        prompts.forEach(prompt -> {
            System.out.println("Prompt updated: " + prompt.name());
            updatePromptsCache(prompt);
        });
    }
);

// Create client with change notification support
McpAsyncClient client = McpClient.using(transport)
    .toolsChangeConsumer(toolsChangeConsumer)
    .resourcesChangeConsumer(resourcesChangeConsumer)
    .promptsChangeConsumer(promptsChangeConsumer)
    .async();

// Initialize client
client.initialize()
    .doOnSuccess(result -> {
        // Client will automatically handle all change notifications
        // and invoke consumers non-blockingly on boundedElastic scheduler
    })
    .subscribe();
```

The change notification system ensures that all consumers are executed non-blockingly, preventing any potential performance impact from blocking implementations. All consumers are executed on Project Reactor's boundedElastic scheduler, making it safe to perform potentially blocking operations within the consumers.

Each type of notification handler operates independently:
- Tools: Handles notifications when available tools change
- Resources: Handles notifications when available resources change
- Prompts: Handles notifications when available prompts change

## Error Handling

The SDK provides comprehensive error handling through the McpError class:

- Protocol version incompatibility
- Transport-level communication errors
- JSON-RPC protocol violations
- Tool execution failures
- Resource access and subscription errors
- Prompt management errors
- Request timeout handling
- Server capability mismatches
- SSE connection failures and retry handling
- HTTP request/response errors

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

Copyright 2024 - 2024 the original author or authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
