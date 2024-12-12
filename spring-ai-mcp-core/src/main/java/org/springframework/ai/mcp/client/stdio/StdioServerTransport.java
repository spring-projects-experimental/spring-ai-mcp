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

package org.springframework.ai.mcp.client.stdio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.mcp.client.util.Assert;
import org.springframework.ai.mcp.spec.AbstractMcpTransport;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCMessage;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCNotification;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCRequest;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCResponse;

/**
 * Stdio client for communicating with a server process.
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */
public class StdioServerTransport extends AbstractMcpTransport {

	private final static TypeReference<HashMap<String, Object>> MAP_TYPE_REF = new TypeReference<HashMap<String, Object>>() {
	};

	private Process process;

	private BufferedReader processErrorReader;

	private BufferedReader processReader;

	private BufferedWriter processWriter;

	private Scheduler inboundScheduler;

	private Scheduler outboundScheduler;

	private Scheduler errorScheduler;

	volatile boolean isRunning;

	private final ServerParameters params;

	public StdioServerTransport(ServerParameters params) {
		this(params, new ObjectMapper());
	}

	public StdioServerTransport(ServerParameters params, ObjectMapper objectMapper) {

		super(objectMapper);

		Assert.notNull(params, "The params can not be null");
		Assert.notNull(objectMapper, "The ObjectMapper can not be null");

		this.params = params;

		// Start threads
		this.inboundScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "inbound");
		this.outboundScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "outbound");
		this.errorScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "error");
	}

	@Override
	public void start() {
		// Let's kick off the abstraction layer that will consume the logical messages
		// pushed via sinks. The code that follows is actually feeding the sinks with
		// data.
		super.start();

		// Prepare command and environment
		List<String> fullCommand = new ArrayList<>();
		fullCommand.add(params.getCommand());
		fullCommand.addAll(params.getArgs());

		ProcessBuilder processBuilder = this.getProcessBuilder();
		processBuilder.command(fullCommand);
		processBuilder.environment().putAll(params.getEnv());

		// Start the process
		try {
			this.process = processBuilder.start();
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to start process with command: " + fullCommand, e);
		}

		// Validate process streams
		if (this.process.getInputStream() == null || process.getOutputStream() == null) {
			this.process.destroy();
			throw new RuntimeException("Process input or output stream is null");
		}

		// Initialize readers and writers
		this.processErrorReader = this.process.errorReader();
		this.processReader = this.process.inputReader();
		this.processWriter = this.process.outputWriter();

		// Start threads
		this.isRunning = true;
		startInboundProcessing();
		startOutboundProcessing();
		startErrorProcessing();
	}

	protected ProcessBuilder getProcessBuilder() {
		return new ProcessBuilder();
	}

	public void awaitForExit() {
		try {
			this.process.waitFor();
		}
		catch (InterruptedException e) {
			throw new RuntimeException("Process interrupted", e);
		}
	}

	private void startErrorProcessing() {
		this.errorScheduler.schedule(() -> {
			try {
				String line;
				while (isRunning && processErrorReader != null && (line = processErrorReader.readLine()) != null) {
					try {
						System.out.println("Received error line: " + line);
						// TODO: handle errors, etc.
						this.getErrorSink().tryEmitNext(line);
					}
					catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
			catch (IOException e) {
				if (this.isRunning) {
					throw new RuntimeException(e);
				}
			}
			finally {
				this.isRunning = false;
			}
		});
	}

	private void startInboundProcessing() {
		this.inboundScheduler.schedule(() -> {
			try {
				String line;
				while (this.isRunning && this.processReader != null && (line = this.processReader.readLine()) != null) {
					try {
						JSONRPCMessage message = deserializeJsonRpcMessage(line);
						if (!this.getInboundSink().tryEmitNext(message).isSuccess()) {
							// TODO: Back off, reschedule, give up?
							throw new RuntimeException("Failed to enqueue message");
						}
					}
					catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
			catch (IOException e) {
				if (isRunning) {
					throw new RuntimeException(e);
				}
			}
			finally {
				isRunning = false;
			}
		});
	}

	private JSONRPCMessage deserializeJsonRpcMessage(String jsonText) throws IOException {

		var map = this.objectMapper.readValue(jsonText, MAP_TYPE_REF);

		// Determine message type based on specific JSON structure
		if (map.containsKey("method") && map.containsKey("id")) {
			return this.objectMapper.convertValue(map, JSONRPCRequest.class);
		}
		else if (map.containsKey("method") && !map.containsKey("id")) {
			return this.objectMapper.convertValue(map, JSONRPCNotification.class);
		}
		else if (map.containsKey("result") || map.containsKey("error")) {
			return this.objectMapper.convertValue(map, JSONRPCResponse.class);
		}

		throw new IllegalArgumentException("Cannot deserialize JSONRPCMessage: " + jsonText);
	}

	private void startOutboundProcessing() {
		this.getOutboundSink()
			.asFlux()
			// this bit is important since writes come from user threads and we
			// want to ensure that the actual writing happens on a dedicated thread
			.publishOn(outboundScheduler)
			.handle((message, s) -> {
				if (message != null) {
					try {
						this.processWriter.write(objectMapper.writeValueAsString(message));
						this.processWriter.newLine();
						this.processWriter.flush();
						s.next(message);
					}
					catch (IOException e) {
						s.error(new RuntimeException(e));
					}
				}
			})
			.subscribe();
	}

	@Override
	public Mono<Void> closeGracefully() {

		this.isRunning = false;

		return Mono.fromFuture(() -> {
			System.out.println("Sending TERM to process");
			if (this.process != null) {
				this.process.destroy();
				return process.onExit();
			}
			else {
				return CompletableFuture.failedFuture(new RuntimeException("Process not started"));
			}
		}).doOnNext(process -> {
			if (process.exitValue() != 0) {
				System.out.println("Process terminated with code " + process.exitValue());
			}
		}).then(Mono.whenDelayError(Mono.fromRunnable(() -> {
			try {
				this.processErrorReader.close();
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}), Mono.fromRunnable(() -> {
			try {
				this.processReader.close();
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}), Mono.fromRunnable(() -> {
			try {
				this.processWriter.close();
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		})))
			.then(Mono.whenDelayError(inboundScheduler.disposeGracefully(), outboundScheduler.disposeGracefully(),
					errorScheduler.disposeGracefully()))
			.subscribeOn(Schedulers.boundedElastic());
	}

}