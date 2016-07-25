/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.cloud.contract.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContext;
import org.springframework.restdocs.operation.Operation;
import org.springframework.restdocs.snippet.Snippet;

import com.github.tomakehurst.wiremock.client.RemoteMappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.matching.UrlPattern;

public class WireMockSnippet implements Snippet {

	private String snippetName = "stubs";

	private Set<String> headerBlackList = new HashSet<>(
			Arrays.asList("host", "content-length"));

	private Set<String> jsonPaths = new LinkedHashSet<>();

	private MediaType contentType;

	@Override
	public void document(Operation operation) throws IOException {
		String json = Json
				.write(request(operation).willReturn(response(operation)).build());
		RestDocumentationContext context = (RestDocumentationContext) operation
				.getAttributes().get(RestDocumentationContext.class.getName());
		File output = new File(context.getOutputDirectory(),
				this.snippetName + "/" + operation.getName() + ".json");
		output.getParentFile().mkdirs();
		try (Writer writer = new OutputStreamWriter(new FileOutputStream(output))) {
			writer.append(json);
		}
	}

	private ResponseDefinitionBuilder response(Operation operation) {
		return aResponse().withHeaders(responseHeaders(operation))
				.withBody(operation.getResponse().getContentAsString());
	}

	private RemoteMappingBuilder<?, ?> request(Operation operation) {
		return requestHeaders(requestBuilder(operation), operation);
	}

	private RemoteMappingBuilder<?, ?> requestHeaders(RemoteMappingBuilder<?, ?> request,
			Operation operation) {
		org.springframework.http.HttpHeaders headers = operation.getRequest()
				.getHeaders();
		// TODO: whitelist headers
		for (String name : headers.keySet()) {
			if (!headerBlackList.contains(name.toLowerCase())) {
				if ("content-type".equalsIgnoreCase(name) && this.contentType != null) {
					continue;
				}
				request = request.withHeader(name, equalTo(headers.getFirst(name)));
			}
		}
		if (this.contentType != null) {
			request = request.withHeader("Content-Type",
					matching(Pattern.quote(this.contentType.toString()) + ".*"));
		}
		return request;
	}

	private RemoteMappingBuilder<?, ?> requestBuilder(Operation operation) {
		switch (operation.getRequest().getMethod()) {
		case DELETE:
			return delete(requestPattern(operation));
		case POST:
			return bodyPattern(post(requestPattern(operation)),
					operation.getRequest().getContentAsString());
		case PUT:
			return bodyPattern(put(requestPattern(operation)),
					operation.getRequest().getContentAsString());
		default:
			return get(requestPattern(operation));
		}
	}

	private RemoteMappingBuilder<?, ?> bodyPattern(RemoteMappingBuilder<?, ?> builder,
			String content) {
		if (jsonPaths != null) {
			for (String jsonPath : jsonPaths) {
				builder.withRequestBody(matchingJsonPath(jsonPath));
			}
		}
		else {
			builder.withRequestBody(equalTo(content));
		}
		return builder;
	}

	private UrlPattern requestPattern(Operation operation) {
		return urlEqualTo(operation.getRequest().getUri().getPath());
	}

	private HttpHeaders responseHeaders(Operation operation) {
		org.springframework.http.HttpHeaders headers = operation.getResponse()
				.getHeaders();
		HttpHeaders result = new HttpHeaders();
		for (String name : headers.keySet()) {
			// TODO: whitelist the headers
			if (!headerBlackList.contains(name.toLowerCase())) {
				result = result.plus(new HttpHeader(name, headers.get(name)));
			}
		}
		return result;
	}

	public ContractRequestHandler content() {
		return new ContractRequestHandler(this);
	}

	public void setJsonPaths(Collection<String> jsonPaths) {
		this.jsonPaths = new LinkedHashSet<>(jsonPaths);
	}

	public void setContentType(MediaType contentType) {
		this.contentType = contentType;
	}

}