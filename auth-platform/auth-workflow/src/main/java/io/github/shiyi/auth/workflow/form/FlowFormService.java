package io.github.shiyi.auth.workflow.form;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.shiyi.auth.workflow.mapper.FlowFormMapper;

/**
 * W1-3: form schema storage + start-time validation. The schema is authored
 * by admins; the SPA renders it dynamically, and {@link #validate} enforces
 * required/type constraints on the flat variable map submitted at start.
 */
public class FlowFormService {

    /** Field types accepted in the schema. */
    private static final java.util.Set<String> TYPES =
            java.util.Set.of("string", "number", "boolean", "date", "enum", "text");

    private final FlowFormMapper mapper;

    public FlowFormService(FlowFormMapper mapper) {
        this.mapper = mapper;
    }

    public List<FlowFormEntity> list() {
        return mapper.selectList(new LambdaQueryWrapper<FlowFormEntity>()
                .orderByAsc(FlowFormEntity::getProcessKey));
    }

    public FlowFormEntity get(String processKey) {
        return mapper.selectOne(new LambdaQueryWrapper<FlowFormEntity>()
                .eq(FlowFormEntity::getProcessKey, processKey));
    }

    public FlowFormEntity save(String processKey, String formName, String schemaJson, String updatedBy) {
        validateSchema(schemaJson);
        FlowFormEntity existing = get(processKey);
        FlowFormEntity e = existing == null ? new FlowFormEntity() : existing;
        e.setProcessKey(processKey);
        e.setFormName(formName);
        e.setSchemaJson(schemaJson);
        e.setUpdatedBy(updatedBy);
        if (existing == null) {
            mapper.insert(e);
        } else {
            mapper.updateById(e);
        }
        return e;
    }

    // ---- validation ----

    /**
     * Structural check of the schema JSON itself — fail fast at authoring time
     * instead of at render/start time. Throws IllegalArgumentException on the
     * first problem (message names the offending field).
     */
    @SuppressWarnings("unchecked")
    public void validateSchema(String schemaJson) {
        Map<String, Object> schema = parse(schemaJson);
        Object fields = schema.get("fields");
        if (!(fields instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("schema must contain a non-empty 'fields' array");
        }
        for (Object o : list) {
            if (!(o instanceof Map)) {
                throw new IllegalArgumentException("each field must be an object");
            }
            Map<String, Object> f = (Map<String, Object>) o;
            String key = str(f.get("key"));
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("field 'key' missing or not a valid identifier: " + key);
            }
            if (str(f.get("label")) == null) {
                throw new IllegalArgumentException("field '" + key + "': 'label' is required");
            }
            String type = str(f.get("type")) == null ? "string" : str(f.get("type"));
            if (!TYPES.contains(type)) {
                throw new IllegalArgumentException("field '" + key + "': unknown type '" + type + "'");
            }
            if ("enum".equals(type)) {
                if (!(f.get("options") instanceof List<?> opts) || opts.isEmpty()) {
                    throw new IllegalArgumentException("field '" + key + "': enum needs an 'options' array");
                }
            }
        }
    }

    /**
     * Validate a start-submission against the schema (when one exists).
     *
     * @return the cleaned variable map (only schema fields, coerced types)
     * @throws IllegalArgumentException on missing required fields / bad types
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> validateAgainstSchema(String processKey, Map<String, Object> submitted) {
        FlowFormEntity form = get(processKey);
        if (form == null) {
            return submitted == null ? Map.of() : submitted; // free-form process
        }
        Map<String, Object> schema = parse(form.getSchemaJson());
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> in = submitted == null ? Map.of() : submitted;
        for (Object o : (List<?>) schema.get("fields")) {
            Map<String, Object> f = (Map<String, Object>) o;
            String key = str(f.get("key"));
            String type = str(f.get("type")) == null ? "string" : str(f.get("type"));
            boolean required = Boolean.TRUE.equals(f.get("required"));
            Object v = in.get(key);
            if (v == null || (v instanceof String s && s.isBlank())) {
                if (required) {
                    throw new IllegalArgumentException("field '" + key + "' (" + f.get("label") + ") is required");
                }
                continue;
            }
            out.put(key, coerce(key, type, v, f));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object coerce(String key, String type, Object v, Map<String, Object> f) {
        try {
            return switch (type) {
                case "number" -> (v instanceof Number) ? v : Double.valueOf(String.valueOf(v));
                case "boolean" -> (v instanceof Boolean) ? v : Boolean.valueOf(String.valueOf(v));
                case "enum" -> {
                    String s = String.valueOf(v);
                    List<Object> opts = (List<Object>) f.get("options");
                    if (opts.stream().noneMatch(o -> String.valueOf(o).equals(s))) {
                        throw new IllegalArgumentException(
                                "'" + s + "' is not one of " + opts);
                    }
                    yield s;
                }
                default -> String.valueOf(v);
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("field '" + key + "': not a valid number: " + v);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("'")) {
                throw e; // rethrow the enum message as-is
            }
            throw new IllegalArgumentException("field '" + key + "': " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        try {
            return new tools.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("schema is not valid JSON: " + e.getMessage());
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
