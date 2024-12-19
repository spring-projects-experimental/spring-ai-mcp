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

package org.springframework.ai.mcp.attic;

import java.time.Duration;

import org.springframework.ai.mcp.client.McpClient;
import org.springframework.ai.mcp.client.McpSyncClient;
import org.springframework.ai.mcp.client.stdio.ServerParameters;
import org.springframework.ai.mcp.client.stdio.StdioClientTransport;
import org.springframework.ai.mcp.spec.McpSchema.ListResourcesResult;
import org.springframework.ai.mcp.spec.McpSchema.ListToolsResult;
import org.springframework.ai.mcp.spec.McpSchema.Resource;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class ClientSessionTests2 {

	public static void main(String[] args) {

		var stdioParams = ServerParameters.builder("uv")
			.args("--directory", "dir", "run", "mcp-server-sqlite", "--db-path", "~/test.db")
			.build();

		try (McpSyncClient clientSession = McpClient.using(new StdioClientTransport(stdioParams))
			.requestTimeout(Duration.ofSeconds(10))
			.sync()) {

			clientSession.initialize();

			ListToolsResult tools = clientSession.listTools(null);
			System.out.println("Tools: " + tools);

			clientSession.ping();

			// CallToolRequest callToolRequest = new CallToolRequest("echo",
			// Map.of("message", "Hello MCP Spring AI!"));
			// CallToolResult callToolResult =
			// clientSession.callTool(callToolRequest);
			// System.out.println("Call Tool Result: " + callToolResult);
			// //
			// clientSession.sendRootsListChanged();

			// Resources
			ListResourcesResult resources = clientSession.listResources(null);
			System.out.println("Resources Size: " + resources.resources().size());
			System.out.println("Resources: " + resources);
			for (Resource resource : resources.resources()) {
				System.out.println(clientSession.readResource(resource));

			}

			// var resourceTemplate = clientSession.listResourceTemplates(null);
			// System.out.println("Resource Templates: " + resourceTemplate);

			// stdioClient.awaitForExit();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}
