# JDBC drivers

Place trusted vendor JDBC driver JAR files in this directory before starting Yak Ops.

The distribution starts the Spring Boot application with `PropertiesLauncher` and adds this directory through `loader.path`. All JAR/ZIP files below `jdbc-drivers/` are therefore available to datasource plugins at runtime.

MySQL and PostgreSQL drivers are bundled with Yak Ops and do not need to be copied here. This directory is mainly intended for drivers that cannot be redistributed with Yak Ops, for example:

- Oracle: `ojdbc11.jar`
- KingbaseES: the Kingbase JDBC driver JAR that provides `com.kingbase8.Driver`
- Dameng: the Dameng JDBC driver JAR that provides `dm.jdbc.driver.DmDriver`

After adding, replacing, or removing a driver, restart Yak Ops so the runtime classpath is rebuilt.

For Docker deployments, mount the driver files into `/opt/yak-ops/jdbc-drivers` (the backend image already declares that path as a volume).

Only install JDBC drivers from sources you trust. Driver JARs are executable code and run with the same permissions as the Yak Ops backend process.
