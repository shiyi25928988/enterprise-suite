package io.github.shiyi.auth.user.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.shiyi.auth.user.entity.SysOrg;

public interface SysOrgMapper extends BaseMapper<SysOrg> {

    /** Ids of the whole subtree rooted at orgId (inclusive), via materialized path. */
    @org.apache.ibatis.annotations.Select("""
            SELECT id FROM sys_org
            WHERE org_path LIKE CONCAT(#{orgPath}, '%') AND deleted = 0
            """)
    List<Long> selectSubtreeIds(String orgPath);
}
