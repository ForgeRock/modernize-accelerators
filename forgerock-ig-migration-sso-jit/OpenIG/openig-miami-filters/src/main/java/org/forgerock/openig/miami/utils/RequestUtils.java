/***************************************************************************
 *  Copyright 2019 ForgeRock AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ***************************************************************************/
package org.forgerock.openig.miami.utils;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

public final class RequestUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestUtils.class);

	public static ResponseEntity<String> sendPostRequest(String url, String jsonBody, MediaType contentType,
			MultiValueMap<String, String> headersMap) {
		LOGGER.error("sendPostRequest()::url: " + url);
		LOGGER.debug("sendPostRequest()::jsonBody: " + jsonBody);
		LOGGER.debug("sendPostRequest()::contentType: " + contentType);
		LOGGER.debug("sendPostRequest()::headersMap: " + headersMap);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(contentType);
		headers.addAll(headersMap);
		HttpEntity<String> entity = new HttpEntity<String>(jsonBody, headers);
		RestTemplate restTemplate = new RestTemplate();
		HttpClient httpClient = HttpClientBuilder.create().disableCookieManagement().build();
		restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
		ResponseEntity<String> responseEntity = null;
		try {
			responseEntity = restTemplate.postForEntity(url, entity, String.class);
			LOGGER.debug(
					"sendPostRequest()::responseEntity.getStatusCodeValue(): " + responseEntity.getStatusCodeValue());
		} catch (HttpClientErrorException exception) {
			LOGGER.error("sendPostRequest()::exception.getStatusCodeValue(): " + exception.getStatusCode().value());
			LOGGER.error(
					"sendPostRequest()::exception.getResponseBodyAsString(): " + exception.getResponseBodyAsString());
			exception.printStackTrace();
		}
		return responseEntity;
	}

	public static ResponseEntity<String> sendGetRequest(String url, MediaType contentType,
			MultiValueMap<String, String> headersMap) {
		LOGGER.debug("sendGetRequest()::url: " + url);
		LOGGER.debug("sendGetRequest()::contentType: " + contentType);
		LOGGER.debug("sendGetRequest()::headersMap: " + headersMap);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(contentType);
		headers.addAll(headersMap);
		HttpEntity<String> entity = new HttpEntity<String>(headers);
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
		LOGGER.debug("sendGetRequest()::responseEntity.getStatusCodeValue(): " + responseEntity.getStatusCodeValue());
		return responseEntity;
	}
}
