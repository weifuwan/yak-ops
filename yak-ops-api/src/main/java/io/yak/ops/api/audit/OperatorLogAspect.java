package io.yak.ops.api.audit;

import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.audit.operator.AuditOperator;
import io.yak.ops.dao.entity.AuditLog;
import io.yak.ops.dao.entity.User;
import io.yak.ops.service.bean.SpringApplicationContext;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Aspect
@Slf4j
@Component
public class OperatorLogAspect {

    private static final ThreadLocal<AuditContext> auditThreadLocal = new ThreadLocal<>();

    @Pointcut("@annotation(io.yak.ops.api.audit.OperatorLog)")
    public void logPointCut() {
    }

    @Before("logPointCut()")
    public void before(JoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        OperatorLog operatorLog = method.getAnnotation(OperatorLog.class);
        Operation operation = method.getAnnotation(Operation.class);

        if (operation == null) {
            log.warn("Operation is null of method: {}", method.getName());
            return;
        }

        Map<String, Object> paramsMap = OperatorUtils.getParamsMap(point, signature);
        User user = OperatorUtils.getUser(paramsMap);
        if (user == null) {
            log.error("user is null");
            return;
        }

        AuditType auditType = operatorLog.auditType();

        try {
            AuditOperator operator = SpringApplicationContext.getBean(operatorLog.auditType().getOperatorClass());
            List<AuditLog> auditLogList = OperatorUtils.buildAuditLogList(operation.description(), auditType, user);
            operator.setRequestParam(auditType, auditLogList, paramsMap);
            AuditContext auditContext =
                    new AuditContext(auditLogList, paramsMap, operatorLog, System.currentTimeMillis(), operator);
            auditThreadLocal.set(auditContext);
        } catch (Throwable throwable) {
            log.error("Record audit log error", throwable);
        }
    }

    @AfterReturning(value = "logPointCut()", returning = "returnValue")
    public void afterReturning(Object returnValue) {
        try {
            AuditContext auditContext = auditThreadLocal.get();
            if (auditContext == null) {
                return;
            }
            auditContext.getOperator().recordAudit(auditContext, returnValue);
        } catch (Throwable throwable) {
            log.error("Record audit log error", throwable);
        } finally {
            auditThreadLocal.remove();
        }
    }

    @AfterThrowing("logPointCut()")
    public void afterThrowing() {
        auditThreadLocal.remove();
    }

    @Data
    @AllArgsConstructor
    public static class AuditContext {

        List<AuditLog> auditLogList;
        Map<String, Object> paramsMap;
        OperatorLog operatorLog;
        long beginTime;
        AuditOperator operator;
    }
}
