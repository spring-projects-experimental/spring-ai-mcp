/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.mcp.attic;

import java.time.Duration;
import java.util.Map;

import org.springframework.ai.mcp.client.McpClient;
import org.springframework.ai.mcp.client.McpSyncClient;
import org.springframework.ai.mcp.client.transport.SseClientTransport;
import org.springframework.ai.mcp.spec.McpSchema.CallToolRequest;
import org.springframework.ai.mcp.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.spec.McpSchema.ListPromptsResult;
import org.springframework.ai.mcp.spec.McpSchema.ListResourcesResult;
import org.springframework.ai.mcp.spec.McpSchema.ListToolsResult;
import org.springframework.ai.mcp.spec.McpSchema.Resource;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class MainSSE {

	public static void main(String[] args) {

		try (McpSyncClient mcpClient = McpClient
			.using(new SseClientTransport(WebClient.builder().baseUrl("http://localhost:3001")))
			.requestTimeout(Duration.ofSeconds(1000))
			.sync()) {

			mcpClient.initialize();

			ListToolsResult tools = mcpClient.listTools();
			System.out.println("Tools: " + tools);

			mcpClient.ping();

			CallToolRequest callToolRequest = new CallToolRequest("echo", Map.of("message", "Hello MCP Spring AI!"));
			CallToolResult callToolResult = mcpClient.callTool(callToolRequest);
			System.out.println("Call Tool Result: " + callToolResult);

			// mcpClient.sendRootsListChanged();

			// Resources
			ListResourcesResult resources = mcpClient.listResources();
			System.out.println("Resources Size: " + resources.resources().size());
			System.out.println("Resources: " + resources);
			for (Resource resource : resources.resources()) {
				System.out.println(mcpClient.readResource(resource));
			}

			var resourceTemplate = mcpClient.listResourceTemplates();
			System.out.println("Resource Templates: " + resourceTemplate);

			ListPromptsResult prompts = mcpClient.listPrompts();
			for (var prompt : prompts.prompts()) {
				System.out.println("Prompt: " + prompt);
			}

		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}
