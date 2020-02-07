/*
 * This file is part of the Java API to Qirk.
 * Copyright (C) 2020 Memfis Inc.
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
package org.wrkr.clb.repo.mapper.project.task;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.wrkr.clb.common.jdbc.BaseMapper;
import org.wrkr.clb.model.project.task.TemporaryAttachment;
import org.wrkr.clb.model.project.task.TemporaryAttachmentMeta;

public class TemporaryAttachmentPathMapper extends BaseMapper<TemporaryAttachment> {

    public TemporaryAttachmentPathMapper(String attachmentTableName) {
        super(attachmentTableName);
    }

    @Override
    public String generateSelectColumnsStatement() {
        return generateSelectColumnStatement(TemporaryAttachmentMeta.path);
    }

    @Override
    public TemporaryAttachment mapRow(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
        TemporaryAttachment attachment = new TemporaryAttachment();
        attachment.setPath(rs.getString(generateColumnAlias(TemporaryAttachmentMeta.path)));
        return attachment;
    }
}
