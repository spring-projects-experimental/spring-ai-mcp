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

package org.springframework.ai.mcp.spec;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

/**
 * Based on the <a href="http://www.jsonrpc.org/specification">JSON-RPC 2.0
 * specification<a/> and the <a href=
 * "https://github.com/modelcontextprotocol/specification/blob/main/schema/schema.ts">Model
 * Context Protocol Schema</a>.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class McpSchema {

	public static final String LATEST_PROTOCOL_VERSION = "2024-11-05";

	public static final String JSONRPC_VERSION = "2.0";

	// ---------------------------
	// JSON-RPC Error Codes
	// ---------------------------
	public final class ErrorCodes {

		public static final int PARSE_ERROR = -32700;

		public static final int INVALID_REQUEST = -32600;

		public static final int METHOD_NOT_FOUND = -32601;

		public static final int INVALID_PARAMS = -32602;

		public static final int INTERNAL_ERROR = -32603;

	}

	public sealed interface Request
			permits InitializeRequest, CallToolRequest, CreateMessageRequest, CompleteRequest, GetPromptRequest {

	}

	// ---------------------------
	// JSON-RPC Message Types
	// ---------------------------
	public sealed interface JSONRPCMessage permits JSONRPCRequest, JSONRPCNotification, JSONRPCResponse {

		String jsonrpc();

	}

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record JSONRPCRequest( // @formatter:off
			@JsonProperty("jsonrpc") String jsonrpc,
			@JsonProperty("method") String method,
			@JsonProperty("id") Object id,
			@JsonProperty("params") Object params) implements JSONRPCMessage {
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record JSONRPCNotification( // @formatter:off
			@JsonProperty("jsonrpc") String jsonrpc,
			@JsonProperty("method") String method,
			@JsonProperty("params") Map<String, Object> params) implements JSONRPCMessage {
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record JSONRPCResponse( // @formatter:off
			@JsonProperty("jsonrpc") String jsonrpc,
			@JsonProperty("id") Object id,
			@JsonProperty("result") Object result,
			@JsonProperty("error") JSONRPCError error) implements JSONRPCMessage {

		@JsonInclude(JsonInclude.Include.NON_ABSENT)
		public record JSONRPCError(
			@JsonProperty("code") int code,
			@JsonProperty("message") String message,
			@JsonProperty("data") Object data) {
		}
	}// @formatter:on

	// ---------------------------
	// Initialization
	// ---------------------------
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record InitializeRequest( // @formatter:off
		@JsonProperty("protocolVersion") String protocolVersion,
		@JsonProperty("capabilities") ClientCapabilities capabilities,
		@JsonProperty("clientInfo") Implementation clientInfo) implements Request {		
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record InitializeResult( // @formatter:off
		@JsonProperty("protocolVersion") String protocolVersion,
		@JsonProperty("capabilities") ServerCapabilities capabilities,
		@JsonProperty("serverInfo") Implementation serverInfo,
		@JsonProperty("instructions") String instructions) {
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ClientCapabilities( // @formatter:off
		@JsonProperty("experimental") Map<String, Object> experimental,
		@JsonProperty("roots") RootCapabilities roots,
		@JsonProperty("sampling") Object sampling) {

		@JsonInclude(JsonInclude.Include.NON_ABSENT)			
		public record RootCapabilities(
			@JsonProperty("listChanged") Boolean listChanged) {
		}
	}// @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ServerCapabilities( // @formatter:off
		@JsonProperty("experimental") Map<String, Object> experimental,
		@JsonProperty("logging") Object logging,
		@JsonProperty("prompts") PromptCapabilities prompts,
		@JsonProperty("resources") ResourceCapabilities resources,
		@JsonProperty("tools") ToolCapabilities tools) {
		
		@JsonInclude(JsonInclude.Include.NON_ABSENT)
		public record PromptCapabilities(
			@JsonProperty("listChanged") Boolean listChanged) {
		}

		@JsonInclude(JsonInclude.Include.NON_ABSENT)
		public record ResourceCapabilities(
			@JsonProperty("subscribe") Boolean subscribe,
			@JsonProperty("listChanged") Boolean listChanged) {
		}

		@JsonInclude(JsonInclude.Include.NON_ABSENT)
		public record ToolCapabilities(
			@JsonProperty("listChanged") Boolean listChanged) {
		}
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record Implementation(// @formatter:off
		@JsonProperty("name") String name,
		@JsonProperty("version") String version) {
	} // @formatter:on

	// Existing Enums and Base Types (from previous implementation)
	public enum Role {

		@JsonProperty("user")
		USER, @JsonProperty("assistant")
		ASSISTANT

	}

	public enum LoggingLevel {

		@JsonProperty("debug")
		DEBUG, @JsonProperty("info")
		INFO, @JsonProperty("notice")
		NOTICE, @JsonProperty("warning")
		WARNING, @JsonProperty("error")
		ERROR, @JsonProperty("critical")
		CRITICAL, @JsonProperty("alert")
		ALERT, @JsonProperty("emergency")
		EMERGENCY

	}

	// ---------------------------
	// Resource Interfaces
	// ---------------------------
	/**
	 * Base for objects that include optional annotations for the client. The client can
	 * use annotations to inform how objects are used or displayed
	 */
	public interface Annotated {

		Annotations annotations();

	}

	/**
	 * @param audience Describes who the intended customer of this object or data is. It
	 * can include multiple entries to indicate content useful for multiple audiences
	 * (e.g., `["user", "assistant"]`).
	 * @param priority Describes how important this data is for operating the server. A
	 * value of 1 means "most important," and indicates that the data is effectively
	 * required, while 0 means "least important," and indicates that the data is entirely
	 * optional. It is a number between 0 and 1.
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record Annotations( // @formatter:off
		@JsonProperty("audience") List<Role> audience,
		@JsonProperty("priority") Double priority) {
	} // @formatter:on

	/**
	 * A known resource that the server is capable of reading.
	 *
	 * @param uri the URI of the resource.
	 * @param name A human-readable name for this resource. This can be used by clients to
	 * populate UI elements.
	 * @param description A description of what this resource represents. This can be used
	 * by clients to improve the LLM's understanding of available resources. It can be
	 * thought of like a "hint" to the model.
	 * @param mimeType The MIME type of this resource, if known.
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Resource( // @formatter:off
		@JsonProperty("uri") String uri,
		@JsonProperty("name") String name,
		@JsonProperty("description") String description,
		@JsonProperty("mimeType") String mimeType,
		@JsonProperty("annotations") Annotations annotations) implements Annotated {
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ResourceTemplate( // @formatter:off
		@JsonProperty("uriTemplate") String uriTemplate,
		@JsonProperty("name") String name,
		@JsonProperty("description") String description,
		@JsonProperty("mimeType") String mimeType,
		@JsonProperty("annotations") Annotations annotations) implements Annotated {
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ListResourcesResult( // @formatter:off
		@JsonProperty("resources") List<Resource> resources,
		@JsonProperty("nextCursor") String nextCursor) {
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ListResourceTemplatesResult( // @formatter:off
		@JsonProperty("resourceTemplates") List<ResourceTemplate> resourceTemplates,
		@JsonProperty("nextCursor") String nextCursor) {
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ReadResourceRequest( // @formatter:off
		@JsonProperty("uri") String uri){
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ReadResourceResult( // @formatter:off
		@JsonProperty("contents") List<ResourceContents> contents){
	} // @formatter:on

	/**
	 * Sent from the client to request resources/updated notifications from the server
	 * whenever a particular resource changes.
	 *
	 * @param uri the URI of the resource to subscribe to. The URI can use any protocol;
	 * it is up to the server how to interpret it.
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record SubscribeRequest( // @formatter:off
		@JsonProperty("uri") String uri){
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record UnsubscribeRequest( // @formatter:off
		@JsonProperty("uri") String uri){
	} // @formatter:on

	/**
	 * The contents of a specific resource or sub-resource.
	 */
	@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, include = As.PROPERTY)
	@JsonSubTypes({ @JsonSubTypes.Type(value = TextResourceContents.class, name = "text"),
			@JsonSubTypes.Type(value = BlobResourceContents.class, name = "blob") })
	public sealed interface ResourceContents permits TextResourceContents, BlobResourceContents {

		/**
		 * The URI of this resource.
		 * @return the URI of this resource.
		 */
		String uri();

		/**
		 * The MIME type of this resource.
		 * @return the MIME type of this resource.
		 */
		String mimeType();

	}

	/**
	 * Text contents of a resource.
	 *
	 * @param uri the URI of this resource.
	 * @param mimeType the MIME type of this resource.
	 * @param text the text of the resource. This must only be set if the resource can
	 * actually be represented as text (not binary data).
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record TextResourceContents( // @formatter:off
		@JsonProperty("uri") String uri,
		@JsonProperty("mimeType") String mimeType,
		@JsonProperty("text") String text) implements ResourceContents {
	} // @formatter:on

	/**
	 * Binary contents of a resource.
	 *
	 * @param uri the URI of this resource.
	 * @param mimeType the MIME type of this resource.
	 * @param blob a base64-encoded string representing the binary data of the resource.
	 * This must only be set if the resource can actually be represented as binary data
	 * (not text).
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record BlobResourceContents( // @formatter:off
		@JsonProperty("uri") String uri,
		@JsonProperty("mimeType") String mimeType,
		@JsonProperty("blob") String blob) implements ResourceContents {
	} // @formatter:on

	// ---------------------------
	// Prompt Interfaces
	// ---------------------------
	/**
	 * A prompt or prompt template that the server offers.
	 *
	 * @param name The name of the prompt or prompt template.
	 * @param description An optional description of what this prompt provides.
	 * @param arguments A list of arguments to use for templating the prompt.
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record Prompt( // @formatter:off
		@JsonProperty("name") String name,
		@JsonProperty("description") String description,
		@JsonProperty("arguments") List<PromptArgument> arguments) {
	} // @formatter:on

	/**
	 * Describes an argument that a prompt can accept.
	 *
	 * @param name The name of the argument.
	 * @param description A human-readable description of the argument.
	 * @param required Whether this argument must be provided.
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record PromptArgument( // @formatter:off
		@JsonProperty("name") String name,
		@JsonProperty("description") String description,
		@JsonProperty("required") Boolean required) {
	}// @formatter:on

	/**
	 * Describes a message returned as part of a prompt.
	 *
	 * This is similar to `SamplingMessage`, but also supports the embedding of resources
	 * from the MCP server.
	 *
	 * @param role The sender or recipient of messages and data in a conversation.
	 * @param content The content of the message of type {@link Content}.
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record PromptMessage( // @formatter:off
		@JsonProperty("role") Role role,
		@JsonProperty("content") Content content) {
	} // @formatter:on

	/**
	 * The server's response to a prompts/list request from the client.
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ListPromptsResult( // @formatter:off
		@JsonProperty("prompts") List<Prompt> prompts,
		@JsonProperty("nextCursor") String nextCursor) {
	}// @formatter:on

	/**
	 * Used by the client to get a prompt provided by the server.
	 *
	 * @param name The name of the prompt or prompt template.
	 * @param arguments Arguments to use for templating the prompt.
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record GetPromptRequest(// @formatter:off
		@JsonProperty("name") String name,
		@JsonProperty("arguments") Map<String, Object> arguments) implements Request {
	}// @formatter:off

	/**
	 * The server's response to a prompts/get request from the client.
	 *
	 * @param description An optional description for the prompt.
	 * @param messages A list of messages to display as part of the prompt.
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record GetPromptResult( // @formatter:off
		@JsonProperty("description") String description,
		@JsonProperty("messages") List<PromptMessage> messages) {
	} // @formatter:on

	// ---------------------------
	// Tool Interfaces
	// ---------------------------
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ListToolsResult( // @formatter:off
		@JsonProperty("tools") List<Tool> tools,
		@JsonProperty("nextCursor") String nextCursor) {
	}// @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record Tool( // @formatter:off
		@JsonProperty("name") String name,
		@JsonProperty("description") String description,
		@JsonProperty("inputSchema") Map<String, Object> inputSchema) {
	} // @formatter:on

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record CallToolRequest(// @formatter:off
		@JsonProperty("name") String name,
		@JsonProperty("arguments") Map<String, Object> arguments) implements Request {
	}// @formatter:off

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record CallToolResult( // @formatter:off
		@JsonProperty("content") List<Content> content,
		@JsonProperty("isError") Boolean isError) {
	} // @formatter:on

	// ---------------------------
	// Sampling Interfaces
	// ---------------------------
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record SamplingMessage(// @formatter:off
		@JsonProperty("role") Role role,
		@JsonProperty("content") Content content) {
	} // @formatter:on

	// Sampling and Message Creation
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record CreateMessageRequest(List<SamplingMessage> messages, ModelPreferences modelPreferences,
			String systemPrompt, ContextInclusionStrategy includeContext, Double temperature, int maxTokens,
			List<String> stopSequences, Map<String, Object> metadata) implements Request {

		public enum ContextInclusionStrategy {

			NONE, THIS_SERVER, ALL_SERVERS

		}
	}

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record CreateMessageResult(Role role, Content content, String model, StopReason stopReason) {
		public enum StopReason {

			END_TURN, STOP_SEQUENCE, MAX_TOKENS

		}
	}

	// ---------------------------
	// Model Preferences
	// ---------------------------
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ModelPreferences(List<ModelHint> hints, Double costPriority, Double speedPriority,
			Double intelligencePriority) {
	}

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ModelHint(String name) {
	}

	// ---------------------------
	// Pagination Interfaces
	// ---------------------------
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record PaginatedRequest(@JsonProperty("cursor") String cursor) {
	}

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record PaginatedResult(@JsonProperty("nextCursor") String nextCursor) {
	}

	// ---------------------------
	// Progress and Logging
	// ---------------------------
	public record ProgressNotification(String progressToken, double progress, Double total) {
	}

	public record LoggingMessageNotification(LoggingLevel level, String logger, Object data) {
	}

	// ---------------------------
	// Autocomplete
	// ---------------------------
	public record CompleteRequest(PromptOrResourceReference ref, CompleteArgument argument) implements Request {
		public sealed interface PromptOrResourceReference permits PromptReference, ResourceReference {

			String type();

		}

		public record PromptReference(String type, String name) implements PromptOrResourceReference {
		}

		public record ResourceReference(String type, String uri) implements PromptOrResourceReference {
		}

		public record CompleteArgument(String name, String value) {
		}
	}

	public record CompleteResult(CompleteCompletion completion) {
		public record CompleteCompletion(List<String> values, Integer total, Boolean hasMore) {
		}
	}

	// ---------------------------
	// Content Types
	// ---------------------------
	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
	@JsonSubTypes({ @JsonSubTypes.Type(value = TextContent.class, name = "text"),
			@JsonSubTypes.Type(value = ImageContent.class, name = "image"),
			@JsonSubTypes.Type(value = EmbeddedResource.class, name = "resource") })
	public sealed interface Content permits TextContent, ImageContent, EmbeddedResource {

		String type();

	}

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record TextContent( // @formatter:off
		@JsonProperty("audience") List<Role> audience,
		@JsonProperty("priority") Double priority,
		@JsonProperty("type") String type,
		@JsonProperty("text") String text) implements Content { // @formatter:on

		public TextContent {
			type = "text";
		}

		public String type() {
			return type;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record ImageContent( // @formatter:off
		@JsonProperty("audience") List<Role> audience,
		@JsonProperty("priority") Double priority,
		@JsonProperty("type") String type,
		@JsonProperty("data") String data,
		@JsonProperty("mimeType") String mimeType) implements Content { // @formatter:on

		public ImageContent {
			type = "image";
		}

		public String type() {
			return type;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record EmbeddedResource( // @formatter:off
		@JsonProperty("audience") List<Role> audience,
		@JsonProperty("priority") Double priority,
		@JsonProperty("type") String type,
		@JsonProperty("resource") ResourceContents resource) implements Content { // @formatter:on

		public EmbeddedResource {
			type = "resource";
		}

		public String type() {
			return type;
		}
	}

	// Roots
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record Root( // @formatter:off
		@JsonProperty("uri") String uri,
		@JsonProperty("name") String name) {
	} // @formatter:on

}