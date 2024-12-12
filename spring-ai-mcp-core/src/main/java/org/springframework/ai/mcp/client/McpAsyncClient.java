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
package org.springframework.ai.mcp.client;

import java.time.Duration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.spec.DefaultMcpSession;
import org.springframework.ai.mcp.spec.McpError;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptRequest;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptResult;
import org.springframework.ai.mcp.spec.McpSchema.ListPromptsResult;
import org.springframework.ai.mcp.spec.McpSchema.PaginatedRequest;
import org.springframework.ai.mcp.spec.McpTransport;

/**
 * @author Dariusz Jędrzejczyk
 * @author Christian Tzolov
 */
public class McpAsyncClient extends DefaultMcpSession {

	private static TypeReference<Void> VOID_TYPE_REFERENCE = new TypeReference<Void>() {
	};

	public McpAsyncClient(McpTransport transport) {
		this(transport, Duration.ofSeconds(10), new ObjectMapper());
	}

	public McpAsyncClient(McpTransport transport, Duration requestTimeout, ObjectMapper objectMapper) {
		super(requestTimeout, objectMapper, transport);
	}

	/**
	 * The initialization phase MUST be the first interaction between client and server.
	 * During this phase, the client and server:
	 * <ul>
	 * <li>Establish protocol version compatibility</li>
	 * <li>Exchange and negotiate capabilities</li>
	 * <li>Share implementation details</li>
	 * </ul>
	 * <br/>
	 * The client MUST initiate this phase by sending an initialize request containing:
	 * <ul>
	 * <li>The protocol version the client supports</li>
	 * <li>The client's capabilities</li>
	 * <li>Client implementation information</li>
	 * </ul>
	 *
	 * The server MUST respond with its own capabilities and information:
	 * {@link McpSchema.ServerCapabilities}. <br/>
	 * After successful initialization, the client MUST send an initialized notification
	 * to indicate it is ready to begin normal operations.
	 *
	 * <br/>
	 *
	 * <a href=
	 * "https://github.com/modelcontextprotocol/specification/blob/main/docs/specification/basic/lifecycle.md#initialization">Initialization
	 * Spec</a>
	 * @return the initialize result.
	 */
	public Mono<McpSchema.InitializeResult> initialize() {
		McpSchema.InitializeRequest initializeRequest = new McpSchema.InitializeRequest(// @formatter:off
				McpSchema.LATEST_PROTOCOL_VERSION,
				new McpSchema.ClientCapabilities(null, new McpSchema.ClientCapabilities.RootCapabilities(true), null),
				new McpSchema.Implementation("mcp-java-client", "0.0.1")); // @formatter:on

		Mono<McpSchema.InitializeResult> result = this.sendRequest("initialize", initializeRequest,
				new TypeReference<McpSchema.InitializeResult>() {
				});

		return result.flatMap(initializeResult -> {
			if (!McpSchema.LATEST_PROTOCOL_VERSION.equals(initializeResult.protocolVersion())) {
				return Mono.error(new McpError(
						"Unsupported protocol version from the server: " + initializeResult.protocolVersion()));
			}
			else {
				return this.sendNotification("notifications/initialized", null).thenReturn(initializeResult);
			}
		});
	}

	/**
	 * Send a roots/list_changed notification.
	 */
	public Mono<Void> sendRootsListChanged() {
		return this.sendNotification("notifications/roots/list_changed");
	}

	/**
	 * Send a synchronous ping request.
	 */
	public Mono<Void> ping() {
		return this.sendRequest("ping", null, VOID_TYPE_REFERENCE);
	}

	// --------------------------
	// Tools
	// --------------------------
	private static TypeReference<McpSchema.CallToolResult> CALL_TOOL_RESULT_TYPE_REF = new TypeReference<McpSchema.CallToolResult>() {
	};

	private static TypeReference<McpSchema.ListToolsResult> LIST_TOOLS_RESULT_TYPE_REF = new TypeReference<McpSchema.ListToolsResult>() {
	};

	/**
	 * Send a tools/call request.
	 * @param callToolRequest the call tool request.
	 * @return the call tool result.
	 */
	public Mono<McpSchema.CallToolResult> callTool(McpSchema.CallToolRequest callToolRequest) {
		return this.sendRequest("tools/call", callToolRequest, CALL_TOOL_RESULT_TYPE_REF);
	}

	/**
	 * Send a tools/list request.
	 * @return the list of tools result.
	 */
	public Mono<McpSchema.ListToolsResult> listTools() {
		return this.listTools(null);
	}

	/**
	 * Send a tools/list request.
	 * @param cursor the cursor
	 * @return the list of tools result.
	 */
	public Mono<McpSchema.ListToolsResult> listTools(String cursor) {
		return this.sendRequest("tools/list", new McpSchema.PaginatedRequest(cursor), LIST_TOOLS_RESULT_TYPE_REF);
	}

	// --------------------------
	// Resources
	// --------------------------

	private static TypeReference<McpSchema.ListResourcesResult> LIST_RESOURCES_RESULT_TYPE_REF = new TypeReference<McpSchema.ListResourcesResult>() {
	};

	private static TypeReference<McpSchema.ReadResourceResult> READ_RESOURCE_RESULT_TYPE_REF = new TypeReference<McpSchema.ReadResourceResult>() {
	};

	private static TypeReference<McpSchema.ListResourceTemplatesResult> LIST_RESOURCE_TEMPLATES_RESULT_TYPE_REF = new TypeReference<McpSchema.ListResourceTemplatesResult>() {
	};

	/**
	 * Send a resources/list request.
	 * @return the list of resources result.
	 */
	public Mono<McpSchema.ListResourcesResult> listResources() {
		return this.listResources(null);
	}

	/**
	 * Send a resources/list request.
	 * @param cursor the cursor
	 * @return the list of resources result.
	 */
	public Mono<McpSchema.ListResourcesResult> listResources(String cursor) {
		return this.sendRequest("resources/list", new McpSchema.PaginatedRequest(cursor),
				LIST_RESOURCES_RESULT_TYPE_REF);
	}

	/**
	 * Send a resources/read request.
	 * @param resource the resource to read
	 * @return the resource content.
	 */
	public Mono<McpSchema.ReadResourceResult> readResource(McpSchema.Resource resource) {
		return this.readResource(new McpSchema.ReadResourceRequest(resource.uri()));
	}

	/**
	 * Send a resources/read request.
	 * @param readResourceRequest the read resource request.
	 * @return the resource content.
	 */
	public Mono<McpSchema.ReadResourceResult> readResource(McpSchema.ReadResourceRequest readResourceRequest) {
		return this.sendRequest("resources/read", readResourceRequest, READ_RESOURCE_RESULT_TYPE_REF);
	}

	/**
	 * Resource templates allow servers to expose parameterized resources using URI
	 * templates. Arguments may be auto-completed through the completion API.
	 *
	 * Request a list of resource templates the server has.
	 * @return the list of resource templates result.
	 */
	public Mono<McpSchema.ListResourceTemplatesResult> listResourceTemplates() {
		return this.listResourceTemplates(null);
	}

	/**
	 * Resource templates allow servers to expose parameterized resources using URI
	 * templates. Arguments may be auto-completed through the completion API.
	 *
	 * Request a list of resource templates the server has.
	 * @param cursor the cursor
	 * @return the list of resource templates result.
	 */
	public Mono<McpSchema.ListResourceTemplatesResult> listResourceTemplates(String cursor) {
		return this.sendRequest("resources/templates/list", new McpSchema.PaginatedRequest(cursor),
				LIST_RESOURCE_TEMPLATES_RESULT_TYPE_REF);
	}

	/**
	 * List Changed Notification. When the list of available resources changes, servers
	 * that declared the listChanged capability SHOULD send a notification:
	 */
	public Mono<Void> sendResourcesListChanged() {
		return this.sendNotification("notifications/resources/list_changed");
	}

	/**
	 * Subscriptions. The protocol supports optional subscriptions to resource changes.
	 * Clients can subscribe to specific resources and receive notifications when they
	 * change.
	 *
	 * Send a resources/subscribe request.
	 * @param subscribeRequest the subscribe request contains the uri of the resource to
	 * subscribe to.
	 */
	public Mono<Void> subscribeResource(McpSchema.SubscribeRequest subscribeRequest) {
		return this.sendRequest("resources/subscribe", subscribeRequest, VOID_TYPE_REFERENCE);
	}

	/**
	 * Send a resources/unsubscribe request.
	 * @param unsubscribeRequest the unsubscribe request contains the uri of the resource
	 * to unsubscribe from.
	 */
	public Mono<Void> unsubscribeResource(McpSchema.UnsubscribeRequest unsubscribeRequest) {
		return this.sendRequest("resources/unsubscribe", unsubscribeRequest, VOID_TYPE_REFERENCE);
	}

	// --------------------------
	// Prompts
	// --------------------------
	private static TypeReference<McpSchema.ListPromptsResult> LIST_PROMPTS_RESULT_TYPE_REF = new TypeReference<McpSchema.ListPromptsResult>() {
	};

	private static TypeReference<McpSchema.GetPromptResult> GET_PROMPT_RESULT_TYPE_REF = new TypeReference<McpSchema.GetPromptResult>() {
	};

	/**
	 * List all available prompts.
	 * @return the list of prompts result.
	 */
	public Mono<ListPromptsResult> listPrompts() {
		return this.listPrompts(null);
	}

	/**
	 * List all available prompts.
	 * @param cursor the cursor
	 * @return the list of prompts result.
	 */
	public Mono<ListPromptsResult> listPrompts(String cursor) {
		return this.sendRequest("prompts/list", new PaginatedRequest(cursor), LIST_PROMPTS_RESULT_TYPE_REF);
	}

	/**
	 * Get a prompt by its id.
	 * @param getPromptRequest the get prompt request.
	 * @return the get prompt result.
	 */
	public Mono<GetPromptResult> getPrompt(GetPromptRequest getPromptRequest) {
		return this.sendRequest("prompts/get", getPromptRequest, GET_PROMPT_RESULT_TYPE_REF);
	}

	/**
	 * (Server) An optional notification from the server to the client, informing it that
	 * the list of prompts it offers has changed. This may be issued by servers without
	 * any previous subscription from the client.
	 */
	public Mono<Void> promptListChangedNotification() {
		return this.sendNotification("notifications/prompts/list_changed");
	}

}
