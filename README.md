# Spring AI MCP

Java SDK for the Model Context Protocol (MCP), providing seamless integration between Java and Spring applications and MCP-compliant AI resources and tools.

## Overview

Spring AI MCP is an experimental project that provides Java and Spring Framework integration for the [Model Context Protocol](https://modelcontextprotocol.org/docs/concepts/architecture). It enables Java applications to interact with AI models and tools through a standardized interface, supporting both synchronous and asynchronous communication patterns.

<img src="docs/spring-ai-mcp-architecture.jpg" width="600">

## Modules

The project consists of two main modules:

### [mcp-core](./spring-ai-mcp-core/README.md)

The core module provides a Java implementation of the Model Context Protocol specification. It includes:
- Synchronous and asynchronous client implementations
- Standard MCP operations support (tool discovery, resource management, prompt handling). Support for request and notificaiotn handling.
- [Stdio](https://spec.modelcontextprotocol.io/specification/basic/transports/#stdio) and [SSE](https://spec.modelcontextprotocol.io/specification/basic/transports/#http-with-sse) transport implementations. 
- [Find more](./spring-ai-mcp-core/README.md).

### [mcp-spring](./spring-ai-mcp-spring/README.md)

The Spring integration module provides Spring-specific functionality:
- Integration with Spring AI's function calling system
- Spring-friendly abstractions for MCP clients
- Auto-configurations (WIP)

## Requirements

- Java 17 or later
- Maven 3.6 or later
- Spring AI 1.0.0-M4 or later

## Installation

Add the following dependencies to your Maven project:

```xml
<!-- For core MCP functionality -->
<dependency>
    <groupId>org.springframework.experimental</groupId>
    <artifactId>spring-ai-mcp-core</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>

<!-- For Spring integration -->
<dependency>
    <groupId>org.springframework.experimental</groupId>
    <artifactId>spring-ai-mcp-spring</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

## Example Demos

Explore these MCP examples in the [spring-ai-examples/model-context-protocol](https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol) repository:

- [SQLite Simple](https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol/sqlite/simple) - Demonstrates LLM integration with a database
- [SQLite Chatbot](https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol/sqlite/chatbot) - Interactive chatbot with SQLite database interaction
- [Filesystem](https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol/filesystem) - Enables LLM interaction with local filesystem folders and files
- [Brave](https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol/brave) - Enables natural language interactions with Brave Search, allowing you to perform internet searches.

## Documentation

- [Core Module Documentation](spring-ai-mcp-core/README.md)
- [Spring Integration Documentation](spring-ai-mcp-spring/README.md)
- [UML Class Diagrams](spring-ai-mcp-core/docs/spring-ai-mcp-uml-classdiagram.svg)

## Development

### Building from Source

```bash
mvn clean install
```

### Running Tests

```bash
mvn test
```

## Project Information

- **Group ID**: org.springframework.experimental
- **Version**: 0.2.0-SNAPSHOT
- **Java Version**: 17
- **Spring AI Version**: 1.0.0-M4

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

```
Copyright 2024-2024 the original author or authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
