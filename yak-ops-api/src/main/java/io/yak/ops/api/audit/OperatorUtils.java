package io.yak.ops.api.audit;

import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.enums.ExecuteType;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.AuditModelType;
import io.yak.ops.common.enums.AuditOperationType;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.dao.entity.AuditLog;
import io.yak.ops.dao.entity.User;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OperatorUtils {

    public static boolean resultFail(Result<?> result) {
        return result != null && result.isFailed();
    }

    public static List<AuditLog> buildAuditLogList(String apiDescription, AuditType auditType, User user) {
        List<AuditLog> auditLogList = new ArrayList<>();
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(user.getId());
        auditLog.setModelType(auditType.getAuditModelType().getName());
        auditLog.setOperationType(auditType.getAuditOperationType().getName());
        auditLog.setDescription(apiDescription);
        auditLog.setCreateTime(new Date());
        auditLogList.add(auditLog);
        return auditLogList;
    }

    public static User getUser(Map<String, Object> paramsMap) {
        for (Object object : paramsMap.values()) {
            if (object instanceof User) {
                return (User) object;
            }
        }

        return null;
    }

    public static Map<String, Object> getParamsMap(JoinPoint point, MethodSignature signature) {
        Object[] args = point.getArgs();
        String[] strings = signature.getParameterNames();

        Map<String, Object> paramsMap = new HashMap<>();
        for (int i = 0; i < strings.length; i++) {
            paramsMap.put(strings[i], args[i]);
        }

        return paramsMap;
    }

    public static AuditOperationType modifyReleaseOperationType(AuditType auditType, Map<String, Object> paramsMap) {
        switch (auditType.getAuditOperationType()) {
            case RELEASE:
                ReleaseState releaseState = (ReleaseState) paramsMap.get(Constants.RELEASE_STATE);
                if (releaseState == null) {
                    break;
                }
                switch (releaseState) {
                    case ONLINE:
                        return AuditOperationType.ONLINE;
                    case OFFLINE:
                        return AuditOperationType.OFFLINE;
                    default:
                        break;
                }
                break;
            case EXECUTE:
                ExecuteType executeType = (ExecuteType) paramsMap.get(Constants.EXECUTE_TYPE);
                if (executeType == null) {
                    break;
                }
                switch (executeType) {
                    case REPEAT_RUNNING:
                        return AuditOperationType.RERUN;
                    case RECOVER_SUSPENDED_PROCESS:
                        return AuditOperationType.RESUME_PAUSE;
                    case START_FAILURE_TASK_PROCESS:
                        return AuditOperationType.RESUME_FAILURE;
                    case STOP:
                        return AuditOperationType.STOP;
                    case PAUSE:
                        return AuditOperationType.PAUSE;
                    case EXECUTE_TASK:
                        return AuditOperationType.EXECUTE;
                    default:
                        break;
                }
                break;
            default:
                break;
        }

        return auditType.getAuditOperationType();
    }

    public static long getObjectIdentityByParam(String[] paramNameArr, Map<String, Object> paramsMap) {
        for (String name : paramNameArr) {
            Object value = paramsMap.get(name);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                String param = (String) value;
                try {
                    if (param.matches("\\d+")) {
                        return Long.parseLong(param);
                    }
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }

        return -1;
    }

    public static Map<String, Object> getObjectIfFromReturnObject(Object obj, String[] params) {
        Map<String, Object> map = new HashMap<>();

        try {
            Class<?> clazz = obj.getClass();

            if (clazz.equals(Long.class)) {
                map.put(params[0], obj);
            }

            while (clazz != null) {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);

                    if (field.getName().equals(params[0])) {
                        map.put(params[0], field.get(obj));
                    }
                }

                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            log.error("get object if from return object error", e);
        }

        return map;
    }

    public static boolean isFolder(String name) {
        return name != null && name.endsWith("/");
    }

    public static String getFileAuditObject(AuditType auditType, Map<String, Object> paramsMap, String name) {
        boolean isFolder = auditType == AuditType.FOLDER_CREATE || isFolder(name);
        return isFolder ? AuditModelType.FOLDER.getName() : AuditModelType.FILE.getName();
    }

}
