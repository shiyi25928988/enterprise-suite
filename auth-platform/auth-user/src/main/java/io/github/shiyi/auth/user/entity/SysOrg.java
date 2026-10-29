package io.github.shiyi.auth.user.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * sys_org row: company → plant → workshop → line hierarchy
 * with a materialized path for subtree queries / data scope.
 */
@Data
@TableName("sys_org")
public class SysOrg {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String orgCode;

    private String orgName;

    /** 1 company 2 plant 3 workshop 4 line 5 department */
    private Integer orgType;

    /** materialized path, e.g. /1/5/12/ */
    private String orgPath;

    private Integer sortNo;

    /** Directory sync origin: "wecom" / "dingtalk" / null for local orgs */
    private String externalSource;

    /** Department id inside the external directory (unique per source) */
    private String externalId;

    private Long leaderId;

    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
