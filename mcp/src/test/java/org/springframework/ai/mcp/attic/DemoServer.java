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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.netty.http.server.HttpServer;

import org.springframework.ai.mcp.server.McpServer;
import org.springframework.ai.mcp.server.transport.SseServerTransport;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.CallToolResult;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunctions;

/**
 *
 * https://docs.spring.io/spring-framework/reference/web/webflux-functional.html#webflux-fn-running
 * https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html#webflux-httphandler
 *
 * @author Christian Tzolov
 */

public class DemoServer {

	public static void main(String[] args) {
		SseServerTransport transport = new SseServerTransport(new ObjectMapper(), "/mcp/message");

		var mcpServer = McpServer.using(transport)
			.serverInfo("Weather Forecast", "1.0.0")
			.tool(new McpSchema.Tool("weather", "Weather forecast tool by location", "{ \"type\": \"object\" }"),
					(arguments) -> {
						String city = (String) arguments.get("city");
						return new CallToolResult(List.of(), false);
					})
			.async();

		HttpHandler httpHandler = RouterFunctions.toHttpHandler(transport.getRouterFunction());
		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		HttpServer httpServer = HttpServer.create().port(8080).handle(adapter);
		httpServer.bindNow().onDispose().block();

	}

}
