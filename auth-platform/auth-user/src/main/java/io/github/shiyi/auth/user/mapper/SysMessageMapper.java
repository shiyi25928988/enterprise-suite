package io.github.shiyi.auth.user.mapper;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.shiyi.auth.user.entity.SysMessage;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface SysMessageMapper extends BaseMapper<SysMessage> {

    /**
     * Retention sweep (MessageRetention, daily): read messages past their
     * grace window and unread ones past the long window. Two criteria in one
     * statement keeps the unread leg cheap on the (recipient, read_at) index.
     */
    @Delete("""
            DELETE FROM sys_message
            WHERE (read_at IS NOT NULL AND read_at < #{readBefore})
               OR (read_at IS NULL AND create_time < #{unreadBefore})
            """)
    int purgeBefore(@Param("readBefore") LocalDateTime readBefore,
                    @Param("unreadBefore") LocalDateTime unreadBefore);
}
