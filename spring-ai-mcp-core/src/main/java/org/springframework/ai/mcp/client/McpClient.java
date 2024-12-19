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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.Root;
import org.springframework.ai.mcp.spec.McpTransport;
import org.springframework.ai.mcp.util.Assert;

/**
 * Factory class providing static methods for creating Model Context Protocol (MCP)
 * clients. This class serves as the main entry point for establishing connections with
 * MCP servers, offering both synchronous and asynchronous client implementations.
 *
 * <p>
 * The class provides factory methods to create either:
 * <ul>
 * <li>{@link McpAsyncClient} for non-blocking operations
 * <li>{@link McpSyncClient} for blocking operations
 * </ul>
 *
 * <p>
 * Each client type can be instantiated with default settings or with custom configuration
 * including request timeout and JSON object mapping.
 *
 * <p>
 * Use the builder pattern for flexible client configuration:
 *
 * <pre>{@code
 * McpClient.using(transport)
 * 		.withRequestTimeout(Duration.ofSeconds(5))
 * 		.sync(); // or .async()
 * }</pre>
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */
public class McpClient {

	/**
	 * Private constructor to prevent instantiation as this is a utility class containing
	 * only static factory methods.
	 */
	private McpClient() {
	}

	/**
	 * Start building an MCP client with the specified transport.
	 * @param transport The transport layer implementation for MCP communication
	 * @return A new builder instance
	 */
	public static Builder using(McpTransport transport) {
		return new Builder(transport);
	}

	/**
	 * Builder class for creating MCP clients with custom configuration.
	 */
	public static class Builder {

		private final McpTransport transport;

		private Duration requestTimeout = Duration.ofSeconds(20); // Default timeout

		private boolean rootsListChangedNotification = false;

		private List<Supplier<List<Root>>> rootsListProviders = new ArrayList<>();

		private List<Consumer<List<McpSchema.Tool>>> toolsChangeConsumers = new ArrayList<>();

		private List<Consumer<List<McpSchema.Resource>>> resourcesChangeConsumers = new ArrayList<>();

		private List<Consumer<List<McpSchema.Prompt>>> promptsChangeConsumers = new ArrayList<>();

		private Builder(McpTransport transport) {
			Assert.notNull(transport, "Transport must not be null");
			this.transport = transport;
		}

		/**
		 * Set the request timeout duration.
		 * @param requestTimeout The duration to wait before timing out requests
		 * @return This builder instance
		 */
		public Builder requestTimeout(Duration requestTimeout) {
			Assert.notNull(requestTimeout, "Request timeout must not be null");
			this.requestTimeout = requestTimeout;
			return this;
		}

		public Builder rootsListChangedNotification(boolean rootsListChangedNotification) {
			this.rootsListChangedNotification = rootsListChangedNotification;
			return this;
		}

		public Builder rootsListProvider(Supplier<List<Root>> rootsListProvider) {
			this.rootsListProviders.add(rootsListProvider);
			return this;
		}

		public Builder toolsChangeConsumer(Consumer<List<McpSchema.Tool>> toolsChangeConsumer) {
			this.toolsChangeConsumers.add(toolsChangeConsumer);
			return this;
		}

		public Builder resourcesChangeConsumer(Consumer<List<McpSchema.Resource>> resourcesChangeConsumer) {
			this.resourcesChangeConsumers.add(resourcesChangeConsumer);
			return this;
		}

		public Builder promptsChangeConsumer(Consumer<List<McpSchema.Prompt>> promptsChangeConsumer) {
			this.promptsChangeConsumers.add(promptsChangeConsumer);
			return this;
		}

		/**
		 * Build a synchronous MCP client.
		 * @return A new instance of {@link McpSyncClient}
		 */
		public McpSyncClient sync() {
			return new McpSyncClient(async());
		}

		/**
		 * Build an asynchronous MCP client.
		 * @return A new instance of {@link McpAsyncClient}
		 */
		public McpAsyncClient async() {
			return new McpAsyncClient(transport, requestTimeout, rootsListProviders, rootsListChangedNotification,
					toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers);
		}

	}

	/**
	 * @deprecated Use {@link #using(McpTransport)} instead.
	 */
	@Deprecated
	public static McpAsyncClient async(McpTransport transport) {
		return using(transport).async();
	}

	/**
	 * @deprecated Use {@link #using(McpTransport)} instead.
	 */
	@Deprecated
	public static McpAsyncClient async(McpTransport transport, Duration requestTimeout) {
		return using(transport).requestTimeout(requestTimeout).async();
	}

	/**
	 * @deprecated Use {@link #using(McpTransport)} instead.
	 */
	@Deprecated
	public static McpSyncClient sync(McpTransport transport) {
		return using(transport).sync();
	}

	/**
	 * @deprecated Use {@link #using(McpTransport)} instead.
	 */
	@Deprecated
	public static McpSyncClient sync(McpTransport transport, Duration requestTimeout) {
		return using(transport).requestTimeout(requestTimeout).sync();
	}

}
