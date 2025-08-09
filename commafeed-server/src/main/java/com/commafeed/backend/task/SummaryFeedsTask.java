package com.commafeed.backend.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.commafeed.backend.dao.FeedEntryDAO;
import com.commafeed.backend.dao.FeedEntryStatusDAO;
import com.commafeed.backend.dao.FeedSubscriptionDAO;
import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.feed.AiService;
import com.commafeed.backend.feed.FeedEntryKeyword;
import com.commafeed.backend.model.AIRequest;
import com.commafeed.backend.model.AIResponse;
import com.commafeed.backend.model.FeedEntry;
import com.commafeed.backend.model.FeedEntryStatus;
import com.commafeed.backend.model.FeedSubscription;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserSettings.ReadingOrder;

import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class SummaryFeedsTask extends ScheduledTask {

	private final FeedEntryDAO feedEntryDAO;
	private final FeedEntryStatusDAO feedEntryStatusDAO;
	private final FeedSubscriptionDAO feedSubscriptionDAO;

	private final UserDAO userDAO;
	private final List<String> prefixs = List.of("https://xinquji.com");
	@RestClient
	private final AiService aiService;

	public SummaryFeedsTask(FeedEntryDAO feedEntryDAO, @RestClient AiService aiService, FeedEntryStatusDAO feedEntryStatusDAO,
			UserDAO userDAO, FeedSubscriptionDAO feedSubscriptionDAO) {
		this.feedEntryDAO = feedEntryDAO;
		this.aiService = aiService;
		this.feedEntryStatusDAO = feedEntryStatusDAO;
		this.userDAO = userDAO;
		this.feedSubscriptionDAO = feedSubscriptionDAO;
	}

	@Override
	public void run() {

		Long start = new Date().getTime();
		log.info("[{}]start summary feeds..", start);
		List<FeedEntryStatus> statuses = getAllStatus();
		for (FeedEntryStatus status : statuses) {
			log.info("[{}]user {},start summary feeds,url={},retry={}", start, status.getUser().getId(), status.getEntry().getGuid(),
					status.getEntry().getRetryTimes());
			User user = status.getUser();
			FeedEntry entry = status.getEntry();
			try {
				if (!prefixs.stream().anyMatch(prdct -> entry.getUrl().contains(prdct))) {
					log.info("url={} is in prefixs,skip", entry.getUrl());
					continue;
				}
				if (entry.getRetryTimes() != null && entry.getRetryTimes() > 3) {
					log.info("url={},retryTimes={},skip", entry.getUrl(), entry.getRetryTimes());
					continue;
				}
				if (entry.getSummary() != null && !entry.getSummary().isEmpty()) {
					log.info("url={},summary is not empty,skip", entry.getUrl());
					continue;
				}
				AIResponse.RespData summaryResp = summary(entry.getUrl());
				updateEntry(summaryResp, entry);
				log.info("[{}]user {},end summary feeds,total size ={},time cost ={},token cost={}", start, user.getId(),
						new Date().getTime() - start, summaryResp.getInputToken() + summaryResp.getOutputToken());
			} catch (Exception e) {
				log.error("summary url={},failed", entry.getUrl(), e);
				updateRetryTimes(entry);
			}

		}
		log.info("end summary feeds cost={}", start, new Date().getTime() - start);
	}

	@Transactional
	public List<FeedEntryStatus> getAllStatus() {
		List<FeedEntryStatus> statuses = new ArrayList<>();
		userDAO.findAll().forEach(user -> {
			List<FeedSubscription> subs = feedSubscriptionDAO.findAll(user);
			List<FeedEntryKeyword> entryKeywords = FeedEntryKeyword.fromQueryString("");
			List<FeedEntryStatus> entryList = feedEntryStatusDAO.findBySubscriptions(user, subs, true, entryKeywords, null, 0, 1000,
					ReadingOrder.asc, true, null, null, null);
			statuses.addAll(entryList);
		});
		return statuses;
	}

	private AIResponse.RespData summary(String url) {
		long start = new Date().getTime();

		AIRequest request = new AIRequest();
		request.setUrl(url);
		AIResponse resp = aiService.getSummary(request);
		if (resp.getCode() == 0 && resp.getData() != null) {
			AIResponse.RespData data = resp.getData();
			log.info("url={},fetch summary tags={},summary={},cost = {}", url, data.getTags(), data.getSummary(),
					new Date().getTime() - start);
			return data;
		}
		throw new RuntimeException("fetch summary error=" + url);
	}

	@Transactional
	public void updateRetryTimes(FeedEntry entry) {
		Integer retryTimes = entry.getRetryTimes() == null ? 0 : entry.getRetryTimes();
		entry.setRetryTimes(retryTimes + 1);
		feedEntryDAO.saveOrUpdate(entry);
		log.info("url={},summary failed,entry retryTimes={}", entry.getUrl(), entry.getRetryTimes());
	}

	@Transactional
	public void updateEntry(AIResponse.RespData summary, FeedEntry entry) {
		entry.setSummary(summary.getSummary());
		feedEntryDAO.saveOrUpdate(entry);
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
