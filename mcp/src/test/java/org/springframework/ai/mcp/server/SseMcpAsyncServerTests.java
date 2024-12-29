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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunctions;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.ai.mcp.server.transport.SseServerTransport;
import org.springframework.ai.mcp.spec.McpTransport;

/**
 * Tests for {@link McpAsyncServer} using {@link SseServerTransport}.
 *
 * @author Christian Tzolov
 */
class SseMcpAsyncServerTests extends AbstractMcpAsyncServerTests {

	private static final int PORT = 8181;

	private static final String MESSAGE_ENDPOINT = "/mcp/message";

	private DisposableServer httpServer;

	@Override
	protected McpTransport createMcpTransport() {
		var transport = new SseServerTransport(new ObjectMapper(), MESSAGE_ENDPOINT);

		HttpHandler httpHandler = RouterFunctions.toHttpHandler(transport.getRouterFunction());
		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		httpServer = HttpServer.create().port(PORT).handle(adapter).bindNow();

		return transport;
	}

	@Override
	protected void onStart() {
	}

	@Override
	protected void onClose() {
		if (httpServer != null) {
			httpServer.disposeNow();
		}
	}

}