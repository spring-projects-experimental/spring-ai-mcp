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

import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.server.McpServer.PromptRegistration;
import org.springframework.ai.mcp.server.McpServer.ResourceRegistration;
import org.springframework.ai.mcp.server.McpServer.ToolRegistration;
import org.springframework.ai.mcp.spec.McpSchema.LoggingMessageNotification;
import org.springframework.ai.mcp.util.Assert;

/**
 * Synchronous wrapper around {@link McpAsyncServer} that provides blocking operations.
 * This class delegates all operations to an underlying async server instance while
 * providing a synchronous API.
 *
 * @author Christian Tzolov
 */
public class McpSyncServer {

	/**
	 * The async server to wrap.
	 */
	private final McpAsyncServer asyncServer;

	/**
	 * Creates a new synchronous server that wraps the provided async server.
	 * @param asyncServer The async server to wrap
	 */
	public McpSyncServer(McpAsyncServer asyncServer) {
		Assert.notNull(asyncServer, "Async server must not be null");
		this.asyncServer = asyncServer;
	}

	/**
	 * Add a new tool handler.
	 * @param toolHandler The tool handler to add
	 */
	public void addTool(ToolRegistration toolHandler) {
		this.asyncServer.addTool(toolHandler).block();
	}

	/**
	 * Remove a tool handler.
	 * @param toolName The name of the tool handler to remove
	 */
	public void removeTool(String toolName) {
		this.asyncServer.removeTool(toolName).block();
	}

	/**
	 * Add a new resource handler.
	 * @param resourceHandler The resource handler to add
	 */
	public void addResource(ResourceRegistration resourceHandler) {
		this.asyncServer.addResource(resourceHandler).block();
	}

	/**
	 * Remove a resource handler.
	 * @param resourceUri The URI of the resource handler to remove
	 */
	public void removeResource(String resourceUri) {
		this.asyncServer.removeResource(resourceUri).block();
	}

	/**
	 * Add a new prompt handler.
	 * @param promptRegistration The prompt registration to add
	 */
	public void addPrompt(PromptRegistration promptRegistration) {
		this.asyncServer.addPrompt(promptRegistration).block();
	}

	/**
	 * Remove a prompt handler.
	 * @param promptName The name of the prompt handler to remove
	 */
	public void removePrompt(String promptName) {
		this.asyncServer.removePrompt(promptName).block();
	}

	/**
	 * Notify clients that the list of available tools has changed.
	 */
	public void notifyToolsListChanged() {
		this.asyncServer.notifyToolsListChanged().block();
	}

	/**
	 * Notify clients that the list of available resources has changed.
	 */
	public void notifyResourcesListChanged() {
		this.asyncServer.notifyResourcesListChanged().block();
	}

	/**
	 * Notify clients that the list of available prompts has changed.
	 */
	public void notifyPromptsListChanged() {
		this.asyncServer.notifyPromptsListChanged().block();
	}

	/**
	 * Send a logging message notification to all clients.
	 * @param loggingMessageNotification The logging message notification to send
	 */
	public void loggingNotification(LoggingMessageNotification loggingMessageNotification) {
		this.asyncServer.loggingNotification(loggingMessageNotification).block();
	}

	/**
	 * Close the server gracefully.
	 */
	public void closeGracefully() {
		this.asyncServer.closeGracefully().block();
	}

	/**
	 * Close the server immediately.
	 */
	public void close() {
		this.asyncServer.close();
	}

	/**
	 * Get the underlying async server instance.
	 * @return The wrapped async server
	 */
	public McpAsyncServer getAsyncServer() {
		return this.asyncServer;
	}

}
