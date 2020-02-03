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
package org.wrkr.clb.repo.mapper.organization;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.wrkr.clb.common.jdbc.BaseMapper;
import org.wrkr.clb.model.organization.OrganizationMember;
import org.wrkr.clb.model.organization.OrganizationMemberMeta;
import org.wrkr.clb.repo.mapper.user.PublicUserMapper;

public class ShortOrganizationMemberWithUserMapper extends BaseMapper<OrganizationMember> {

    protected PublicUserMapper userMapper;

    protected ShortOrganizationMemberWithUserMapper(String orgMemberTableName) {
        super(orgMemberTableName);
    }

    public ShortOrganizationMemberWithUserMapper(String orgMemberTableName, String userTableName) {
        super(orgMemberTableName);
        userMapper = new PublicUserMapper(userTableName);
    }

    @Override
    public String generateSelectColumnsStatement() {
        return generateSelectColumnStatement(OrganizationMemberMeta.id) + ", " +
                generateSelectColumnStatement(OrganizationMemberMeta.userId) + ", " +
                generateSelectColumnStatement(OrganizationMemberMeta.enabled) + ", " +
                generateSelectColumnStatement(OrganizationMemberMeta.fired) + ", " +
                userMapper.generateSelectColumnsStatement();
    }

    @Override
    public OrganizationMember mapRow(ResultSet rs, int rowNum) throws SQLException {
        OrganizationMember member = new OrganizationMember();

        member.setId(rs.getLong(generateColumnAlias(OrganizationMemberMeta.id)));
        member.setUserId(rs.getLong(generateColumnAlias(OrganizationMemberMeta.userId)));
        member.setEnabled(rs.getBoolean(generateColumnAlias(OrganizationMemberMeta.enabled)));
        member.setFired(rs.getBoolean(generateColumnAlias(OrganizationMemberMeta.fired)));

        member.setUser(userMapper.mapRow(rs, rowNum));

        return member;
    }
}