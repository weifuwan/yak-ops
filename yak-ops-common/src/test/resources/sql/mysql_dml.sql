-- ############################# t_ds_tenant #############################
drop PROCEDURE if EXISTS dolphin_t_ds_tenant_insert_default;
delimiter d//
CREATE PROCEDURE dolphin_t_ds_tenant_insert_default()
BEGIN
    IF
NOT EXISTS(SELECT 1
                   FROM t_ds_tenant
                   WHERE id = -1)
    THEN
        INSERT INTO `t_ds_tenant` VALUES ('-1', 'default', 'default tenant', '1', current_timestamp, current_timestamp);
END IF;
END;
d//

delimiter ;
CALL dolphin_t_ds_tenant_insert_default();
DROP PROCEDURE dolphin_t_ds_tenant_insert_default;

-- tenant improvement
UPDATE t_ds_schedules t1 JOIN t_ds_workflow_definition t2 ON t1.workflow_definition_code = t2.code LEFT JOIN t_ds_tenant t3 ON t2.tenant_id = t3.id SET t1.tenant_code = COALESCE(t3.tenant_code, 'default');
UPDATE `t_ds_workflow_instance` SET `tenant_code` = 'default' WHERE `tenant_code` IS NULL;

