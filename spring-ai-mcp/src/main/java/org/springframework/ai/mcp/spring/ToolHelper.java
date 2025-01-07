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
package org.springframework.ai.mcp.spring;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.server.McpServer;
import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.util.ClassUtils;

/**
 * Utility class that provides helper methods for working with Model Context Protocol
 * (MCP) tools in a Spring AI environment. This class facilitates the integration between
 * Spring AI's function callbacks and MCP's tool system.
 *
 * <p>
 * The MCP tool system enables servers to expose executable functionality to language
 * models, allowing them to interact with external systems, perform computations, and take
 * actions in the real world. Each tool is uniquely identified by a name and includes
 * metadata describing its schema.
 *
 * <p>
 * This helper class provides methods to:
 * <ul>
 * <li>Convert Spring AI's {@link FunctionCallback} instances to MCP tool
 * registrations</li>
 * <li>Generate JSON schemas for tool input validation</li>
 * </ul>
 *
 * @author Christian Tzolov
 * @see org.springframework.ai.model.function.FunctionCallback
 * @see org.springframework.ai.mcp.server.McpServer.ToolRegistration
 * @see org.springframework.ai.mcp.spec.McpSchema.Tool
 */
public final class ToolHelper {

	private ToolHelper() {
	}

	public static List<McpServer.ToolRegistration> toToolRegistration(List<FunctionCallback> functionCallbacks) {
		return functionCallbacks.stream().map(ToolHelper::toToolRegistration).toList();
	}

	public static List<McpServer.ToolRegistration> toToolRegistration(FunctionCallback... functionCallbacks) {
		return toToolRegistration(List.of(functionCallbacks));
	}

	/**
	 * Converts a Spring AI FunctionCallback to an MCP ToolRegistration. This enables
	 * Spring AI functions to be exposed as MCP tools that can be discovered and invoked
	 * by language models.
	 *
	 * <p>
	 * The conversion process:
	 * <ul>
	 * <li>Creates an MCP Tool with the function's name and input schema</li>
	 * <li>Wraps the function's execution in a ToolRegistration that handles the MCP
	 * protocol</li>
	 * <li>Provides error handling and result formatting according to MCP
	 * specifications</li>
	 * </ul>
	 *
	 * You can use the FunctionCallback builder to create a new instance of
	 * FunctionCallback using either java.util.function.Function or Method reference.
	 * @param functionCallback the Spring AI function callback to convert
	 * @return an MCP ToolRegistration that wraps the function callback
	 * @throws RuntimeException if there's an error during the function execution
	 */
	public static McpServer.ToolRegistration toToolRegistration(FunctionCallback functionCallback) {

		var tool = new McpSchema.Tool(functionCallback.getName(), functionCallback.getName(),
				functionCallback.getInputTypeSchema());

		return new McpServer.ToolRegistration(tool, request -> {
			try {
				String callResult = functionCallback.call(ModelOptionsUtils.toJsonString(request));
				return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(callResult)), false);
			}
			catch (Exception e) {
				return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(e.getMessage())), true);
			}
		});
	}

	/**
	 * Generates a JSON schema for a map of named classes using the default ObjectMapper.
	 * This schema can be used to validate tool inputs according to the MCP specification.
	 * @param namedClasses a map of class names to their corresponding Class objects
	 * @return a JSON schema string that describes the structure of the named classes
	 * @throws RuntimeException if schema generation fails
	 * @see #generateJsonSchema(Map, ObjectMapper)
	 */
	public static String generateJsonSchema(Map<String, Class<?>> namedClasses) {
		return generateJsonSchema(namedClasses, new ObjectMapper());
	}

	/**
	 * Generates a JSON schema for a map of named classes using a custom ObjectMapper. The
	 * generated schema follows the JSON Schema Draft 2020-12 specification and describes
	 * the structure of the provided classes.
	 *
	 * <p>
	 * This method:
	 * <ul>
	 * <li>Creates a schema that validates the structure of tool inputs</li>
	 * <li>Excludes ToolContext class from schema generation</li>
	 * <li>Uses Jackson's JsonSchemaGenerator for accurate type representation</li>
	 * </ul>
	 * @param namedClasses a map of class names to their corresponding Class objects
	 * @param mapper the ObjectMapper to use for JSON processing
	 * @return a JSON schema string that describes the structure of the named classes
	 * @throws RuntimeException if schema generation fails
	 */
	public static String generateJsonSchema(Map<String, Class<?>> namedClasses, ObjectMapper mapper) {
		try {
			JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);

			ObjectNode rootNode = mapper.createObjectNode();
			rootNode.put("$schema", "https://json-schema.org/draft/2020-12/schema");
			rootNode.put("type", "object");
			ObjectNode propertiesNode = rootNode.putObject("properties");

			for (Map.Entry<String, Class<?>> entry : namedClasses.entrySet()) {
				String className = entry.getKey();
				Class<?> clazz = entry.getValue();

				if (ClassUtils.isAssignable(clazz, ToolContext.class)) {
					// Skip the ToolContext class from the schema generation.
					continue;
				}

				JsonSchema schema = schemaGen.generateSchema(clazz);
				JsonNode schemaNode = mapper.valueToTree(schema);
				propertiesNode.set(className, schemaNode);
			}

			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
