/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.server;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.mcp.server.McpServer.PromptRegistration;
import org.springframework.ai.mcp.server.McpServer.ResourceRegistration;
import org.springframework.ai.mcp.server.McpServer.ToolRegistration;
import org.springframework.ai.mcp.spec.DefaultMcpSession;
import org.springframework.ai.mcp.spec.DefaultMcpSession.NotificationHandler;
import org.springframework.ai.mcp.spec.McpError;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.spec.McpSchema.LoggingLevel;
import org.springframework.ai.mcp.spec.McpSchema.LoggingMessageNotification;
import org.springframework.ai.mcp.spec.McpSchema.Tool;
import org.springframework.ai.mcp.spec.McpTransport;
import org.springframework.ai.mcp.util.Utils;

/**
 * The Model Context Protocol (MCP) server implementation that provides asynchronous
 * communication.
 *
 * @author Christian Tzolov
 */
public class McpAsyncServer {

	private final static Logger logger = LoggerFactory.getLogger(McpAsyncServer.class);

	/**
	 * The MCP session implementation that manages bidirectional JSON-RPC communication
	 * between clients and servers.
	 */
	private final DefaultMcpSession mcpSession;

	private final McpTransport transport;

	private final McpSchema.ServerCapabilities serverCapabilities;

	private final McpSchema.Implementation serverInfo;

	/**
	 * Thread-safe list of tool handlers that can be modified at runtime.
	 */
	private final CopyOnWriteArrayList<ToolRegistration> tools;

	private final CopyOnWriteArrayList<McpSchema.ResourceTemplate> resourceTemplates;

	private final ConcurrentHashMap<String, ResourceRegistration> resources;

	private final ConcurrentHashMap<String, PromptRegistration> prompts;

	private LoggingLevel minLoggingLevel = LoggingLevel.DEBUG;

	/**
	 * Create a new McpAsyncServer with the given transport and capabilities.
	 * @param mcpTransport The transport layer implementation for MCP communication
	 * @param serverInfo The server implementation details
	 * @param serverCapabilities The server capabilities
	 * @param tools The list of tool registrations
	 * @param resources The map of resource registrations
	 * @param resourceTemplates The list of resource templates
	 * @param prompts The map of prompt registrations
	 */
	public McpAsyncServer(McpTransport mcpTransport, McpSchema.Implementation serverInfo,
			McpSchema.ServerCapabilities serverCapabilities, List<ToolRegistration> tools,
			Map<String, ResourceRegistration> resources, List<McpSchema.ResourceTemplate> resourceTemplates,
			Map<String, PromptRegistration> prompts) {

		this.serverInfo = serverInfo;
		this.tools = new CopyOnWriteArrayList<>(tools != null ? tools : List.of());
		this.resources = !Utils.isEmpty(resources) ? new ConcurrentHashMap<>(resources) : new ConcurrentHashMap<>();
		this.resourceTemplates = !Utils.isEmpty(resourceTemplates) ? new CopyOnWriteArrayList<>(resourceTemplates)
				: new CopyOnWriteArrayList<>();
		this.prompts = !Utils.isEmpty(prompts) ? new ConcurrentHashMap<>(prompts) : new ConcurrentHashMap<>();

		this.serverCapabilities = (serverCapabilities != null) ? serverCapabilities : new McpSchema.ServerCapabilities(
				null, // experimental
				new McpSchema.ServerCapabilities.LoggingCapabilities(), // Enable logging
																		// by default
				!Utils.isEmpty(this.prompts) ? new McpSchema.ServerCapabilities.PromptCapabilities(false) : null,
				!Utils.isEmpty(this.resources) ? new McpSchema.ServerCapabilities.ResourceCapabilities(false, false)
						: null,
				!Utils.isEmpty(this.tools) ? new McpSchema.ServerCapabilities.ToolCapabilities(false) : null);

		Map<String, DefaultMcpSession.RequestHandler> requestHandlers = new HashMap<>();

		// Initialize request handlers for standard MCP methods
		requestHandlers.put("initialize", initializeRequestHandler());

		// Ping MUST respond with an empty data, but not NULL response.
		requestHandlers.put("ping", (params) -> Mono.<Object>just(""));

		// Add tools API handlers if the tool capability is enabled
		if (this.serverCapabilities.tools() != null) {
			requestHandlers.put("tools/list", toolsListRequestHandler());
			requestHandlers.put("tools/call", toolsCallRequestHandler());
		}

		// Add resources API handlers if provided
		if (!Utils.isEmpty(this.resources)) {
			requestHandlers.put("resources/list", resourcesListRequestHandler());
			requestHandlers.put("resources/read", resourcesReadRequestHandler());
		}

		// Add resource templates API handlers if provided.
		if (!Utils.isEmpty(this.resourceTemplates)) {
			requestHandlers.put("resources/templates/list", resourceTemplateListRequestHandler());
		}

		// Add prompts API handlers if provider exists
		if (!Utils.isEmpty(this.prompts)) {
			requestHandlers.put("prompts/list", promptsListRequestHandler());
			requestHandlers.put("prompts/get", promptsGetRequestHandler());
		}

		// Add logging API handlers if the logging capability is enabled
		if (this.serverCapabilities.logging() != null) {
			requestHandlers.put("logging/setLevel", setLoggerRequestHandler());
		}

		Map<String, NotificationHandler> notificationHandlers = new HashMap<>();

		notificationHandlers.put("notifications/initialized", (params) -> Mono.empty());

		this.transport = mcpTransport;
		this.mcpSession = new DefaultMcpSession(Duration.ofSeconds(10), mcpTransport, requestHandlers,
				notificationHandlers);
	}

	// ---------------------------------------
	// Lifecycle Management
	// ---------------------------------------
	private DefaultMcpSession.RequestHandler initializeRequestHandler() {
		return params -> {
			McpSchema.InitializeRequest initializeRequest = transport.unmarshalFrom(params,
					new TypeReference<McpSchema.InitializeRequest>() {
					});

			logger.info("Client initialize request - Protocol: {}, Capabilities: {}, Info: {}",
					initializeRequest.protocolVersion(), initializeRequest.capabilities(),
					initializeRequest.clientInfo());

			if (!McpSchema.LATEST_PROTOCOL_VERSION.equals(initializeRequest.protocolVersion())) {
				return Mono
					.<Object>error(new McpError(
							"Unsupported protocol version from client: " + initializeRequest.protocolVersion()))
					.publishOn(Schedulers.boundedElastic());
			}

			return Mono
				.<Object>just(new McpSchema.InitializeResult(McpSchema.LATEST_PROTOCOL_VERSION, this.serverCapabilities,
						this.serverInfo, null))
				.publishOn(Schedulers.boundedElastic());
		};
	}

	/**
	 * Gracefully closes the server, allowing any in-progress operations to complete.
	 * @return A Mono that completes when the server has been closed
	 */
	public Mono<Void> closeGracefully() {
		return this.mcpSession.closeGracefully();
	}

	/**
	 * Close the server immediately.
	 */
	public void close() {
		this.mcpSession.close();
	}

	// ---------------------------------------
	// Tool Management
	// ---------------------------------------

	/**
	 * Add a new tool registration at runtime.
	 * @param toolRegistration The tool registration to add
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> addTool(ToolRegistration toolRegistration) {
		if (toolRegistration == null) {
			return Mono.error(new McpError("Tool registration must not be null"));
		}
		if (toolRegistration.tool() == null) {
			return Mono.error(new McpError("Tool must not be null"));
		}
		if (toolRegistration.call() == null) {
			return Mono.error(new McpError("Tool call handler must not be null"));
		}
		if (this.serverCapabilities.tools() == null) {
			return Mono.error(new McpError("Server must be configured with tool capabilities"));
		}

		// Check for duplicate tool names
		if (this.tools.stream().anyMatch(th -> th.tool().name().equals(toolRegistration.tool().name()))) {
			return Mono.error(new McpError("Tool with name '" + toolRegistration.tool().name() + "' already exists"));
		}

		this.tools.add(toolRegistration);
		logger.info("Added tool handler: {}", toolRegistration.tool().name());
		if (this.serverCapabilities.tools().listChanged()) {
			return notifyToolsListChanged();
		}
		return Mono.empty();
	}

	/**
	 * Remove a tool handler at runtime.
	 * @param toolName The name of the tool handler to remove
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> removeTool(String toolName) {
		if (toolName == null) {
			return Mono.error(new McpError("Tool name must not be null"));
		}
		if (this.serverCapabilities.tools() == null) {
			return Mono.error(new McpError("Server must be configured with tool capabilities"));
		}

		boolean removed = this.tools.removeIf(toolRegistration -> toolRegistration.tool().name().equals(toolName));
		if (removed) {
			logger.info("Removed tool handler: {}", toolName);
			if (this.serverCapabilities.tools().listChanged()) {
				return notifyToolsListChanged();
			}
			return Mono.empty();
		}
		return Mono.error(new McpError("Tool with name '" + toolName + "' not found"));
	}

	/**
	 * Notifies clients that the list of available tools has changed.
	 * @return A Mono that completes when all clients have been notified
	 */
	public Mono<Void> notifyToolsListChanged() {
		return this.mcpSession.sendNotification("notifications/tools/list_changed", null);
	}

	private DefaultMcpSession.RequestHandler toolsListRequestHandler() {
		return params -> {
			McpSchema.PaginatedRequest request = transport.unmarshalFrom(params,
					new TypeReference<McpSchema.PaginatedRequest>() {
					});

			List<Tool> tools = this.tools.stream().map(toolRegistration -> {
				return toolRegistration.tool();
			}).toList();

			logger.info("Client tools list request - Cursor: {}", request.cursor());
			return Mono.just(new McpSchema.ListToolsResult(tools, null));
		};
	}

	private DefaultMcpSession.RequestHandler toolsCallRequestHandler() {
		return params -> {
			McpSchema.CallToolRequest callToolRequest = transport.unmarshalFrom(params,
					new TypeReference<McpSchema.CallToolRequest>() {
					});

			Optional<ToolRegistration> toolRegistration = this.tools.stream()
				.filter(tr -> callToolRequest.name().equals(tr.tool().name()))
				.findAny();

			if (toolRegistration.isEmpty()) {
				return Mono.<Object>error(new McpError("Tool not found: " + callToolRequest.name()));
			}

			CallToolResult callResponse = toolRegistration.get().call().apply(callToolRequest.arguments());

			return Mono.just(callResponse);
		};
	}

	// ---------------------------------------
	// Resource Management
	// ---------------------------------------

	/**
	 * Add a new resource handler at runtime.
	 * @param resourceHandler The resource handler to add
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> addResource(ResourceRegistration resourceHandler) {
		if (resourceHandler == null || resourceHandler.resource() == null) {
			return Mono.error(new McpError("Resource must not be null"));
		}

		if (this.serverCapabilities.resources() == null) {
			return Mono.error(new McpError("Server must be configured with resource capabilities"));
		}

		if (this.resources.containsKey(resourceHandler.resource().uri())) {
			return Mono
				.error(new McpError("Resource with URI '" + resourceHandler.resource().uri() + "' already exists"));
		}

		this.resources.put(resourceHandler.resource().uri(), resourceHandler);
		logger.info("Added resource handler: {}", resourceHandler.resource().uri());
		if (this.serverCapabilities.resources().listChanged()) {
			return notifyResourcesListChanged();
		}
		return Mono.empty();
	}

	/**
	 * Remove a resource handler at runtime.
	 * @param resourceUri The URI of the resource handler to remove
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> removeResource(String resourceUri) {
		if (resourceUri == null) {
			return Mono.error(new McpError("Resource URI must not be null"));
		}
		if (this.serverCapabilities.resources() == null) {
			return Mono.error(new McpError("Server must be configured with resource capabilities"));
		}

		ResourceRegistration removed = this.resources.remove(resourceUri);
		if (removed != null) {
			logger.info("Removed resource handler: {}", resourceUri);
			if (this.serverCapabilities.resources().listChanged()) {
				return notifyResourcesListChanged();
			}
			return Mono.empty();
		}
		return Mono.error(new McpError("Resource with URI '" + resourceUri + "' not found"));
	}

	/**
	 * Notifies clients that the list of available resources has changed.
	 * @return A Mono that completes when all clients have been notified
	 */
	public Mono<Void> notifyResourcesListChanged() {
		return this.mcpSession.sendNotification("notifications/resources/list_changed", null);
	}

	private DefaultMcpSession.RequestHandler resourcesListRequestHandler() {
		return params -> {
			var resourceList = this.resources.values().stream().map(ResourceRegistration::resource).toList();
			return Mono.just(new McpSchema.ListResourcesResult(resourceList, null));
		};
	}

	private DefaultMcpSession.RequestHandler resourceTemplateListRequestHandler() {
		return params -> Mono.just(new McpSchema.ListResourceTemplatesResult(this.resourceTemplates, null));

	}

	private DefaultMcpSession.RequestHandler resourcesReadRequestHandler() {
		return params -> {
			McpSchema.ReadResourceRequest resourceRequest = transport.unmarshalFrom(params,
					new TypeReference<McpSchema.ReadResourceRequest>() {
					});
			var resourceUri = resourceRequest.uri();
			if (this.resources.containsKey(resourceUri)) {
				return Mono.just(this.resources.get(resourceUri).readHandler().apply(resourceRequest));
			}
			return Mono.error(new McpError("Resource not found: " + resourceUri));
		};
	}

	// ---------------------------------------
	// Prompt Management
	// ---------------------------------------

	/**
	 * Add a new prompt handler at runtime.
	 * @param promptRegistration The prompt handler to add
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> addPrompt(PromptRegistration promptRegistration) {
		if (promptRegistration == null) {
			return Mono.error(new McpError("Prompt registration must not be null"));
		}
		if (this.serverCapabilities.prompts() == null) {
			return Mono.error(new McpError("Server must be configured with prompt capabilities"));
		}

		if (this.prompts.containsKey(promptRegistration.propmpt().name())) {
			return Mono
				.error(new McpError("Prompt with name '" + promptRegistration.propmpt().name() + "' already exists"));
		}

		this.prompts.put(promptRegistration.propmpt().name(), promptRegistration);

		logger.info("Added prompt handler: {}", promptRegistration.propmpt().name());

		// Servers that declared the listChanged capability SHOULD send a notification,
		// when the list of available prompts changes
		if (this.serverCapabilities.prompts().listChanged()) {
			return notifyPromptsListChanged();
		}
		return Mono.empty();
	}

	/**
	 * Remove a prompt handler at runtime.
	 * @param promptName The name of the prompt handler to remove
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> removePrompt(String promptName) {
		if (promptName == null) {
			return Mono.error(new McpError("Prompt name must not be null"));
		}
		if (this.serverCapabilities.prompts() == null) {
			return Mono.error(new McpError("Server must be configured with prompt capabilities"));
		}

		PromptRegistration removed = this.prompts.remove(promptName);

		if (removed != null) {
			logger.info("Removed prompt handler: {}", promptName);
			// Servers that declared the listChanged capability SHOULD send a
			// notification, when the list of available prompts changes
			if (this.serverCapabilities.prompts().listChanged()) {
				return this.notifyPromptsListChanged();
			}
			return Mono.empty();
		}
		return Mono.error(new McpError("Prompt with name '" + promptName + "' not found"));
	}

	/**
	 * Notifies clients that the list of available prompts has changed.
	 * @return A Mono that completes when all clients have been notified
	 */
	public Mono<Void> notifyPromptsListChanged() {
		return this.mcpSession.sendNotification("notifications/prompts/list_changed", null);
	}

	private DefaultMcpSession.RequestHandler promptsListRequestHandler() {
		return params -> {
			McpSchema.PaginatedRequest request = transport.unmarshalFrom(params,
					new TypeReference<McpSchema.PaginatedRequest>() {
					});

			var promptList = this.prompts.values().stream().map(PromptRegistration::propmpt).toList();

			return Mono.just(new McpSchema.ListPromptsResult(promptList, null));
		};
	}

	private DefaultMcpSession.RequestHandler promptsGetRequestHandler() {
		return params -> {
			McpSchema.GetPromptRequest promptRequest = transport.unmarshalFrom(params,
					new TypeReference<McpSchema.GetPromptRequest>() {
					});

			// Implement prompt retrieval logic here
			if (this.prompts.containsKey(promptRequest.name())) {
				return Mono.just(this.prompts.get(promptRequest.name()).promptHandler().apply(promptRequest));
			}

			return Mono.error(new McpError("Prompt not found: " + promptRequest.name()));
		};
	}

	// ---------------------------------------
	// Logging Management
	// ---------------------------------------

	/**
	 * Send a logging message notification to all connected clients. Messages below the
	 * current minimum logging level will be filtered out.
	 * @param loggingMessageNotification The logging message to send
	 * @return A Mono that completes when the notification has been sent
	 */
	public Mono<Void> loggingNotification(LoggingMessageNotification loggingMessageNotification) {

		if (loggingMessageNotification == null) {
			return Mono.error(new McpError("Logging message must not be null"));
		}

		Map<String, Object> params = this.transport.unmarshalFrom(loggingMessageNotification,
				new TypeReference<Map<String, Object>>() {
				});

		if (loggingMessageNotification.level().level() < minLoggingLevel.level()) {
			return Mono.empty();
		}

		return this.mcpSession.sendNotification("notifications/message", params);
	}

	/**
	 * Handles requests to set the minimum logging level. Messages below this level will
	 * not be sent.
	 * @return A handler that processes logging level change requests
	 */
	private DefaultMcpSession.RequestHandler setLoggerRequestHandler() {
		return params -> {
			McpSchema.LoggingLevel setLoggerRequest = transport.unmarshalFrom(params,
					new TypeReference<McpSchema.LoggingLevel>() {
					});

			this.minLoggingLevel = setLoggerRequest;

			return Mono.empty();
		};
	}

}
