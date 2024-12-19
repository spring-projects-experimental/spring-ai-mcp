# SSE Server Transport Implementation for MCP

## Overview

The `SseClientTransport` class implements the Model Context Protocol (MCP) HTTP with SSE transport specification, providing a bidirectional communication channel between clients and servers. This implementation is part of the Spring AI MCP Core library and follows the [MCP HTTP with SSE Transport Specification](https://spec.modelcontextprotocol.io/specification/basic/transports/#http-with-sse).

## Key Features

- **Bidirectional Communication**:
  - Inbound: Receives messages through SSE connection
  - Outbound: Sends messages via HTTP POST requests

- **Error Handling**:
  - Automatic reconnection for transient failures
  - Graceful shutdown capabilities
  - Comprehensive error reporting

- **Message Flow**:
  1. Client establishes SSE connection to `/sse` endpoint
  2. Server sends 'endpoint' event with message URI
  3. Client uses provided URI for outbound messages

## Technical Details

### Transport Architecture

The transport consists of two main processing pipelines:

1. **Inbound Processing**:
   - Establishes SSE connection to `/sse` endpoint
   - Handles two types of events:
     - `message`: JSON-RPC protocol messages
     - `endpoint`: Server-provided URI for outbound messages
   - Implements automatic retry logic for connection failures

2. **Outbound Processing**:
   - Sends messages via HTTP POST to server-provided endpoint
   - Uses dedicated scheduler for sequential message processing
   - Handles JSON serialization and HTTP communication

### Key Components

- **WebClient**: Handles HTTP communications for both SSE and POST requests
- **ObjectMapper**: Manages JSON serialization/deserialization
- **Scheduler**: Ensures sequential outbound message processing
- **Message Endpoint Sink**: Manages server-provided endpoint URI

## Usage Example

```java
// Create WebClient builder with base URL
WebClient.Builder webClientBuilder = WebClient.builder()
    .baseUrl("http://localhost:3001");

// Initialize transport
SseClientTransport transport = new SseClientTransport(webClientBuilder);

// Create a JSON-RPC request
JSONRPCRequest request = new JSONRPCRequest(
    McpSchema.JSONRPC_VERSION,
    "method-name",
    "request-id",
    Map.of("key", "value")
);

// Send message
transport.sendMessage(request)
    .subscribe(
        success -> System.out.println("Message sent successfully"),
        error -> System.err.println("Error sending message: " + error)
    );

// Graceful shutdown when done
transport.closeGracefully()
    .subscribe();
```

## Implementation Details

### Message Processing

1. **Inbound Messages**:
   - Received through SSE connection
   - Automatically deserialized from JSON
   - Routed to appropriate handlers based on event type

2. **Outbound Messages**:
   - Serialized to JSON
   - Sent via HTTP POST
   - Processed sequentially on dedicated thread

### Error Handling

- **Connection Failures**:
  - Automatic retry for transient failures
  - Configurable retry policies
  - Graceful handling of permanent failures

- **Message Processing Errors**:
  - Comprehensive error reporting
  - Clean failure handling
  - Maintains system stability

### Shutdown Process

The transport implements a graceful shutdown mechanism that:
1. Marks transport as closing
2. Disposes of all subscriptions
3. Cleans up schedulers
4. Completes message endpoint sink

## Technical Specifications

- **Protocol**: HTTP with Server-Sent Events (SSE)
- **Message Format**: JSON-RPC 2.0
- **Content Type**: application/json for outbound, text/event-stream for inbound
- **Endpoints**:
  - SSE Connection: `/sse`
  - Message Endpoint: Dynamically provided by server

## Dependencies

- Spring WebFlux for reactive HTTP client
- Jackson for JSON processing
- Project Reactor for reactive streams
- SLF4J for logging

## Thread Safety

The implementation is thread-safe and designed for concurrent use:
- Inbound processing uses SSE event loop
- Outbound processing uses dedicated single-threaded scheduler
- State management uses thread-safe constructs

## Testing

The implementation includes comprehensive tests covering:
- Message processing
- Error handling
- Connection management
- Shutdown procedures
- Edge cases and failure scenarios

For detailed test examples, see `SseClientTransportTests.java`.
