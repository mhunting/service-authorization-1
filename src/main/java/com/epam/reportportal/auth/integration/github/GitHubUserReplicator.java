/*
 * Copyright 2016 EPAM Systems
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

import com.epam.reportportal.auth.integration.AbstractUserReplicator;
import com.epam.reportportal.auth.oauth.UserSynchronizationException;
import com.epam.ta.reportportal.commons.validation.BusinessRule;
import com.epam.ta.reportportal.database.BinaryData;
import com.epam.ta.reportportal.database.DataStorage;
import com.epam.ta.reportportal.database.dao.ProjectRepository;
import com.epam.ta.reportportal.database.dao.UserRepository;
import com.epam.ta.reportportal.database.entity.user.User;
import com.epam.ta.reportportal.database.entity.user.UserRole;
import com.epam.ta.reportportal.database.entity.user.UserType;
import com.epam.ta.reportportal.database.personal.PersonalProjectService;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.google.common.base.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Objects;

import static com.epam.ta.reportportal.commons.EntityUtils.normalizeId;

/**
 * Replicates GitHub account info with internal ReportPortal's database
 *
 * @author <a href="mailto:andrei_varabyeu@epam.com">Andrei Varabyeu</a>
 */
@Component
public class GitHubUserReplicator extends AbstractUserReplicator {

    private final String githubBaseUrl;

    @Autowired
    public GitHubUserReplicator(UserRepository userRepository, ProjectRepository projectRepository, DataStorage dataStorage,
                                PersonalProjectService personalProjectService,
                                @Value("${rp.auth.github.apiUrl:https://api.github.com}") String githubBaseUrl) {
        super(userRepository, projectRepository, personalProjectService, dataStorage);
        this.githubBaseUrl=githubBaseUrl;
    }

    public User synchronizeUser(String accessToken) {
        GitHubClient gitHubClient = GitHubClient.withAccessToken(accessToken, this.githubBaseUrl);
        UserResource userInfo = gitHubClient.getUser();
        User user = userRepository.findOne(normalizeId(userInfo.login));
        BusinessRule.expect(user, Objects::nonNull).verify(ErrorType.USER_NOT_FOUND, userInfo.login);
        BusinessRule.expect(user.getType(), userType -> Objects.equals(userType, UserType.GITHUB))
                .verify(ErrorType.INCORRECT_AUTHENTICATION_TYPE, "User '" + userInfo.login + "' is not GitHUB user");
        user.setFullName(userInfo.name);
        user.getMetaInfo().setSynchronizationDate(Date.from(ZonedDateTime.now().toInstant()));

        String newPhotoId = uploadAvatar(gitHubClient, userInfo.login, userInfo.avatarUrl);
        if (!Strings.isNullOrEmpty(newPhotoId)) {
            dataStorage.deleteData(user.getPhotoId());
            user.setPhotoId(newPhotoId);
        }
        userRepository.save(user);
        return user;
    }

    /**
     * Replicates GitHub user to internal database (if does NOT exist). Creates personal project for that user
     *
     * @param accessToken Access token to access GitHub
     * @return Internal User representation
     */
    public User replicateUser(String accessToken) {
        GitHubClient gitHubClient = GitHubClient.withAccessToken(accessToken, this.githubBaseUrl);
        UserResource userInfo = gitHubClient.getUser();
        return replicateUser(userInfo, gitHubClient);
    }

    /**
     * Replicates GitHub user to internal database (if does NOT exist). Creates personal project for that user
     *
     * @param userInfo     GitHub user to be replicated
     * @param gitHubClient Configured github client
     * @return Internal User representation
     */
    public User replicateUser(UserResource userInfo, GitHubClient gitHubClient) {
        String login = normalizeId(userInfo.login);
        User user = userRepository.findOne(login);
        if (null == user) {
            user = new User();
            user.setLogin(login);

            String email = userInfo.email;
            if (Strings.isNullOrEmpty(email)) {
                email = gitHubClient.getUserEmails().stream().filter(EmailResource::isVerified).filter(EmailResource::isPrimary).findAny()
                        .get().getEmail();
            }
            email = normalizeId(email);
            checkEmail(email);
            user.setEmail(email);

            if (!Strings.isNullOrEmpty(userInfo.name)) {
                user.setFullName(userInfo.name);
            }

            user.setMetaInfo(defaultMetaInfo());

            user.setType(UserType.GITHUB);
            user.setRole(UserRole.USER);
            Object avatarUrl = userInfo.avatarUrl;
            user.setPhotoId(uploadAvatar(gitHubClient, login, avatarUrl));

            user.setIsExpired(false);

            user.setDefaultProject(generatePersonalProject(user));
            userRepository.save(user);

        } else if (!UserType.GITHUB.equals(user.getType())) {
            //if user with such login exists, but it's not GitHub user than throw an exception
            throw new UserSynchronizationException("User with login '" + user.getId() + "' already exists");
        }
        return user;
    }

    private String uploadAvatar(GitHubClient gitHubClient, String login, Object avatarUrl) {
        String photoId = null;
        if (null != avatarUrl) {
            ResponseEntity<Resource> photoRs = gitHubClient.downloadResource(avatarUrl.toString());
            try (InputStream photoStream = photoRs.getBody().getInputStream()) {
                BinaryData photo = new BinaryData(photoRs.getHeaders().getContentType().toString(), photoRs.getBody().contentLength(),
                        photoStream);
                photoId = uploadPhoto(login, photo);
            } catch (IOException e) {
                LOGGER.error("Unable to load photo for user {}", login);
            }
        }
        return photoId;
    }

}
