package com.commafeed.backend.task;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.commafeed.backend.dao.FeedEntryDAO;
import com.commafeed.backend.feed.AiService;
import com.commafeed.backend.model.AIRequest;
import com.commafeed.backend.model.AIResponse;
import com.commafeed.backend.model.FeedEntry;

import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class SummaryFeedsTask extends ScheduledTask {

	private final FeedEntryDAO feedEntryDAO;

	@RestClient
	private final AiService aiService;

	public SummaryFeedsTask(FeedEntryDAO feedEntryDAO, @RestClient AiService aiService) {
		this.feedEntryDAO = feedEntryDAO;
		this.aiService = aiService;
	}

	@Override
	public void run() {

		Long start = new Date().getTime();
		log.info("[{}]start summary feeds..", start);
		List<FeedEntry> entryList = getFeedEntries();
		Integer total = 0;
		for (FeedEntry entry : entryList) {
			Integer cost = getAndUpdate(entry);
			total += cost;
		}
		log.info("[{}]end summary feeds,total size ={},time cost ={},token cost={}", start, entryList.size(), new Date().getTime() - start,
				total);
	}

	@Transactional
	public List<FeedEntry> getFeedEntries() {
		List<FeedEntry> entryList = feedEntryDAO.findNotSummaries(10);
		return entryList;
	}

	@Transactional
	public Integer getAndUpdate(FeedEntry entry) {
		AIResponse.RespData summary = getSummary(entry.getUrl());
		if (summary == null) {
			return -1;
		}
		entry.setSummary(summary.getSummary());

		feedEntryDAO.saveOrUpdate(entry);
		return summary.getInputToken() + summary.getOutputToken();
	}

	private AIResponse.RespData getSummary(String url) {
		long start = new Date().getTime();
		try {
			AIRequest request = new AIRequest();
			request.setUrl(url);
			AIResponse resp = aiService.getSummary(request);
			if (resp.getCode() == 0 && resp.getData() != null) {
				AIResponse.RespData data = resp.getData();
				log.info("url={},fetch summary resp={},{},cost = {}", url, data.getTags(), data.getSummary(), new Date().getTime() - start);
				return data;
			}
		} catch (Exception e) {
			log.error("url={},fetch summary failed,cost={}", url, new Date().getTime() - start, e);
		}
		return null;
	}

	@Override
	public long getInitialDelay() {
		return 1;
	}

	@Override
	public long getPeriod() {
		return 10;
	}

	@Override
	public TimeUnit getTimeUnit() {
		return TimeUnit.MINUTES;
	}

}
