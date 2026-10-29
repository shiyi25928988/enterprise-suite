package io.github.shiyi.auth.user.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.user.entity.SysOrg;
import io.github.shiyi.auth.user.entity.SysRole;
import io.github.shiyi.auth.user.entity.SysUser;
import io.github.shiyi.auth.user.entity.SysUserRole;
import io.github.shiyi.auth.user.mapper.SysOrgMapper;
import io.github.shiyi.auth.user.mapper.SysRoleMapper;
import io.github.shiyi.auth.user.mapper.SysUserMapper;
import io.github.shiyi.auth.user.mapper.SysUserRoleMapper;
import io.github.shiyi.auth.user.util.PasswordPolicy;

/**
 * Batch user import / export as xlsx (Apache POI), JDBC mode only —
 * see UserDomainConfig for the conditional bean wiring.
 *
 * <p>Workbook layout (sheet 1 "用户", sheet 2 "填写说明"):
 * username / realName / password / employeeNo / mobile / email / orgCode / roleCodes.
 * Import is partial-success: every row is validated then inserted in its own
 * transaction, failures are collected per row and reported back.</p>
 */
public class UserImportExportService {

    /** Hard cap protecting the server from a pathological upload. */
    public static final int MAX_IMPORT_ROWS = 5000;
    static final String DEFAULT_PASSWORD = "Init@12345";

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_.-]{1,64}$");
    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final String[] HEADERS = {
            "用户名*", "姓名*", "初始密码", "员工编号", "手机号", "邮箱", "组织编码", "角色编码(逗号分隔)"
    };
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserMapper userMapper;
    private final SysOrgMapper orgMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final TransactionTemplate tx;

    public UserImportExportService(SysUserMapper userMapper, SysOrgMapper orgMapper,
                                   SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper,
                                   PlatformTransactionManager txManager) {
        this.userMapper = userMapper;
        this.orgMapper = orgMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------- export ----------

    /** Export rows matching the same filters as the paged list API, unpaginated. */
    public byte[] export(String keyword, Long orgId, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getRealName, keyword))
                .eq(status != null, SysUser::getStatus, status)
                .orderByAsc(SysUser::getId);

        if (orgId != null) {
            SysOrg org = orgMapper.selectById(orgId);
            if (org != null) {
                wrapper.in(SysUser::getOrgId, orgMapper.selectSubtreeIds(org.getOrgPath()));
            } else {
                wrapper.eq(SysUser::getOrgId, orgId);
            }
        }

        List<SysUser> users = userMapper.selectList(wrapper);

        Map<Long, SysOrg> orgs = new HashMap<>();
        for (SysOrg o : orgMapper.selectList(null)) {
            orgs.put(o.getId(), o);
        }
        Map<Long, String> roleCodesByUserId = roleCodesByUserId();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(wb);
            Sheet sheet = wb.createSheet("用户");
            Row header = sheet.createRow(0);
            String[] cols = { "ID", "用户名", "姓名", "员工编号", "手机号", "邮箱",
                    "组织编码", "组织名称", "角色编码", "状态", "最后登录时间", "创建时间" };
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 16 * 256);
            }

            int r = 1;
            for (SysUser u : users) {
                Row row = sheet.createRow(r++);
                SysOrg org = u.getOrgId() == null ? null : orgs.get(u.getOrgId());
                row.createCell(0).setCellValue(u.getId());
                row.createCell(1).setCellValue(u.getUsername());
                row.createCell(2).setCellValue(nullToEmpty(u.getRealName()));
                row.createCell(3).setCellValue(nullToEmpty(u.getEmployeeNo()));
                row.createCell(4).setCellValue(nullToEmpty(u.getMobile()));
                row.createCell(5).setCellValue(nullToEmpty(u.getEmail()));
                row.createCell(6).setCellValue(org == null ? "" : nullToEmpty(org.getOrgCode()));
                row.createCell(7).setCellValue(org == null ? "" : nullToEmpty(org.getOrgName()));
                row.createCell(8).setCellValue(roleCodesByUserId.getOrDefault(u.getId(), ""));
                row.createCell(9).setCellValue(switch (u.getStatus() == null ? 0 : u.getStatus()) {
                    case 1 -> "启用";
                    case 2 -> "锁定";
                    default -> "停用";
                });
                row.createCell(10).setCellValue(u.getLastLoginTime() == null ? "" : u.getLastLoginTime().format(TS));
                row.createCell(11).setCellValue(u.getCreateTime() == null ? "" : u.getCreateTime().format(TS));
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "failed to build export workbook");
        }
    }

    /** Downloadable import template: header + example row + a 说明 sheet. */
    public byte[] importTemplate() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(wb);
            Sheet sheet = wb.createSheet("用户");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 22 * 256);
            }
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("zhangsan01");
            example.createCell(1).setCellValue("张三");
            example.createCell(2).setCellValue("Init@12345");
            example.createCell(3).setCellValue("E1001");
            example.createCell(4).setCellValue("13800138000");
            example.createCell(5).setCellValue("zhangsan01@example.com");
            example.createCell(6).setCellValue("HQ");
            example.createCell(7).setCellValue("ADMIN");

            Sheet notes = wb.createSheet("填写说明");
            String[][] notes_ = {
                    { "列", "说明" },
                    { "用户名*", "必填；字母/数字/_/./-，≤64 字符，系统内唯一" },
                    { "姓名*", "必填；≤64 字符" },
                    { "初始密码", "可空；留空则使用默认密码 " + DEFAULT_PASSWORD + "；≥8 位且含大小写/数字/特殊字符至少 3 类" },
                    { "员工编号", "可空" },
                    { "手机号", "可空；填写时须为 1 开头的 11 位大陆手机号" },
                    { "邮箱", "可空；填写时须包含 @" },
                    { "组织编码", "可空；须为系统已有组织编码（组织管理中查看）" },
                    { "角色编码(逗号分隔)", "可空；多个用英文逗号分隔，如 ADMIN,OPERATOR；须为系统已有角色编码" },
            };
            for (int i = 0; i < notes_.length; i++) {
                Row row = notes.createRow(i);
                row.createCell(0).setCellValue(notes_[i][0]);
                row.createCell(1).setCellValue(notes_[i][1]);
            }
            notes.setColumnWidth(0, 22 * 256);
            notes.setColumnWidth(1, 80 * 256);
            return toBytes(wb);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "failed to build template workbook");
        }
    }

    // ---------- import ----------

    public ImportResult importUsers(InputStream in) {
        Map<String, Long> orgIdByCode = new HashMap<>();
        for (SysOrg o : orgMapper.selectList(null)) {
            orgIdByCode.put(o.getOrgCode(), o.getId());
        }
        Map<String, Long> roleIdByCode = new HashMap<>();
        for (SysRole role : roleMapper.selectList(null)) {
            roleIdByCode.put(role.getRoleCode(), role.getId());
        }
        Set<String> existingUsernames = new HashSet<>();
        for (SysUser u : userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getUsername))) {
            existingUsernames.add(u.getUsername());
        }

        List<RowError> errors = new ArrayList<>();
        int total = 0;
        int ok = 0;

        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Set<String> seenInFile = new HashSet<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                total++;
                if (total > MAX_IMPORT_ROWS) {
                    errors.add(new RowError(i + 1, "", "超过单次导入上限 " + MAX_IMPORT_ROWS + " 行，剩余行未处理"));
                    break;
                }
                String[] cells = readCells(row, HEADERS.length);
                String username = cells[0].trim();
                String realName = cells[1].trim();
                String password = cells[2].trim();
                String employeeNo = cells[3].trim();
                String mobile = cells[4].trim();
                String email = cells[5].trim();
                String orgCode = cells[6].trim();
                String roleCodes = cells[7].trim();

                String err = validate(username, realName, password, mobile, email,
                        orgCode, roleCodes, orgIdByCode, roleIdByCode, existingUsernames, seenInFile);
                if (err != null) {
                    errors.add(new RowError(i + 1, username, err));
                    continue;
                }

                final String fUsername = username;
                final String fRealName = realName;
                final String fPassword = password.isEmpty() ? DEFAULT_PASSWORD : password;
                final String fEmployeeNo = employeeNo.isEmpty() ? null : employeeNo;
                final String fMobile = mobile.isEmpty() ? null : mobile;
                final String fEmail = email.isEmpty() ? null : email;
                final Long fOrgId = orgCode.isEmpty() ? null : orgIdByCode.get(orgCode);
                final List<Long> fRoleIds = roleCodes.isEmpty() ? List.of()
                        : java.util.Arrays.stream(roleCodes.split(","))
                                .map(String::trim).map(roleIdByCode::get).toList();

                try {
                    tx.executeWithoutResult(s -> {
                        SysUser user = new SysUser();
                        user.setUsername(fUsername);
                        user.setPassword(encoder().encode(fPassword));
                        user.setRealName(fRealName);
                        user.setEmployeeNo(fEmployeeNo);
                        user.setMobile(fMobile);
                        user.setEmail(fEmail);
                        user.setOrgId(fOrgId);
                        user.setSource(1);
                        user.setStatus(1);
                        user.setFailCount(0);
                        // default sheet password is shared knowledge → force a change
                        user.setMustChangePassword(1);
                        user.setPasswordUpdateTime(LocalDateTime.now());
                        userMapper.insert(user);
                        for (Long roleId : fRoleIds) {
                            SysUserRole ur = new SysUserRole();
                            ur.setUserId(user.getId());
                            ur.setRoleId(roleId);
                            userRoleMapper.insert(ur);
                        }
                    });
                    ok++;
                    existingUsernames.add(username);
                    seenInFile.add(username);
                } catch (Exception e) {
                    errors.add(new RowError(i + 1, username, "写入失败: " + rootMessage(e)));
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "无法解析 Excel 文件，请使用下载的模板填写后上传");
        }
        return new ImportResult(total, ok, errors.size(), errors);
    }

    // ---------- helpers ----------

    private String validate(String username, String realName, String password, String mobile,
                            String email, String orgCode, String roleCodes,
                            Map<String, Long> orgIdByCode, Map<String, Long> roleIdByCode,
                            Set<String> existingUsernames, Set<String> seenInFile) {
        if (username.isEmpty()) {
            return "用户名为空";
        }
        if (!USERNAME.matcher(username).matches()) {
            return "用户名只允许字母/数字/_/./-，且 ≤64 字符";
        }
        if (existingUsernames.contains(username)) {
            return "用户名已存在";
        }
        if (!seenInFile.add(username)) {
            return "文件内用户名重复";
        }
        if (realName.isEmpty()) {
            return "姓名为空";
        }
        if (realName.length() > 64) {
            return "姓名超过 64 字符";
        }
        try {
            PasswordPolicy.validate(password.isEmpty() ? DEFAULT_PASSWORD : password);
        } catch (BizException e) {
            return "初始密码不合格（≥8 位，含至少 3 类字符）";
        }
        if (!mobile.isEmpty() && !MOBILE.matcher(mobile).matches()) {
            return "手机号格式错误";
        }
        if (!email.isEmpty() && !email.contains("@")) {
            return "邮箱格式错误";
        }
        if (!orgCode.isEmpty() && !orgIdByCode.containsKey(orgCode)) {
            return "组织编码不存在: " + orgCode;
        }
        if (!roleCodes.isEmpty()) {
            for (String rc : roleCodes.split(",")) {
                if (!roleIdByCode.containsKey(rc.trim())) {
                    return "角色编码不存在: " + rc.trim();
                }
            }
        }
        return null;
    }

    private org.springframework.security.crypto.password.PasswordEncoder encoder() {
        return org.springframework.security.crypto.factory.PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    private Map<Long, String> roleCodesByUserId() {
        Map<Long, String> roleCodeById = new HashMap<>();
        for (SysRole role : roleMapper.selectList(null)) {
            roleCodeById.put(role.getId(), role.getRoleCode());
        }
        Map<Long, List<Long>> byUser = new LinkedHashMap<>();
        for (SysUserRole ur : userRoleMapper.selectList(null)) {
            byUser.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>()).add(ur.getRoleId());
        }
        Map<Long, String> result = new HashMap<>();
        byUser.forEach((userId, roleIds) -> result.put(userId,
                roleIds.stream().map(id -> roleCodeById.getOrDefault(id, "?"))
                        .reduce((a, b) -> a + "," + b).orElse("")));
        return result;
    }

    private static boolean isBlankRow(Row row) {
        for (int c = 0; c < HEADERS.length; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != org.apache.poi.ss.usermodel.CellType.BLANK
                    && !new DataFormatter().formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String[] readCells(Row row, int n) {
        DataFormatter fmt = new DataFormatter();
        String[] out = new String[n];
        for (int c = 0; c < n; c++) {
            Cell cell = row.getCell(c);
            out[c] = cell == null ? "" : fmt.formatCellValue(cell).strip();
        }
        return out;
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static byte[] toBytes(Workbook wb) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "failed to serialize workbook");
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }

    /** Row-level failure detail. */
    public record RowError(int row, String username, String reason) {
    }

    /** Summary of a partial-success import. */
    public record ImportResult(int total, int succeeded, int failed, List<RowError> errors) {
    }
}
