# Java & Spring MCP

Set of projects that provide Java SDK and Spring Framework integration for the [Model Context Protocol](https://modelcontextprotocol.org/docs/concepts/architecture). 
It enables Java applications to interact with AI models and tools through a standardized interface, supporting both synchronous and asynchronous communication patterns.

<img src="mcp-docs/src/main/antora/modules/ROOT/images/spring-ai-mcp-architecture.jpg" width="600">

## Projects

### [MCP Java SDK](https://docs.spring.io/spring-ai-mcp/reference/mcp.html)

Java implementation of the Model Context Protocol specification. It includes:
- Synchronous and asynchronous [MCP Client](https://github.com/spring-projects-experimental/spring-ai-mcp/blob/main/mcp/README.md#client-usage-examples) and [MCP Server](https://github.com/spring-projects-experimental/spring-ai-mcp/blob/main/mcp/README.md#server-usage-examples) implementations
- Standard MCP operations support (tool discovery, resource management, prompt handling, structured logging). Support for request and notification handling.
- [Stdio](https://spec.modelcontextprotocol.io/specification/basic/transports/#stdio) and [SSE](https://spec.modelcontextprotocol.io/specification/basic/transports/#http-with-sse) transport implementations. 
- [Find more](./mcp/README.md).

### [Spring AI MCP](https://docs.spring.io/spring-ai-mcp/reference/spring-mcp.html)

The Spring integration module provides Spring-specific functionality:
- Integration with Spring AI's function calling system
- Spring-friendly abstractions for MCP clients
- Auto-configurations (WIP)


## Installation

Add the following dependencies to your Maven project:

```xml
<!-- For core MCP functionality -->
<dependency>
    <groupId>org.springframework.experimental</groupId>
    <artifactId>mcp</artifactId>
    <version>0.5.0-SNAPSHOT</version>
</dependency>

<!-- For Spring AI integration -->
<dependency>
    <groupId>org.springframework.experimental</groupId>
    <artifactId>spring-ai-mcp</artifactId>
    <version>0.5.0-SNAPSHOT</version>
</dependency>
```

This is a milestone release, not available on Maven Central. 
Add this repository to your POM:

```xml
<repositories>
  <repository>
    <id>spring-milestones</id>
    <name>Spring Milestones</name>
    <url>https://repo.spring.io/libs-milestone-local</url>
    <snapshots>
      <enabled>false</enabled>
    </snapshots>
  </repository>
</repositories>
```


## Example Demos

Explore these MCP examples in the [spring-ai-examples/model-context-protocol](https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol) repository:

- [SQLite Simple](https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol/sqlite/simple) - Demonstrates LLM integration with a database
- [SQLite Chatbot](https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol/sqlite/chatbot) - Interactive chatbot with SQLite database interaction
- [Filesystem](https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol/filesystem) - Enables LLM interaction with local filesystem folders and files
- [Brave](https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol/brave) - Enables natural language interactions with Brave Search, allowing you to perform internet searches.
- [Spring-ai-mcp-sample](./spring-ai-mcp-sample/) - Showcases how to create and use MCP servers and clients with different transport modes and capabilities.
## Documentation

- [Java MCP SDK documentation](mcp/README.md)
  - [Reference documentation](docs/ref-index.md)
- [Spring Integration documentation](spring-ai-mcp/README.md)


## Development

- Building from Source

```bash
mvn clean install
```

- Running Tests

```bash
mvn test
```


## Contributing

This is an experimental Spring project. Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Submit a Pull Request

## Team

- Christian Tzolov
- Dariusz Jędrzejczyk

## Links

- [GitHub Repository](https://github.com/spring-projects-experimental/spring-ai-mcp)
- [Issue Tracker](https://github.com/spring-projects-experimental/spring-ai-mcp/issues)
- [CI/CD](https://github.com/spring-projects-experimental/spring-ai-mcp/actions)

## License

This project is licensed under the [Apache License 2.0](LICENSE).
