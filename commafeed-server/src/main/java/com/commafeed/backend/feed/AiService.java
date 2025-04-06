package com.commafeed.backend.feed;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.commafeed.backend.model.AIRequest;
import com.commafeed.backend.model.AIResponse;

@Path("/api")
@RegisterRestClient(configKey = "post-api")
public interface AiService {
	@POST
	@Path("/summary")
	@Consumes("application/json")
	@Produces("application/json")
	AIResponse getSummary(AIRequest link);
}
