# MCP Session Components

## Overview

The MCP (Model Context Protocol) session components implement the [MCP specification](https://spec.modelcontextprotocol.io/) for managing bidirectional JSON-RPC communication between clients and model servers. 
The implementation provides a robust foundation for request-response patterns and notification handling.

## Core Components

### McpSession Interface

The `McpSession` interface defines the contract for MCP communication with three primary operations:

1. Request-Response Communication
```java
<T> Mono<T> sendRequest(String method, Object requestParams, TypeReference<T> typeRef);
```

2. Notification Handling
```java
Mono<Void> sendNotification(String method, Map<String, Object> params);
```

3. Session Lifecycle Management
```java
Mono<Void> closeGracefully();
```

### DefaultMcpSession Implementation

The `DefaultMcpSession` provides the concrete implementation of the MCP session with the following key features:

#### 1. Message Correlation
- Uses unique request IDs combining session UUID prefix and atomic counter
- Maintains thread-safe tracking of pending responses
- Implements timeout management for requests

#### 2. Handler Registration
```java
public interface RequestHandler {
    Mono<Object> handle(Object params);
}

public interface NotificationHandler {
    Mono<Void> handle(Object params);
}
```

#### 3. Transport Integration
- Abstracts transport details through `McpTransport` interface
- Supports different transport implementations (SSE, STDIO)
- Manages message serialization using Jackson

## Implementation Details

- The session processes three types of messages: McpSchema.JSONRPCResponse, McpSchema.JSONRPCRequest, McpSchema.JSONRPCNotificaiton.
- The implementation ensures thread safety through

### Error Handling

WIP

## Integration Guidelines

1. **Transport Selection**
   - Choose appropriate transport (SSE, STDIO) based on use case
   - Configure transport-specific parameters
   - Handle transport lifecycle properly

2. **Message Handling**
   - Register handlers before starting session
   - Use appropriate error codes from MCP specification
   - Implement proper request/response correlation

3. **Type Safety**
   - Use appropriate TypeReference for response deserialization
   - Validate request parameters
   - Handle type conversion errors
