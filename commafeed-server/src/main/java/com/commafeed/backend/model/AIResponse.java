package com.commafeed.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class AIResponse {
	private int code;
	private RespData data;

	@Data
	public static class RespData {
		private String summary;
		private String tags;
		@JsonProperty("input_token")
		private Integer inputToken;
		@JsonProperty("output_token")
		private Integer outputToken;

	}
}
