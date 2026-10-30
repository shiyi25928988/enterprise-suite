package io.github.shiyi.auth.workflow.form;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * W1-3: dynamic form schema for one process definition key. Kept out of the
 * BPMN on purpose — the schema changes at business speed (HR adds a field),
 * the process definition only changes when the flow itself does.
 */
@TableName("sys_flow_form")
public class FlowFormEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Process definition key this form belongs to, e.g. "leave-approval". */
    private String processKey;

    private String formName;

    /** JSON: {"fields":[{"key","label","type","required","options":[…]}]} */
    private String schemaJson;

    private String updatedBy;

    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProcessKey() { return processKey; }
    public void setProcessKey(String processKey) { this.processKey = processKey; }
    public String getFormName() { return formName; }
    public void setFormName(String formName) { this.formName = formName; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
