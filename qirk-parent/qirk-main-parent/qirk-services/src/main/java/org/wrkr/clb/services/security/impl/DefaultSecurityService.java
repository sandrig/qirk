/*
 * This file is part of the Java API to Qirk.
 * Copyright (C) 2020 Memfis LLC, Russia
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.wrkr.clb.services.security.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.wrkr.clb.model.user.User;
import org.wrkr.clb.repo.user.JDBCUserRepo;
import org.wrkr.clb.services.security.SecurityService;

@Validated
@Service
public class DefaultSecurityService extends BaseSecurityService implements SecurityService {

    @SuppressWarnings("unused")
    @Autowired
    private JDBCUserRepo userRepo;

    @Override
    public void isAuthenticated(User user) throws AuthenticationCredentialsNotFoundException {
        if (!_isAuthenticated(user)) {
            throw new AuthenticationCredentialsNotFoundException("User is not authenticated or disabled");
        }
    }

    @SuppressWarnings("unused")
    @Override
    public void authzCanReadUserProfile(User currentUser, Long userId) throws SecurityException {
        /*@formatter:off
        User user = userRepo.getEnabledByIdForSecurity(userId);
        if (user == null || !user.isDontRecommend()) {
            return; // public can be read by everyone
        }
        if (!user.getId().equals(currentUser.getId())) {
            throw new SecurityException("User can't read another user's profile");
        }
        @formatter:on*/
    }
}