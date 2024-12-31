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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptRequest;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptResult;
import org.springframework.ai.mcp.spec.McpSchema.ListPromptsResult;
import org.springframework.ai.mcp.util.Assert;

/**
 * A synchronous client for the Model Context Protocol (MCP).
 *
 * @author Dariusz Jędrzejczyk
 * @author Christian Tzolov
 */
public class McpSyncClient implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(McpSyncClient.class);

	// TODO: Consider providing a client config to set this properly
	// this is currently a concern only because AutoCloseable is used - perhaps it
	// is not a requirement?
	private static final long DEFAULT_CLOSE_TIMEOUT_MS = 10_000L;

	private final McpAsyncClient delegate;

	public McpSyncClient(McpAsyncClient delegate) {
		Assert.notNull(delegate, "The delegate can not be null");
		this.delegate = delegate;
	}

	@Override
	public void close() {
		this.delegate.close();
	}

	public boolean closeGracefully() {
		try {
			this.delegate.closeGracefully().block(Duration.ofMillis(DEFAULT_CLOSE_TIMEOUT_MS));
		}
		catch (RuntimeException e) {
			logger.warn("Client didn't close within timeout of {} ms.", DEFAULT_CLOSE_TIMEOUT_MS, e);
			return false;
		}
		return true;
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
	public McpSchema.InitializeResult initialize() {
		// TODO: block takes no argument here as we assume the async client is
		// configured with a requestTimeout at all times
		return this.delegate.initialize().block();
	}

	/**
	 * Send a roots/list_changed notification.
	 */
	public void rootsListChangedNotification() {
		this.delegate.rootsListChangedNotification().block();
	}

	/**
	 * Add a roots dynamically.
	 */
	public void addRoot(McpSchema.Root root) {
		this.delegate.addRoot(root).block();
	}

	/**
	 * Remove a root dynamically.
	 */
	public void removeRoot(String rootUri) {
		this.delegate.removeRoot(rootUri).block();
	}

	/**
	 * Send a synchronous ping request.
	 * @return
	 */
	public Object ping() {
		return this.delegate.ping().block();
	}

	// --------------------------
	// Tools
	// --------------------------
	/**
	 * Calls a tool provided by the server. Tools enable servers to expose executable
	 * functionality that can interact with external systems, perform computations, and
	 * take actions in the real world.
	 * @param callToolRequest The request containing: - name: The name of the tool to call
	 * (must match a tool name from tools/list) - arguments: Arguments that conform to the
	 * tool's input schema
	 * @return The tool execution result containing: - content: List of content items
	 * (text, images, or embedded resources) representing the tool's output - isError:
	 * Boolean indicating if the execution failed (true) or succeeded (false/absent)
	 */
	public McpSchema.CallToolResult callTool(McpSchema.CallToolRequest callToolRequest) {
		return this.delegate.callTool(callToolRequest).block();
	}

	/**
	 * Retrieves the list of all tools provided by the server.
	 * @return The list of tools result containing: - tools: List of available tools, each
	 * with a name, description, and input schema - nextCursor: Optional cursor for
	 * pagination if more tools are available
	 */
	public McpSchema.ListToolsResult listTools() {
		return this.delegate.listTools().block();
	}

	/**
	 * Retrieves a paginated list of tools provided by the server.
	 * @param cursor Optional pagination cursor from a previous list request
	 * @return The list of tools result containing: - tools: List of available tools, each
	 * with a name, description, and input schema - nextCursor: Optional cursor for
	 * pagination if more tools are available
	 */
	public McpSchema.ListToolsResult listTools(String cursor) {
		return this.delegate.listTools(cursor).block();
	}

	// --------------------------
	// Resources
	// --------------------------

	/**
	 * Send a resources/list request.
	 * @param cursor the cursor
	 * @return the list of resources result.
	 */
	public McpSchema.ListResourcesResult listResources(String cursor) {
		return this.delegate.listResources(cursor).block();
	}

	/**
	 * Send a resources/list request.
	 * @return the list of resources result.
	 */
	public McpSchema.ListResourcesResult listResources() {
		return this.delegate.listResources().block();
	}

	/**
	 * Send a resources/read request.
	 * @param resource the resource to read
	 * @return the resource content.
	 */
	public McpSchema.ReadResourceResult readResource(McpSchema.Resource resource) {
		return this.delegate.readResource(resource).block();
	}

	/**
	 * Send a resources/read request.
	 * @param readResourceRequest the read resource request.
	 * @return the resource content.
	 */
	public McpSchema.ReadResourceResult readResource(McpSchema.ReadResourceRequest readResourceRequest) {
		return this.delegate.readResource(readResourceRequest).block();
	}

	/**
	 * Resource templates allow servers to expose parameterized resources using URI
	 * templates. Arguments may be auto-completed through the completion API.
	 *
	 * Request a list of resource templates the server has.
	 * @param cursor the cursor
	 * @return the list of resource templates result.
	 */
	public McpSchema.ListResourceTemplatesResult listResourceTemplates(String cursor) {
		return this.delegate.listResourceTemplates(cursor).block();
	}

	/**
	 * Request a list of resource templates the server has.
	 * @return the list of resource templates result.
	 */
	public McpSchema.ListResourceTemplatesResult listResourceTemplates() {
		return this.delegate.listResourceTemplates().block();
	}

	/**
	 * List Changed Notification. When the list of available resources changes, servers
	 * that declared the listChanged capability SHOULD send a notification:
	 */
	public void sendResourcesListChanged() {
		this.delegate.sendResourcesListChanged().block();
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
	public void subscribeResource(McpSchema.SubscribeRequest subscribeRequest) {
		this.delegate.subscribeResource(subscribeRequest).block();
	}

	/**
	 * Send a resources/unsubscribe request.
	 * @param unsubscribeRequest the unsubscribe request contains the uri of the resource
	 * to unsubscribe from.
	 */
	public void unsubscribeResource(McpSchema.UnsubscribeRequest unsubscribeRequest) {
		this.delegate.unsubscribeResource(unsubscribeRequest).block();
	}

	// --------------------------
	// Prompts
	// --------------------------
	public ListPromptsResult listPrompts(String cursor) {
		return this.delegate.listPrompts(cursor).block();
	}

	public ListPromptsResult listPrompts() {
		return this.delegate.listPrompts().block();
	}

	public GetPromptResult getPrompt(GetPromptRequest getPromptRequest) {
		return this.delegate.getPrompt(getPromptRequest).block();
	}

	/**
	 * (Server) An optional notification from the server to the client, informing it that
	 * the list of prompts it offers has changed. This may be issued by servers without
	 * any previous subscription from the client.
	 */
	public void promptListChangedNotification() {
		this.delegate.promptListChangedNotification().block();
	}

	/**
	 * Client can set the minimum logging level it wants to receive from the server.
	 * @param loggingLevel the min logging level
	 */
	public void setLoggingLevel(McpSchema.LoggingLevel loggingLevel) {
		this.delegate.setLoggingLevel(loggingLevel).block();
	}

}
