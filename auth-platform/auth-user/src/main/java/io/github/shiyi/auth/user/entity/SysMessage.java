package io.github.shiyi.auth.user.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * M11: one row of the in-app message center. Written by {@code InboxChannel}
 * (event deliveries) and by admins (announcements); read by
 * {@code MessageController}. Recipient is the username — see V12.
 */
@TableName("sys_message")
public class SysMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String recipient;

    /** "event" | "announcement". */
    private String type;

    private String event;

    private String title;

    private String body;

    private String refType;

    private String refId;

    private String link;

    private String sender;

    private LocalDateTime readAt;

    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }
    public String getRefId() { return refId; }
    public void setRefId(String refId) { this.refId = refId; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
