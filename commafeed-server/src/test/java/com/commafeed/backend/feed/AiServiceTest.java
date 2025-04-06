package com.commafeed.backend.feed;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.commafeed.backend.model.AIRequest;
import com.commafeed.backend.model.AIResponse;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AiServiceTest {

	@RestClient
	AiService aiService; // 注入真实客户端

	@Test
	void getSummary() {
		// 准备请求
		AIRequest request = new AIRequest();
		request.setUrl("https://xinquji.com/posts/835368?utm_campaign=xinquji-rss");

		// 调用方法
		AIResponse response = aiService.getSummary(request);
		Assertions.assertNotNull(response.getData());
	}
}