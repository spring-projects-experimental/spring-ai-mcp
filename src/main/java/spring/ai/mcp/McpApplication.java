package spring.ai.mcp;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import spring.ai.mcp.client.McpClient;
import spring.ai.mcp.client.stdio.StdioServerParameters;
import spring.ai.mcp.client.stdio.StdioServerTransport;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpApplication {

	public static void main(String[] args) {
		SpringApplication.run(McpApplication.class, args);
	}

	@Bean
	public CommandLineRunner test(ChatClient.Builder chatClientBuilder, List<McpFunctionCallback> functionCallbacks) {

		return args -> {

			var chatClient = chatClientBuilder.defaultFunctions(functionCallbacks.toArray(new McpFunctionCallback[0]))
				.build();

			var response = chatClient.prompt("What is the content of the test.txt file?").call().content();
			System.out.println(response);

			response = chatClient.prompt(
					"Please summarize the content of the test.txt file, covert the result in markdown format and store it into test.md")
				.call()
				.content();
			System.out.println("Summary:" + response);
		};
	}

	@Bean
	public List<McpFunctionCallback> functionCallbacks(McpClient mcpClient) {

		return mcpClient.listTools(null)
			.tools()
			.stream()
			.map(tool -> new McpFunctionCallback(mcpClient, tool))
			.toList();
	}

	@Bean(destroyMethod = "close")
	public McpClient clientSession() {

		var stdioParams = StdioServerParameters.builder("npx")
			.args("-y", "@modelcontextprotocol/server-filesystem",
					"/Users/christiantzolov/Dev/projects/demo/mcp-server/dir")
			.build();

		McpClient mcpClient = null;
		try {			
			mcpClient = new McpClient(new StdioServerTransport(stdioParams, Duration.ofMillis(100)),
					Duration.ofSeconds(10), new ObjectMapper());

			var init = mcpClient.initialize();

			System.out.println("MCP Initialized: " + init);

			return mcpClient;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}