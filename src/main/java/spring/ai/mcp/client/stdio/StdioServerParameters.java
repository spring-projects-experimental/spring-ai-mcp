package spring.ai.mcp.client.stdio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Server parameters for stdio client.
 */
public class StdioServerParameters {

	// Environment variables to inherit by default
	private static final List<String> DEFAULT_INHERITED_ENV_VARS = System.getProperty("os.name")
		.toLowerCase()
		.contains("win")
				? Arrays.asList("APPDATA", "HOMEDRIVE", "HOMEPATH", "LOCALAPPDATA", "PATH",
						"PROCESSOR_ARCHITECTURE", "SYSTEMDRIVE", "SYSTEMROOT", "TEMP", "USERNAME", "USERPROFILE")
				: Arrays.asList("HOME", "LOGNAME", "PATH", "SHELL", "TERM", "USER");

	@JsonProperty("command")
	private String command;

	@JsonProperty("args")
	private List<String> args = new ArrayList<>();

	@JsonProperty("env")
	private Map<String, String> env;

	private StdioServerParameters(String command, List<String> args, Map<String, String> env) {
		Assert.hasText(command, "Command must not be empty");
		Assert.notNull(args, "Arguments must not be null");

		this.command = command;
		this.args = args;
		this.env = new HashMap<>(getDefaultEnvironment());
		if (!CollectionUtils.isEmpty(env)) {
			this.env.putAll(env);
		}
	}

	public String getCommand() {
		return this.command;
	}

	public List<String> getArgs() {
		return this.args;
	}

	public Map<String, String> getEnv() {
		return this.env;
	}

	public static Builder builder(String command) {
		return new Builder(command);
	}

	public static class Builder {

		private String command;

		private List<String> args = new ArrayList<>();

		private Map<String, String> env = new HashMap<>();

		public Builder(String command) {
			Assert.hasText(command, "Command must not be empty");
			this.command = command;
		}

		public Builder args(String... args) {
			Assert.notNull(args, "Arguments must not be null");
			this.args = Arrays.asList(args);
			return this;
		}

		public Builder args(List<String> args) {
			Assert.notNull(args, "Arguments must not be null");
			this.args = new ArrayList<>(args);
			return this;
		}

		public Builder arg(String arg) {
			Assert.hasText(arg, "Argument must not be empty");
			this.args.add(arg);
			return this;
		}

		public Builder env(Map<String, String> env) {
			if (!CollectionUtils.isEmpty(env)) {
				this.env.putAll(env);
			}
			return this;
		}

		public Builder addEnvVar(String key, String value) {
			Assert.hasText(key, "Environment variable key must not be empty");
			Assert.notNull(value, "Environment variable value must not be null");
			this.env.put(key, value);
			return this;
		}

		public StdioServerParameters build() {
			return new StdioServerParameters(command, args, env);
		}

	}

	/**
	 * Returns a default environment object including only environment variables
	 * deemed safe to inherit.
	 */
	private static Map<String, String> getDefaultEnvironment() {
		return System.getenv()
			.entrySet()
			.stream()
			.filter(entry -> DEFAULT_INHERITED_ENV_VARS.contains(entry.getKey()))
			.filter(entry -> entry.getValue() != null)
			.filter(entry -> !entry.getValue().startsWith("()"))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

}