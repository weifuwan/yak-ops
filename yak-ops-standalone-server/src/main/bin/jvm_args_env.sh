-Xms4g
-Xmx4g
-Xmn2g

-XX:+IgnoreUnrecognizedVMOptions
-XX:+PrintGCDateStamps
-XX:+PrintGCDetails
-Xloggc:gc-%t.log
-XX:ErrorFile=java_error-%p.log

-XX:-OmitStackTraceInFastThrow
-XX:+ExitOnOutOfMemoryError
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=dump.hprof

-Duser.timezone=${SPRING_JACKSON_TIME_ZONE}