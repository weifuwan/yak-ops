package io.yak.ops.tools.datasource.mysql;

import io.yak.ops.dao.DaoConfiguration;
import io.yak.ops.tools.datasource.InitializeYakOps;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("mysql")
@SpringBootTest(classes = {InitializeYakOps.class, DaoConfiguration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public @interface YakOpsMysqlProfile {

}
