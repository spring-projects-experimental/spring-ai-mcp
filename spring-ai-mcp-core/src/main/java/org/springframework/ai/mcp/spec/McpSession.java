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
package org.springframework.ai.mcp.spec;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import reactor.core.publisher.Mono;

/**
 * Represents a Model Control Protocol (MCP) session that handles communication between
 * clients and the server. This interface provides methods for sending requests
 * and notifications, as well as managing the session lifecycle.
 * 
 * <p>The session operates asynchronously using Project Reactor's {@link Mono} type
 * for non-blocking operations. It supports both request-response patterns and
 * one-way notifications.</p>
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */
public interface McpSession {

    /**
     * Sends a request to the model server and expects a response of type T.
     * 
     * <p>This method handles the request-response pattern where a response is expected
     * from the server. The response type is determined by the provided TypeReference.</p>
     *
     * @param <T> the type of the expected response
     * @param method the name of the method to be called on the server
     * @param requestParams the parameters to be sent with the request
     * @param typeRef the TypeReference describing the expected response type
     * @return a Mono that will emit the response when received
     */
    <T> Mono<T> sendRequest(String method, Object requestParams, TypeReference<T> typeRef);

    /**
     * Sends a notification to the model server without parameters.
     * 
     * <p>This method implements the notification pattern where no response is expected
     * from the server. It's useful for fire-and-forget scenarios.</p>
     *
     * @param method the name of the notification method to be called on the server
     * @return a Mono that completes when the notification has been sent
     */
	default Mono<Void> sendNotification(String method) {
		return sendNotification(method, null);
	}

    /**
     * Sends a notification to the model server with parameters.
     * 
     * <p>Similar to {@link #sendNotification(String)} but allows sending additional
     * parameters with the notification.</p>
     *
     * @param method the name of the notification method to be called on the server
     * @param params a map of parameters to be sent with the notification
     * @return a Mono that completes when the notification has been sent
     */
    Mono<Void> sendNotification(String method, Map<String, Object> params);

    /**
     * Gracefully closes the session with a specified timeout.
     * 
     * <p>This method attempts to close the session cleanly, waiting for any pending
     * operations to complete up to the specified timeout duration.</p>
     *
     * @param timeout the maximum duration to wait for the session to close
     * @return a Mono that completes when the session has been closed
     */
    Mono<Void> closeGracefully(Duration timeout);
}