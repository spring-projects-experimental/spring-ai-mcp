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
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.mcp.client.util.Assert;
import org.springframework.ai.mcp.spec.AbstractMcpTransport;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCMessage;

/**
 * Implementation of the MCP Stdio transport that communicates with a server process using
 * standard input/output streams. Messages are exchanged as newline-delimited JSON-RPC
 * messages over stdin/stdout, with errors and debug information sent to stderr.
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */
public class StdioServerTransport extends AbstractMcpTransport {

	private static final Logger logger = LoggerFactory.getLogger(StdioServerTransport.class);

	/** The server process being communicated with */
	private Process process;

	/** Scheduler for handling inbound messages from the server process */
	private Scheduler inboundScheduler;

	/** Scheduler for handling outbound messages to the server process */
	private Scheduler outboundScheduler;

	/** Scheduler for handling error messages from the server process */
	private Scheduler errorScheduler;

	/** Parameters for configuring and starting the server process */
	private final ServerParameters params;

	/**
	 * Creates a new StdioServerTransport with the specified parameters and default
	 * ObjectMapper.
	 * @param params The parameters for configuring the server process
	 */
	public StdioServerTransport(ServerParameters params) {
		this(params, new ObjectMapper());
	}

	/**
	 * Creates a new StdioServerTransport with the specified parameters and ObjectMapper.
	 * @param params The parameters for configuring the server process
	 * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
	 */
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

	/**
	 * Starts the server process and initializes the message processing streams. This
	 * method sets up the process with the configured command, arguments, and environment,
	 * then starts the inbound, outbound, and error processing threads.
	 * @throws RuntimeException if the process fails to start or if the process streams
	 * are null
	 */
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

		// Start threads
		startInboundProcessing();
		startOutboundProcessing();
		startErrorProcessing();
	}

	/**
	 * Creates and returns a new ProcessBuilder instance. Protected to allow overriding in
	 * tests.
	 * @return A new ProcessBuilder instance
	 */
	protected ProcessBuilder getProcessBuilder() {
		return new ProcessBuilder();
	}

	/**
	 * Waits for the server process to exit.
	 * @throws RuntimeException if the process is interrupted while waiting
	 */
	public void awaitForExit() {
		try {
			this.process.waitFor();
		}
		catch (InterruptedException e) {
			throw new RuntimeException("Process interrupted", e);
		}
	}

	/**
	 * Starts the error processing thread that reads from the process's error stream.
	 * Error messages are logged and emitted to the error sink.
	 */
	private void startErrorProcessing() {
		this.errorScheduler.schedule(() -> {
			try (BufferedReader processErrorReader = new BufferedReader(
					new InputStreamReader(process.getErrorStream()))) {
				String line;
				while ((line = processErrorReader.readLine()) != null) {
					try {
						logger.error("Received error line: {}", line);
						// TODO: handle errors, etc.
						this.getErrorSink().tryEmitNext(line);
					}
					catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	/**
	 * Starts the inbound processing thread that reads JSON-RPC messages from the
	 * process's input stream. Messages are deserialized and emitted to the inbound sink.
	 */
	private void startInboundProcessing() {
		this.inboundScheduler.schedule(() -> {
			try (BufferedReader processReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = processReader.readLine()) != null) {
					try {
						JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.objectMapper, line);
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
				throw new RuntimeException(e);
			}
		});
	}

	/**
	 * Starts the outbound processing thread that writes JSON-RPC messages to the
	 * process's output stream. Messages are serialized to JSON and written with a newline
	 * delimiter.
	 */
	private void startOutboundProcessing() {
		this.getOutboundSink()
			.asFlux()
			// this bit is important since writes come from user threads and we
			// want to ensure that the actual writing happens on a dedicated thread
			.publishOn(outboundScheduler)
			.handle((message, s) -> {
				if (message != null) {
					try {
						String jsonMessage = objectMapper.writeValueAsString(message);
						// Escape any embedded newlines in the JSON message as per MCP
						// spec
						jsonMessage = jsonMessage.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
						this.process.getOutputStream().write(jsonMessage.getBytes(StandardCharsets.UTF_8));
						this.process.getOutputStream().write("\n".getBytes(StandardCharsets.UTF_8));
						this.process.getOutputStream().flush();
						s.next(message);
					}
					catch (IOException e) {
						s.error(new RuntimeException(e));
					}
				}
			})
			.subscribe();
	}

	/**
	 * Gracefully closes the transport by destroying the process and disposing of the
	 * schedulers. This method sends a TERM signal to the process and waits for it to exit
	 * before cleaning up resources.
	 * @return A Mono that completes when the transport is closed
	 */
	@Override
	public Mono<Void> closeGracefully() {
		return Mono.fromFuture(() -> {
			logger.info("Sending TERM to process");
			if (this.process != null) {
				this.process.destroy();
				return process.onExit();
			}
			else {
				return CompletableFuture.failedFuture(new RuntimeException("Process not started"));
			}
		}).doOnNext(process -> {
			if (process.exitValue() != 0) {
				logger.warn("Process terminated with code " + process.exitValue());
			}
		}).then(Mono.fromRunnable(() -> {
			// The Threads are blocked on readLine so disposeGracefully would not
			// interrupt them, therefore we issue an async hard dispose.
			inboundScheduler.dispose();
			errorScheduler.dispose();
			outboundScheduler.dispose();
		})).then().subscribeOn(Schedulers.boundedElastic());
	}

}
