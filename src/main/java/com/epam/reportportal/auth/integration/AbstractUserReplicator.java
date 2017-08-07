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
package com.epam.reportportal.auth.integration;

import com.epam.reportportal.auth.oauth.UserSynchronizationException;
import com.epam.ta.reportportal.database.dao.ProjectRepository;
import com.epam.ta.reportportal.database.dao.UserRepository;
import com.epam.ta.reportportal.database.entity.Project;
import com.epam.ta.reportportal.database.entity.user.User;
import com.epam.ta.reportportal.database.personal.PersonalProjectService;
import com.epam.ta.reportportal.database.search.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

import static com.epam.ta.reportportal.database.search.FilterCondition.builder;

/**
 * @author Andrei Varabyeu
 */
public class AbstractUserReplicator {

	protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractUserReplicator.class);

	protected final UserRepository userRepository;
	protected final ProjectRepository projectRepository;
	protected final PersonalProjectService personalProjectService;

	public AbstractUserReplicator(UserRepository userRepository, ProjectRepository projectRepository,
			PersonalProjectService personalProjectService) {
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
		this.personalProjectService = personalProjectService;
	}

	/**
	 * Generates personal project if does NOT exists
	 *
	 * @param user Owner of personal project
	 * @return Created project name
	 */
	protected String generatePersonalProject(User user) {
		Optional<String> projectName = projectRepository.findPersonalProjectName(user.getLogin());
		return projectName.orElseGet(() -> {
			Project personalProject = personalProjectService.generatePersonalProject(user);
			projectRepository.save(personalProject);
			return personalProject.getId();
		});
	}

	/**
	 * Generates default metainfo
	 *
	 * @return Default meta info
	 */
	protected User.MetaInfo defaultMetaInfo() {
		User.MetaInfo metaInfo = new User.MetaInfo();
		Date now = Date.from(ZonedDateTime.now().toInstant());
		metaInfo.setLastLogin(now);
		metaInfo.setSynchronizationDate(now);
		return metaInfo;
	}

	/**
	 * Checks email is available
	 *
	 * @param email email to check
	 */
	protected void checkEmail(String email) {
		if (userRepository.exists(Filter.builder().withTarget(User.class).withCondition(builder().eq("email", email).build()).build())) {
			throw new UserSynchronizationException("User with email '" + email + "' already exists");
		}
	}
}