package com.commafeed.security.mechanism;

import java.util.Optional;
import java.util.Set;

import jakarta.inject.Singleton;

import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

@Singleton
public class ApiKeyAuthenticationMecanism implements HttpAuthenticationMechanism {

	@Override
	public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
		// only authorize api key for GET requests
		// add feed entries api path ，all methods of this path are allowed
		// if (!context.request().method().name().equals("GET") || !context.request().path().startsWith("/rest/entry/mark")) {
		// return Uni.createFrom().optional(Optional.empty());
		// }

		String apiKey = context.request().getParam("apiKey");
		if (apiKey == null) {
			return Uni.createFrom().optional(Optional.empty());
		}

		TokenCredential token = new TokenCredential(apiKey, "apiKey");
		TokenAuthenticationRequest request = new TokenAuthenticationRequest(token);
		return identityProviderManager.authenticate(request);
	}

	@Override
	public Uni<ChallengeData> getChallenge(RoutingContext context) {
		return Uni.createFrom().optional(Optional.empty());
	}

	@Override
	public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
		return Set.of(TokenAuthenticationRequest.class);
	}
}
