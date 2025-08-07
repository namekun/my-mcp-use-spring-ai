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
package org.springframework.ai.mcp.server;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 미국 국립 기상청 API를 사용하여 날씨 정보를 제공하는 서비스 클래스
 */
@Service
public class WeatherService {

	private static final String BASE_URL = "https://api.weather.gov";

	private final RestClient restClient;

	/**
	 * WeatherService 생성자
	 * RestClient를 초기화하고 기본 설정을 구성합니다.
	 */
	public WeatherService() {

		this.restClient = RestClient.builder()
			.baseUrl(BASE_URL)
			.defaultHeader("Accept", "application/geo+json")
			.defaultHeader("User-Agent", "WeatherApiClient/1.0 (your@email.com)")
			.build();
	}

	/**
	 * 위도/경도 좌표에 대한 지점 정보를 나타내는 레코드
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Points(@JsonProperty("properties") Props properties) {
		/**
		 * 예보 URL을 포함하는 속성 정보
		 */
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Props(@JsonProperty("forecast") String forecast) {
		}
	}

	/**
	 * 날씨 예보 정보를 나타내는 레코드
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Forecast(@JsonProperty("properties") Props properties) {
		/**
		 * 예보 기간들의 목록을 포함하는 속성 정보
		 */
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Props(@JsonProperty("periods") List<Period> periods) {
		}

		/**
		 * 개별 예보 기간의 상세 정보를 나타내는 레코드
		 */
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Period(@JsonProperty("number") Integer number, 
				@JsonProperty("name") String name,
				@JsonProperty("startTime") String startTime, 
				@JsonProperty("endTime") String endTime,
				@JsonProperty("isDaytime") Boolean isDayTime, 
				@JsonProperty("temperature") Integer temperature,
				@JsonProperty("temperatureUnit") String temperatureUnit,
				@JsonProperty("temperatureTrend") String temperatureTrend,
				@JsonProperty("probabilityOfPrecipitation") Map probabilityOfPrecipitation,
				@JsonProperty("windSpeed") String windSpeed, 
				@JsonProperty("windDirection") String windDirection,
				@JsonProperty("icon") String icon, 
				@JsonProperty("shortForecast") String shortForecast,
				@JsonProperty("detailedForecast") String detailedForecast) {
		}
	}

	/**
	 * 기상 경보 정보를 나타내는 레코드
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Alert(@JsonProperty("features") List<Feature> features) {

		/**
		 * 경보의 개별 특성을 나타내는 레코드
		 */
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Feature(@JsonProperty("properties") Properties properties) {
		}

		/**
		 * 경보의 상세 속성 정보를 나타내는 레코드
		 */
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Properties(@JsonProperty("event") String event, 
				@JsonProperty("areaDesc") String areaDesc,
				@JsonProperty("severity") String severity, 
				@JsonProperty("description") String description,
				@JsonProperty("instruction") String instruction) {
		}
	}

	/**
	 * 특정 위도/경도에 대한 날씨 예보를 조회합니다
	 * @param latitude 위도
	 * @param longitude 경도
	 * @return 해당 위치의 날씨 예보 문자열
	 * @throws RestClientException 요청이 실패할 경우 발생
	 */
	@Tool(description = "특정 위도/경도에 대한 날씨 예보를 가져옵니다")
	public String getWeatherForecastByLocation(double latitude, double longitude) {

		var points = restClient.get()
			.uri("/points/{latitude},{longitude}", latitude, longitude)
			.retrieve()
			.body(Points.class);

		var forecast = restClient.get().uri(points.properties().forecast()).retrieve().body(Forecast.class);

		String forecastText = forecast.properties().periods().stream().map(p -> {
			return String.format("""
					%s:
					온도: %s %s
					바람: %s %s
					예보: %s
					""", p.name(), p.temperature(), p.temperatureUnit(), p.windSpeed(), p.windDirection(),
					p.detailedForecast());
		}).collect(Collectors.joining());

		return forecastText;
	}

	/**
	 * 특정 지역에 대한 기상 경보를 조회합니다
	 * @param state 지역 코드. 미국 주의 2글자 코드 (예: CA, NY)
	 * @return 사람이 읽기 쉬운 경보 정보
	 * @throws RestClientException 요청이 실패할 경우 발생
	 */
	@Tool(description = "미국 주에 대한 기상 경보를 가져옵니다. 입력값은 2글자 미국 주 코드입니다 (예: CA, NY)")
	public String getAlerts(String state) {
		Alert alert = restClient.get().uri("/alerts/active/area/{state}", state).retrieve().body(Alert.class);

		return alert.features()
			.stream()
			.map(f -> String.format("""
					이벤트: %s
					지역: %s
					심각도: %s
					설명: %s
					지침: %s
					""", f.properties().event(), f.properties.areaDesc(), f.properties.severity(),
					f.properties.description(), f.properties.instruction()))
			.collect(Collectors.joining("\n"));
	}

	/**
	 * 테스트용 메인 메서드
	 * 시애틀의 날씨 예보와 뉴욕의 기상 경보를 출력합니다
	 */
	public static void main(String[] args) {
		WeatherService client = new WeatherService();
		System.out.println(client.getWeatherForecastByLocation(47.6062, -122.3321));
		System.out.println(client.getAlerts("NY"));
	}

}