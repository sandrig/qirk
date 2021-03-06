package org.wrkr.clb.chat.services;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.wrkr.clb.chat.model.sql.BaseChatMessage;
import org.wrkr.clb.chat.services.dto.MessageDTO;
import org.wrkr.clb.chat.services.sql.SQLChatService;
import org.wrkr.clb.common.crypto.token.chat.ChatTokenData;
import org.wrkr.clb.common.jms.message.statistics.NewCommentMessage;
import org.wrkr.clb.common.jms.services.StatisticsSender;

public abstract class DefaultChatService implements ChatService {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    @Autowired
    protected StatisticsSender statisticsSender;

    protected abstract SQLChatService getSQLService();

    @Override
    public List<MessageDTO> getLastMessages(ChatTokenData tokenData, Long timestamp) {
        if (timestamp == null) {
            timestamp = System.currentTimeMillis();
        }

        long startTime = System.currentTimeMillis();
        List<MessageDTO> dtoList = getSQLService().getLastMessages(tokenData, timestamp);
        long resultTime = System.currentTimeMillis() - startTime;
        if (LOG.isDebugEnabled()) {
            LOG.debug("processed method getLastMessages for chat type " + tokenData.chatType + " in cassandra in " +
                    resultTime + " ms");
        }

        return dtoList;
    }

    protected void deleteMessageFromMariaDB(ChatTokenData tokenData, long timestamp) {
        getSQLService().delete(tokenData, timestamp);
    }

    protected void sendNewMessageStatistics(ChatTokenData tokenData, BaseChatMessage messageEntity) {
        statisticsSender.send(new NewCommentMessage(
                messageEntity.getChatType(), tokenData.chatId, messageEntity.getTimestamp()));
    }

    @Override
    public MessageDTO saveMessage(ChatTokenData tokenData, String message) throws SQLException {
        UUID uuid = UUID.randomUUID();

        BaseChatMessage messageEntity = getSQLService().saveMessage(tokenData, message, uuid);
        sendNewMessageStatistics(tokenData, messageEntity);

        return MessageDTO.fromEntity(messageEntity);
    }
}
