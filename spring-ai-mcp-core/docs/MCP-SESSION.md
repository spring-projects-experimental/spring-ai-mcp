# MCP Session Components

## Overview

The MCP (Model Context Protocol) session components implement the [MCP specification](https://spec.modelcontextprotocol.io/) for managing bidirectional JSON-RPC communication between clients and model servers. The implementation provides a robust foundation for request-response patterns and notification handling.

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

### Message Processing

The session processes three types of messages:

1. **Responses**
```java
if (message instanceof McpSchema.JSONRPCResponse response) {
    var sink = pendingResponses.remove(response.id());
    if (sink != null) {
        sink.success(response);
    }
}
```

2. **Requests**
```java
if (message instanceof McpSchema.JSONRPCRequest request) {
    handleIncomingRequest(request)
        .subscribe(response -> transport.sendMessage(response).subscribe());
}
```

3. **Notifications**
```java
if (message instanceof McpSchema.JSONRPCNotification notification) {
    handleIncomingNotification(notification)
        .subscribe();
}
```

### Thread Safety

The implementation ensures thread safety through:
- `ConcurrentHashMap` for response tracking and handlers
- Atomic request ID generation
- Immutable configuration

### Error Handling

Comprehensive error handling includes:
- Transport-level errors
- Method not found errors
- Request timeout management
- Internal processing errors

## Usage Example

```java
// Create session
DefaultMcpSession session = new DefaultMcpSession(
    Duration.ofSeconds(30),    // Request timeout
    objectMapper,              // JSON serialization
    transport,                 // Transport implementation
    // Register request handler to a method name "echo"
    Map.of("echo", params -> 
        Mono.just("Echo: " + params)),
    // Register notification handler to a method name "status"
    Map.of("status",  params ->  
        Mono.fromRunnable(() -> logger.info("Status update: {}", params) )) 
);


// Send request
...

// Send notification
...

// Close session
session.closeGracefully().subscribe();
```

## Best Practices

1. **Session Lifecycle**
   - Use `closeGracefully()` for normal shutdown

2. **Error Handling**
   - Implement proper error handling in request/notification handlers
   - Configure appropriate request timeouts
   - Handle transport errors appropriately

3. **Message Processing**
   - Keep handlers lightweight and non-blocking
   - Use appropriate response types with TypeReference
   - Handle notifications asynchronously

4. **Resource Management**
   - Clean up resources in handlers when necessary
   - Monitor pending responses to avoid memory leaks
   - Use appropriate timeout configurations

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

## Conclusion

The MCP session implementation provides a robust foundation for building MCP-compliant applications. It offers:
- Type-safe message handling
- Thread-safe operation
- Flexible transport integration
- Comprehensive error handling
- Efficient resource management
