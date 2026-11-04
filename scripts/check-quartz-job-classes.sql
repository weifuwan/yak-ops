-- Read-only preflight for Scheduler package rename on an EXISTING Quartz database.
-- Use the configured Quartz table prefix in place of QRTZ_ if it differs.
-- Stop active schedulers and back up the database before applying any corrective UPDATE.
-- No database changes are performed by this query.
SELECT JOB_CLASS_NAME, COUNT(*) AS JOB_COUNT
FROM QRTZ_JOB_DETAILS
WHERE JOB_CLASS_NAME LIKE 'org.apache.dolphinscheduler.%'
GROUP BY JOB_CLASS_NAME
ORDER BY JOB_CLASS_NAME;

-- Check old Quartz scheduler identity separately: the renamed configuration uses YakOps.
-- Old SCHED_NAME values can prevent new instances from seeing existing triggers.
-- Do not UPDATE SCHED_NAME in one table only: Quartz references it across QRTZ_* tables.
SELECT SCHED_NAME, COUNT(*) AS TRIGGER_COUNT
FROM QRTZ_TRIGGERS
WHERE SCHED_NAME = 'DolphinScheduler'
GROUP BY SCHED_NAME;
