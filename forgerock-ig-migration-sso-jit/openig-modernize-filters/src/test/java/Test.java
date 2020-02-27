
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
import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Test {

	public static void main(String[] args) {
		String requestBody = "{\n"
				+ "   \"authId\":\"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdXRoSW5kZXhWYWx1ZSI6ImxlZ2FjeUF1dGgiLCJvdGsiOiJodHNxYjN2NnZoNjQ0ajkzaGVlZnZpbzhmayIsImF1dGhJbmRleFR5cGUiOiJzZXJ2aWNlIiwicmVhbG0iOiIvbGVnYWN5Iiwic2Vzc2lvbklkIjoiKkFBSlRTUUFDTURJQUJIUjVjR1VBQ0VwWFZGOUJWVlJJQUFKVE1RQUNNREUuKmV5SjBlWEFpT2lKS1YxUWlMQ0pqZEhraU9pSktWMVFpTENKaGJHY2lPaUpJVXpJMU5pSjkuWlhsS01HVllRV2xQYVVwTFZqRlJhVXhEU2paaFdFRnBUMmxLVDFRd05VWkphWGRwV2xjMWFrbHFiMmxSVkVWNVQwVk9RMUY1TVVsVmVra3hUbWxKYzBsdFJuTmFlVWsyU1cxU2NHTnBTamt1TG5kTlJ6Vk9RVmc0Y21KWlVrVllOM1ZFY1UwelJIY3ViVEZmUlZOcGFtazNXbU0xUjJRd1FsSm1lVU5EUzJsVVpuRTJPWGg2VEhGeU16VndSbmh3ZFZGMk4xazVlbkJtWmtoNVZGZGxPV05OWlVKRGRFVlBjVXR3ZHpaclUxUXlPVlJ4U0RCZlVYRlFlbXRJZFhwTFJUVkNWVmxZVWtaUFkwdzBSMmd5TTJWaFJtOU9TSGx5VUVwRU5qSkZOVU56WjA1T2FHUlBibU51WWxsVFMyOHhVRWt5YkdoSGVuTXdNME4xUldGcE4xZFhkVlpVVTJWVlZHVlJhR2t0VERkbFJHTk1lV3hDUjIxTVZtdDFhaTB3ZUhKclRsOXllR1ZLTWtGbU5qQkdXR1JTZUdWNFRsRmZYelJEYXpSclZEbDRTbkZ6ZW1sNVZHWkpURkZ4VERSMmNHcENVR0pJVGpWdGRWa3daRkZtZFZaZllXcEZTa0ZtUjBsWVdFRkJOVGRMVXpsRmEyWXdURTl6TTJnM1NUZG5jM1ZCVG00ek5FRnBMWGRsYnpNNFpHTm5WVkp4WlV0aVkybE1jMnhXYTJ4VE9USlNjbFJ3TTFReFJuSTBjbkpqY0hSUlpTMVBjRk5XTkVKdVNXRkVTbEpLTURkclRFaEJWa05PT1hrM1dWOUJZVlE0TldWS2QwUXhNREYxYlVGMlVsSTFjV3hJWVdaQ1gxSk1VMUo1UmpSNVRITmhlWEpSTm0wMVExSlFkMnh0UTBJd1NVcDBZbk16WkVoZmJtWkJSVjlSVDFGMldGcHVkelJ3U1V4Vk5tUk5UR2gxVkZnMVRYUnBSMnh1Y25KZk1IVlNjSEZYYmt0SFl6bExOa0kyZFVSbE1FdHNXak42ZEZOUVduZFplR1UxVXpGc1FsbDRhalV6YmxkdVZrOUJUVVIzYmtrNWVWRTNYM3BJZWxCUU1HOU1TekpCZURRNVExTlRNVk0wUlZGM09HaHlVVzkzVGs1S05rdHJZbXBVWDNNNVRFcHlUMmhUVldoYWJUUjRaMEZ3TVhsRE9YUllWa3BzYmxOQ1ZtTllTRFZ6Y0ZaNVgwbG9jVmh4VVhKdlh6UkVZVzVqU1V4VFluSXlOa000ZURBNFdtSjBXVU5mWVhRMU9GUnJkVlJOY1dkR1VucEhaMmx0TmtKeGJWcDFTMEZVYjNocVVXb3hSakEzVDNjdWNVNXlNbkpOTUdSb1gzVXhYMEp3U1RkR1RtMWhady5Bb0tmbmJ1cGZhZDQ1UWtNZEpUb0k5NThQX0hjMkVRdndwTkljZWpkNXl3IiwiZXhwIjoxNTczNDY2NTU2LCJpYXQiOjE1NzM0NjYyNTZ9.dYYC2ugjK8FxSGKlEilsD9lH5wESSTdppq4AE7CKek4\",\n"
				+ "   \"callbacks\":[\n" + "      {\n" + "         \"type\":\"NameCallback\",\n"
				+ "         \"output\":[\n" + "            {\n" + "               \"name\":\"prompt\",\n"
				+ "               \"value\":\"User Name\"\n" + "            }\n" + "         ],\n"
				+ "         \"input\":[\n" + "            {\n" + "               \"name\":\"IDToken1\",\n"
				+ "               \"value\":\"jane.doe\"\n" + "            }\n" + "         ],\n"
				+ "         \"_id\":0\n" + "      },\n" + "      {\n" + "         \"type\":\"PasswordCallback\",\n"
				+ "         \"output\":[\n" + "            {\n" + "               \"name\":\"prompt\",\n"
				+ "               \"value\":\"Password\"\n" + "            }\n" + "         ],\n"
				+ "         \"input\":[\n" + "            {\n" + "               \"name\":\"IDToken2\",\n"
				+ "               \"value\":\"jane.doe\"\n" + "            }\n" + "         ],\n"
				+ "         \"_id\":1\n" + "      }\n" + "   ]\n" + "}";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode callbacks = null;
		try {
			callbacks = mapper.readTree(requestBody);
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println(callbacks.get("callbacks").get(0).get("input").get(0).get("value").asText());

		String responseBody = "{\"tokenId\":\"dmAW0kat9gfrQSNi-rmXsbI_lzc.*AAJTSQACMDIAAlNLABxkSjRqK085RHUzZkdGZ0Y0ajBLK2l1Ym41THM9AAR0eXBlAANDVFMAAlMxAAIwMQ..*\",\"successUrl\":\"/console\",\"realm\":\"/legacy\"}";
	}

}