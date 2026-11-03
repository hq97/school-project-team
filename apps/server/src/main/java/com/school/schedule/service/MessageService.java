package com.school.schedule.service;

import com.school.common.util.Ids;
import com.school.schedule.entity.Message;
import com.school.schedule.mapper.MessageMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 站内消息写入（首版轻量，直接写 message 表，不接 outbox worker）。
 *
 * <p>V1 表 message 无 content 列，故消息正文承载于 {@code title}；{@code category} 用英文码（如 SCHEDULE）。</p>
 */
@Service
public class MessageService {

    private final MessageMapper mapper;

    public MessageService(MessageMapper mapper) {
        this.mapper = mapper;
    }

    public void notify(String receiverId, String category, String title, String bizType, String bizId) {
        if (receiverId == null || receiverId.isBlank()) {
            return;
        }
        Message m = new Message();
        m.setId(Ids.next("msg"));
        m.setReceiverId(receiverId);
        m.setCategory(category);
        m.setTitle(title);
        m.setBizType(bizType);
        m.setBizId(bizId);
        m.setRead(false);
        m.setCreatedAt(OffsetDateTime.now());
        mapper.insert(m);
    }
}
