package io.yak.ops.dao;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@SpringBootTest(classes = DaoConfiguration.class)
@SpringBootApplication(scanBasePackageClasses = DaoConfiguration.class)
@Transactional
@Rollback
public abstract class BaseDaoTest {
}
