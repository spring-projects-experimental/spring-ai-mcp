# StdioClientTransport

The `StdioClientTransport` class implements the [MCP Stdio Transport](https://spec.modelcontextprotocol.io/specification/basic/transports/#stdio) specification, providing communication with MCP servers over standard input/output streams.

## Overview

The Stdio transport uses standard input/output streams for bidirectional communication between the client and server:
- Messages are sent to the server via stdout
- Messages are received from the server via stdin
- Error and debug information is sent via stderr

## Message Format

Messages are exchanged as newline-delimited JSON-RPC messages:
- Each message is a single line of JSON text
- Messages are terminated with a newline character (`\n`)
- Messages follow the JSON-RPC 2.0 specification
- UTF-8 encoding is used for all messages

### Message Types

The transport handles three types of JSON-RPC messages:

1. **Requests** - Messages with both `method` and `id` fields:
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "example.method",
    "params": {}
}
```

2. **Notifications** - Messages with `method` but no `id` field:
```json
{
    "jsonrpc": "2.0",
    "method": "example.notification",
    "params": {}
}
```

3. **Responses** - Messages with either `result` or `error` fields:
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": {}
}
```

## Implementation Details

### Process Management

The transport manages a server process with:
- Command and arguments specified via `ServerParameters`
- Environment variables for configuration
- Proper process cleanup on shutdown

### Threading Model

The implementation uses three dedicated threads:
1. **Inbound** - Reads and processes messages from stdin
2. **Outbound** - Writes messages to stdout
3. **Error** - Handles stderr messages

### Error Handling

Errors are handled at multiple levels:
- Process startup failures
- Stream I/O errors
- Message parsing errors
- Process termination handling

## Usage Example

```java
// Create server parameters
ServerParameters params = ServerParameters.builder()
    .command("path/to/server")
    .args(List.of("--arg1", "value1"))
    .env(Map.of("ENV_VAR", "value"))
    .build();

// Create transport
StdioClientTransport transport = new StdioClientTransport(params);

// Start transport
transport.start();

// Send message
JSONRPCRequest request = new JSONRPCRequest();
request.setId(1);
request.setMethod("example.method");
request.setParams(new HashMap<>());
transport.send(request);

// Close transport
transport.closeGracefully().block();
```

## Error Messages

Error messages from the server process are:
- Written to stderr
- Logged at ERROR level
- Emitted through the error sink for handling
- UTF-8 encoded

## Debugging

For debugging purposes:
1. Enable DEBUG level logging to see detailed message flow
2. Monitor stderr for server process errors
3. Check process exit codes for abnormal termination

## Implementation Notes

- The transport ensures thread safety through dedicated schedulers
- Messages are processed asynchronously using Project Reactor
- Resources are properly cleaned up on shutdown
- The implementation is resilient to various failure modes
