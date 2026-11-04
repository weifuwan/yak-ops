-- ############################# t_yak_ops_tenant #############################
drop PROCEDURE if EXISTS yak_ops_tenant_insert_default;
delimiter d//
CREATE PROCEDURE yak_ops_tenant_insert_default()
BEGIN
    IF
NOT EXISTS(SELECT 1
                   FROM t_yak_ops_tenant
                   WHERE id = -1)
    THEN
        INSERT INTO `t_yak_ops_tenant` VALUES ('-1', 'default', 'default tenant', '1', current_timestamp, current_timestamp);
END IF;
END;
d//

delimiter ;
CALL yak_ops_tenant_insert_default();
DROP PROCEDURE yak_ops_tenant_insert_default;

-- tenant improvement
UPDATE t_yak_ops_schedules t1 JOIN t_yak_ops_workflow_definition t2 ON t1.workflow_definition_code = t2.code LEFT JOIN t_yak_ops_tenant t3 ON t2.tenant_id = t3.id SET t1.tenant_code = COALESCE(t3.tenant_code, 'default');
UPDATE `t_yak_ops_workflow_instance` SET `tenant_code` = 'default' WHERE `tenant_code` IS NULL;

