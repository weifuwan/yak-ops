package io.yak.ops.plugin.task.java;

public class JavaConstants {

    private JavaConstants() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The constants used to get the Java installation directory
     **/
    public static final String JAVA_HOME_VAR = "${JAVA_HOME}";

    /**
     * This constant represents the use of the java -jar command to run a task
     **/
    public static final String RUN_TYPE_FAT_JAR = "FAT_JAR";

    /**
     * This constant represents the use of the java -cp command to run a task
     **/
    public static final String RUN_TYPE_NORMAL_JAR = "NORMAL_JAR";

    /**
     * This constant is the Classpath or module path delimiter for different operating systems
     **/
    public static final String PATH_SEPARATOR = System.getProperty("path.separator");

    /**
     * This constant represents the current directory in the Classpath or module path
     **/
    public static final String CLASSPATH_CURRENT_DIR = ".";

}
