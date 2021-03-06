/*
 * Copyright 2017 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/service-authorization
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.reportportal.auth.integration.github;

import com.epam.reportportal.auth.AuthConfigService;
import com.epam.ta.reportportal.database.entity.settings.OAuth2LoginDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.stereotype.Component;

import javax.inject.Named;

import java.util.Arrays;

import static java.util.Optional.ofNullable;

/**
 * @author Github OAuth Provider
 */
@Component
@Named("github")
public class GithubOAuthProvider extends com.epam.reportportal.auth.oauth.OAuthProvider {

    private static final String BUTTON = "<svg aria-hidden=\"true\" height=\"28\" version=\"1.1\" viewBox=\"0 0 16 16\" width=\"28\"><path d=\"M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0 0 16 8c0-4.42-3.58-8-8-8z\"></path></svg> <span>Login with GitHub</span>";

    private final GitHubUserReplicator githubReplicator;
    private final AuthConfigService authConfigService;
    private final String tokenUrl;
    private final String authUrl;
    private final String githubBaseUrl;

    public GithubOAuthProvider(GitHubUserReplicator githubReplicator,
                               AuthConfigService authConfigService,
                               @Value("${rp.auth.github.tokenUrl:https://github.com/login/oauth/access_token}") String tokenUrl,
                               @Value("${rp.auth.github.authUrl:https://github.com/login/oauth/authorize}") String authUrl,
                               @Value("${rp.auth.github.apiUrl:https://api.github.com}") String githubBaseUrl) {
        super("github", BUTTON, true);
        this.githubReplicator = githubReplicator;
        this.authConfigService = authConfigService;
        this.tokenUrl = tokenUrl;
        this.authUrl = authUrl;
        this.githubBaseUrl = githubBaseUrl;
    }

    @Override
    public void applyDefaults(OAuth2LoginDetails details) {
        details.setScope(ofNullable(details.getScope()).orElse(Arrays.asList("read:user", "user:email")));
        details.setGrantType(ofNullable(details.getGrantType()).orElse("authorization_code"));

        details.setAccessTokenUri(
                ofNullable(details.getAccessTokenUri()).orElse(this.tokenUrl));
        details.setUserAuthorizationUri(
                ofNullable(details.getUserAuthorizationUri()).orElse(this.authUrl));
        details.setClientAuthenticationScheme(ofNullable(details.getClientAuthenticationScheme()).orElse("form"));
    }

    @Override
    public ResourceServerTokenServices getTokenServices() {
        return new GitHubTokenServices(githubReplicator, authConfigService.getLoginDetailsSupplier(getName()), this.githubBaseUrl);
    }

    @Override
    public OAuth2RestOperations getOAuthRestOperations(OAuth2ClientContext context) {
        return authConfigService.getRestTemplate(getName(), context);
    }

}
