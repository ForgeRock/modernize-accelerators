package org.forgerock.openam.miami.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

public final class RequestUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestUtils.class);

	public static ResponseEntity<String> sendPostRequest(String url, String jsonBody, MediaType contentType,
			MultiValueMap<String, String> headersMap) {
		LOGGER.debug("sendPostRequest()::url: " + url);
		LOGGER.debug("sendPostRequest()::jsonBody: " + jsonBody);
		LOGGER.debug("sendPostRequest()::contentType: " + contentType);
		LOGGER.debug("sendPostRequest()::headersMap: " + headersMap);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(contentType);
		headers.addAll(headersMap);
		HttpEntity<String> entity = new HttpEntity<String>(jsonBody, headers);
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> responseEntity = null;
		try {
			responseEntity = restTemplate.postForEntity(url, entity, String.class);
			LOGGER.error("sendPostRequest()::responseEntity.getStatusCodeValue(): " + responseEntity.getStatusCodeValue());
		} catch (HttpClientErrorException exception) {
			LOGGER.error("sendPostRequest()::exception.getStatusCodeValue(): " + exception.getStatusCode().value());
			LOGGER.error("sendPostRequest()::exception.getResponseBodyAsString(): " + exception.getResponseBodyAsString());
		}
		return responseEntity;
	}

	public static ResponseEntity<String> sendGetRequest(String url, MediaType contentType,
			MultiValueMap<String, String> headersMap) {
		LOGGER.debug("sendPostRequest()::url: " + url);
		LOGGER.debug("sendPostRequest()::contentType: " + contentType);
		LOGGER.debug("sendPostRequest()::headersMap: " + headersMap);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(contentType);
		headers.addAll(headersMap);
		HttpEntity<String> entity = new HttpEntity<String>(headers);
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
		LOGGER.error("sendGetRequest()::responseEntity.getStatusCodeValue(): " + responseEntity.getStatusCodeValue());
		return responseEntity;
	}
}
