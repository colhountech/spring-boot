/*
 * Copyright 2012-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample.actuator;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic integration tests for service demo application.
 *
 * @author Dave Syer
 * @author Stephane Nicoll
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(SampleActuatorApplication.class)
@WebIntegrationTest(randomPort = true)
@DirtiesContext
public class SampleActuatorApplicationTests {

	@Autowired
	private SecurityProperties security;

	@Value("${local.server.port}")
	private int port;

	@Test
	public void testHomeIsSecure() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> entity = new TestRestTemplate()
				.getForEntity("http://localhost:" + this.port, Map.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		@SuppressWarnings("unchecked")
		Map<String, Object> body = entity.getBody();
		assertThat(body.get("error")).isEqualTo("Unauthorized");
		assertThat(entity.getHeaders()).doesNotContainKey("Set-Cookie");
	}

	@Test
	public void testMetricsIsSecure() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> entity = new TestRestTemplate()
				.getForEntity("http://localhost:" + this.port + "/metrics", Map.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		entity = new TestRestTemplate()
				.getForEntity("http://localhost:" + this.port + "/metrics/", Map.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		entity = new TestRestTemplate().getForEntity(
				"http://localhost:" + this.port + "/metrics/foo", Map.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		entity = new TestRestTemplate().getForEntity(
				"http://localhost:" + this.port + "/metrics.json", Map.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	public void testHome() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> entity = new TestRestTemplate("user", getPassword())
				.getForEntity("http://localhost:" + this.port, Map.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		Map<String, Object> body = entity.getBody();
		assertThat(body.get("message")).isEqualTo("Hello Phil");
	}

	@Test
	public void testMetrics() throws Exception {
		testHome(); // makes sure some requests have been made
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> entity = new TestRestTemplate("user", getPassword())
				.getForEntity("http://localhost:" + this.port + "/metrics", Map.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		Map<String, Object> body = entity.getBody();
		assertThat(body).containsKey("counter.status.200.root");
	}

	@Test
	public void testEnv() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> entity = new TestRestTemplate("user", getPassword())
				.getForEntity("http://localhost:" + this.port + "/env", Map.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		Map<String, Object> body = entity.getBody();
		assertThat(body).containsKey("systemProperties");
	}

	@Test
	public void testHealth() throws Exception {
		ResponseEntity<String> entity = new TestRestTemplate()
				.getForEntity("http://localhost:" + this.port + "/health", String.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(entity.getBody()).contains("\"status\":\"UP\"");
		assertThat(entity.getBody()).doesNotContain("\"hello\":\"1\"");
	}

	@Test
	public void testSecureHealth() throws Exception {
		ResponseEntity<String> entity = new TestRestTemplate("user", getPassword())
				.getForEntity("http://localhost:" + this.port + "/health", String.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(entity.getBody()).contains("\"hello\":1");
	}

	@Test
	public void testInfo() throws Exception {
		ResponseEntity<String> entity = new TestRestTemplate()
				.getForEntity("http://localhost:" + this.port + "/info", String.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(entity.getBody())
				.contains("\"artifact\":\"spring-boot-sample-actuator\"");
		assertThat(entity.getBody()).contains("\"someKey\":\"someValue\"");
		assertThat(entity.getBody())
				.contains("\"java\":{", "\"source\":\"1.8\"", "\"target\":\"1.8\"");
		assertThat(entity.getBody())
				.contains("\"encoding\":{", "\"source\":\"UTF-8\"", "\"reporting\":\"UTF-8\"");
	}

	@Test
	public void testErrorPage() throws Exception {
		ResponseEntity<String> entity = new TestRestTemplate("user", getPassword())
				.getForEntity("http://localhost:" + this.port + "/foo", String.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		String body = entity.getBody();
		assertThat(body).contains("\"error\":");
	}

	@Test
	public void testHtmlErrorPage() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.TEXT_HTML));
		HttpEntity<?> request = new HttpEntity<Void>(headers);
		ResponseEntity<String> entity = new TestRestTemplate("user", getPassword())
				.exchange("http://localhost:" + this.port + "/foo", HttpMethod.GET,
						request, String.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		String body = entity.getBody();
		assertThat(body).as("Body was null").isNotNull();
		assertThat(body).contains("This application has no explicit mapping for /error");
	}

	@Test
	public void testTrace() throws Exception {
		new TestRestTemplate().getForEntity("http://localhost:" + this.port + "/health",
				String.class);
		@SuppressWarnings("rawtypes")
		ResponseEntity<List> entity = new TestRestTemplate("user", getPassword())
				.getForEntity("http://localhost:" + this.port + "/trace", List.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> list = entity.getBody();
		Map<String, Object> trace = list.get(list.size() - 1);
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) trace
				.get("info")).get("headers")).get("response");
		assertThat(map.get("status")).isEqualTo("200");
	}

	@Test
	public void testErrorPageDirectAccess() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> entity = new TestRestTemplate()
				.getForEntity("http://localhost:" + this.port + "/error", Map.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		@SuppressWarnings("unchecked")
		Map<String, Object> body = entity.getBody();
		assertThat(body.get("error")).isEqualTo("None");
		assertThat(body.get("status")).isEqualTo(999);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testBeans() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<List> entity = new TestRestTemplate("user", getPassword())
				.getForEntity("http://localhost:" + this.port + "/beans", List.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(entity.getBody()).hasSize(1);
		Map<String, Object> body = (Map<String, Object>) entity.getBody().get(0);
		assertThat(((String) body.get("context"))).startsWith("application");
	}

	@Test
	public void testConfigProps() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> entity = new TestRestTemplate("user", getPassword())
				.getForEntity("http://localhost:" + this.port + "/configprops",
						Map.class);
		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		Map<String, Object> body = entity.getBody();
		assertThat(body)
				.containsKey("spring.datasource-" + DataSourceProperties.class.getName());
	}

	private String getPassword() {
		return this.security.getUser().getPassword();
	}

}
