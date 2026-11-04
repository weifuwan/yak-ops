-- Read-only preflight for Scheduler package rename on an EXISTING Quartz database.
-- Use the configured Quartz table prefix in place of QRTZ_ if it differs.
-- Stop active schedulers and back up the database before applying any corrective UPDATE.
-- No database changes are performed by this query.
SELECT JOB_CLASS_NAME, COUNT(*) AS JOB_COUNT
FROM QRTZ_JOB_DETAILS
WHERE JOB_CLASS_NAME LIKE 'io.yak.ops.%'
GROUP BY JOB_CLASS_NAME
ORDER BY JOB_CLASS_NAME;
