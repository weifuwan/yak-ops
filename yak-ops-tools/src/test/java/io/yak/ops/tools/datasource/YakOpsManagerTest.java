package io.yak.ops.tools.datasource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.plugin.api.dialect.DatabaseDialect;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YakOpsManagerTest {

    private SchemaDao schemaDao;

    private DatabaseDialect databaseDialect;

    private YakOpsManager manager;

    @BeforeEach
    void setUp() {
        schemaDao = mock(SchemaDao.class);
        databaseDialect = mock(DatabaseDialect.class);
        manager = new YakOpsManager(schemaDao, databaseDialect);
    }

    private void mockRequiredTables() {
        when(databaseDialect.tableExists("t_ds_user")).thenReturn(true);
        when(databaseDialect.tableExists("t_ds_workflow_instance")).thenReturn(true);
        when(databaseDialect.tableExists("t_ds_task_instance")).thenReturn(true);
    }

    @Test
    void newDatabaseIsInitializedAndValidated() throws Exception {
        when(databaseDialect.tableExists("t_ds_version")).thenReturn(false, true);
        when(databaseDialect.tableExists("t_ds_user")).thenReturn(false, true);
        when(databaseDialect.tableExists("t_ds_workflow_instance")).thenReturn(false, true);
        when(databaseDialect.tableExists("t_ds_task_instance")).thenReturn(true);
        when(schemaDao.getCurrentVersion()).thenReturn("3.4.3");
        manager.initializeOrValidate();
        verify(schemaDao).initSchema();
    }

    @Test
    void initializedDatabaseIsNotReinitialized() throws Exception {
        when(databaseDialect.tableExists("t_ds_version")).thenReturn(true);
        when(schemaDao.getCurrentVersion()).thenReturn("3.4.3");
        mockRequiredTables();
        manager.initializeOrValidate();
        verify(schemaDao, never()).initSchema();
    }

    @Test
    void historicalDatabaseIsRejected() {
        when(databaseDialect.tableExists("t_ds_version")).thenReturn(true);
        when(schemaDao.getCurrentVersion()).thenReturn("3.2.0");
        IllegalStateException ex = Assertions.assertThrows(
                IllegalStateException.class, () -> manager.initializeOrValidate());
        Assertions.assertTrue(ex.getMessage().contains("3.4.3"));
        verify(schemaDao, never()).initSchema();
    }

    @Test
    void missingVersionRowIsRejected() {
        when(databaseDialect.tableExists("t_ds_version")).thenReturn(true);
        Assertions.assertThrows(IllegalStateException.class, () -> manager.initializeOrValidate());
        verify(schemaDao, never()).initSchema();
    }

    @Test
    void partialSchemaIsNotOverwritten() {
        when(schemaDao.hasExistingTables()).thenReturn(true);
        Assertions.assertThrows(IllegalStateException.class, () -> manager.initializeOrValidate());
        verify(schemaDao, never()).initSchema();
    }

    @Test
    void missingBaselineTableIsRejected() {
        when(databaseDialect.tableExists("t_ds_version")).thenReturn(true);
        when(schemaDao.getCurrentVersion()).thenReturn("3.4.3");
        when(databaseDialect.tableExists("t_ds_user")).thenReturn(true);
        Assertions.assertThrows(IllegalStateException.class, () -> manager.initializeOrValidate());
        verify(schemaDao, never()).initSchema();
    }
}
