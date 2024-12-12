# Model Context Protocol (MCP) Java SDK

A Java implementation of the [Model Context Protocol](https://modelcontextprotocol.org/docs/concepts/architecture) specification, providing both synchronous and asynchronous clients for MCP server interactions.

## Overview

This SDK implements the Model Context Protocol, enabling seamless integration with AI models and tools through a standardized interface. It supports both synchronous and asynchronous communication patterns, making it suitable for various use cases and integration scenarios.

## Features

- Synchronous and Asynchronous client implementations
- Standard MCP operations support:
  - Tool discovery and execution
  - Resource management and templates
  - Prompt handling and management
  - Resource subscription system
  - Server initialization and ping
- Stdio-based server transport
- Reactive programming support using Project Reactor

## Installation

Add the following dependency to your Maven project:

```xml
<dependency>
    <groupId>org.springframework.experimental</groupId>
    <artifactId>spring-ai-mcp-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Documentation

Detailed UML class diagrams showing the relationships between components can be found in [docs/class-diagrams.puml](docs/class-diagrams.puml). The diagrams include:
- Core Components: Shows the main interfaces, classes, and their relationships
- Message Flow: Illustrates the message and resource type hierarchies

## Usage

### Sync Client Example

```java
// Create and initialize sync client
ServerParameters params = ServerParameters.builder("npx")
    .args("-y", "@modelcontextprotocol/server-everything", "dir")
    .build();

try (McpSyncClient client = McpClient.sync(new StdioServerTransport(params))) {
    // Initialize connection
    McpSchema.InitializeResult initResult = client.initialize();

    // List tools synchronously
    McpSchema.ListToolsResult tools = client.listTools(null);

    // Call tool synchronously
    McpSchema.CallToolResult result = client.callTool(
        new McpSchema.CallToolRequest("echo", Map.of("message", "Hello!"))
    );

    // Resource management
    McpSchema.ListResourcesResult resources = client.listResources(null);
    McpSchema.ReadResourceResult resource = client.readResource(
        new McpSchema.ReadResourceRequest("resource-uri")
    );

    // Prompt management
    ListPromptsResult prompts = client.listPrompts(null);
    GetPromptResult prompt = client.getPrompt(
        new McpSchema.GetPromptRequest("prompt-id", Map.of())
    );
}
```

### Async Client Example

```java
// Create server parameters
ServerParameters params = ServerParameters.builder("npx")
    .args("-y", "@modelcontextprotocol/server-everything", "dir")
    .build();

// Initialize the async client
McpAsyncClient client = McpClient.async(
    new StdioServerTransport(params)
);

// Initialize the connection
var promptResult = client.initialize()
    flatMap(result -> {
        // Connection initialized
        return client.listTools(null);
    })
    .flatMap(tools -> {
        // Process tools
        return client.callTool(new McpSchema.CallToolRequest("echo",
            Map.of("message", "Hello MCP!")));
    })
    .flatMap(result -> {
        // Handle tool result
        return client.listPrompts(null);
    })
    .flatMap(prompts -> {
        // Process available prompts
        return client.getPrompt(new McpSchema.GetPromptRequest("prompt-id", Map.of()));
    });

// Handle prompt result, e.g. by blocking on it
McpSchema.GetPromptResult result = promptResult.block();

// Resource management example
var resourcesResult = client.listResources(null)
    .flatMap(resources -> {
        // Subscribe to resource changes
        return client.subscribeResource(new McpSchema.SubscribeRequest("resource-uri"));
    });

// Handle resources result
resourcesResult.block();

// Cleanup
client.closeGracefully().block();
```

## Architecture

The SDK follows a layered architecture:

### Core Components

- **McpClient**: Factory class for creating sync and async clients
- **McpAsyncClient**: Primary async implementation using Project Reactor
- **McpSyncClient**: Synchronous wrapper around the async client
- **McpSession**: Core session interface defining communication patterns
- **McpTransport**: Transport layer interface
- **McpSchema**: Comprehensive protocol schema definitions
- **AbstractMcpTransport**: Base transport implementation
- **StdioServerTransport**: Stdio-based server communication

<img src="docs/spring-ai-mcp-uml-classdiagram.svg" width="600"/>

### Key Interactions

1. Client initialization
   - Transport setup
   - Server connection establishment
   - Protocol handshake

2. Message Flow
   - JSON-RPC message creation
   - Transport layer handling
   - Response processing
   - Error handling

3. Resource Management
   - Resource listing and reading
   - Template management
   - Subscription handling
   - Change notifications

4. Prompt System
   - Prompt discovery
   - Prompt retrieval
   - Change notifications

5. Tool Execution
   - Tool discovery
   - Parameter validation
   - Execution handling
   - Result processing

## Error Handling

The SDK provides comprehensive error handling through the McpError class:

- Transport-level errors
- Protocol violations
- Tool execution failures
- Resource access errors
- Subscription handling errors
- Prompt management errors
- Timeout handling

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
