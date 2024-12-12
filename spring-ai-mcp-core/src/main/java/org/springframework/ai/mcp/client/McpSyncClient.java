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

import org.springframework.ai.mcp.client.util.Assert;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptRequest;
import org.springframework.ai.mcp.spec.McpSchema.GetPromptResult;
import org.springframework.ai.mcp.spec.McpSchema.ListPromptsResult;

/**
 * @author Dariusz Jędrzejczyk
 * @author Christian Tzolov
 */
public class McpSyncClient implements AutoCloseable {

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
		this.delegate.closeGracefully().block(Duration.ofMillis(DEFAULT_CLOSE_TIMEOUT_MS));
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
	public void sendRootsListChanged() {
		this.delegate.sendRootsListChanged().block();
	}

	/**
	 * Send a synchronous ping request.
	 */
	public void ping() {
		this.delegate.ping().block();
	}

	// --------------------------
	// Tools
	// --------------------------
	/**
	 * Send a tools/call request.
	 * @param callToolRequest the call tool request.
	 * @return the call tool result.
	 */
	public McpSchema.CallToolResult callTool(McpSchema.CallToolRequest callToolRequest) {
		return this.delegate.callTool(callToolRequest).block();
	}

	/**
	 * Send a tools/list request.
	 * @return the list of tools result.
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

}
