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
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Timeout;

import org.springframework.ai.mcp.server.transport.WebMvcSseServerTransport;
import org.springframework.ai.mcp.spec.ServerMcpTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Timeout(15)
class WebMvcSseSyncServerTransportTests extends AbstractMcpSyncServerTests {

	private static final String MESSAGE_ENDPOINT = "/mcp/message";

	private static final int PORT = 8181;

	private Tomcat tomcat;

	private WebMvcSseServerTransport transport;

	@Configuration
	@EnableWebMvc
	static class TestConfig {

		@Bean
		public WebMvcSseServerTransport webMvcSseServerTransport() {
			return new WebMvcSseServerTransport(new ObjectMapper(), MESSAGE_ENDPOINT);
		}

		@Bean
		public RouterFunction<ServerResponse> routerFunction(WebMvcSseServerTransport transport) {
			return transport.getRouterFunction();
		}

	}

	private AnnotationConfigWebApplicationContext appContext;

	@Override
	protected ServerMcpTransport createMcpTransport() {
		// Set up Tomcat first
		tomcat = new Tomcat();
		tomcat.setPort(PORT);

		// Set Tomcat base directory to java.io.tmpdir to avoid permission issues
		String baseDir = System.getProperty("java.io.tmpdir");
		tomcat.setBaseDir(baseDir);

		// Use the same directory for document base
		Context context = tomcat.addContext("", baseDir);

		// Create and configure Spring WebMvc context
		appContext = new AnnotationConfigWebApplicationContext();
		appContext.register(TestConfig.class);
		appContext.setServletContext(context.getServletContext());
		appContext.refresh();

		// Get the transport from Spring context
		transport = appContext.getBean(WebMvcSseServerTransport.class);

		// Create DispatcherServlet with our Spring context
		DispatcherServlet dispatcherServlet = new DispatcherServlet(appContext);
		// dispatcherServlet.setThrowExceptionIfNoHandlerFound(true);

		// Add servlet to Tomcat and get the wrapper
		var wrapper = Tomcat.addServlet(context, "dispatcherServlet", dispatcherServlet);
		wrapper.setLoadOnStartup(1);
		context.addServletMappingDecoded("/*", "dispatcherServlet");

		try {
			tomcat.start();
			tomcat.getConnector(); // Create and start the connector
		}
		catch (LifecycleException e) {
			throw new RuntimeException("Failed to start Tomcat", e);
		}

		return transport;
	}

	@Override
	protected void onStart() {
	}

	@Override
	protected void onClose() {
		if (transport != null) {
			transport.closeGracefully().block();
		}
		if (appContext != null) {
			appContext.close();
		}
		if (tomcat != null) {
			try {
				tomcat.stop();
				tomcat.destroy();
			}
			catch (LifecycleException e) {
				throw new RuntimeException("Failed to stop Tomcat", e);
			}
		}
	}

}
