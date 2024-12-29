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
package org.springframework.ai.mcp.sample.client;

import org.springframework.ai.mcp.client.transport.SseClientTransport;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Christian Tzolov
 */

public class ClientSse {

	public static void main(String[] args) {
		var transport = new SseClientTransport(WebClient.builder().baseUrl("http://localhost:8080"));

		new SampleClient(transport).run();
	}

}