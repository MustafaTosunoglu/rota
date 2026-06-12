package com.rota.audit.api;

import com.rota.audit.internal.AuditBridge;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity listener that turns {@code @Auditable} entity CUD into audit events. Reflects ONLY
 * the annotation's allow-listed fields, so secrets are never captured. JPA instantiates this
 * class itself, so it reaches the Spring-managed {@link AuditService} via {@link AuditBridge}.
 */
public class AuditEntityListener {

    @PostPersist
    public void onCreate(Object entity) {
        capture(entity, AuditActions.CREATE);
    }

    @PostUpdate
    public void onUpdate(Object entity) {
        capture(entity, AuditActions.UPDATE);
    }

    @PostRemove
    public void onDelete(Object entity) {
        capture(entity, AuditActions.DELETE);
    }

    private void capture(Object entity, String action) {
        Auditable auditable = entity.getClass().getAnnotation(Auditable.class);
        if (auditable == null) {
            return;
        }
        Map<String, Object> snapshot = snapshot(entity, auditable.fields());
        UUID entityId = readId(entity);
        boolean delete = AuditActions.DELETE.equals(action);
        AuditEvent event = AuditEvent.entity(
                auditable.type(), entityId, action,
                delete ? snapshot : null,
                delete ? null : snapshot);
        AuditBridge.auditService().record(event);
    }

    private static Map<String, Object> snapshot(Object entity, String[] fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : fields) {
            values.put(name, readField(entity, name));
        }
        return values;
    }

    private static UUID readId(Object entity) {
        Object id = readField(entity, "id");
        return id instanceof UUID uuid ? uuid : null;
    }

    private static Object readField(Object entity, String name) {
        Class<?> type = entity.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(entity);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read audited field '" + name + "'", e);
            }
        }
        throw new IllegalStateException("Audited field '" + name + "' not found on " + entity.getClass());
    }
}
