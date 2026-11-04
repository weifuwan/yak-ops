/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.tools.datasource;

import org.apache.dolphinscheduler.dao.plugin.api.dialect.DatabaseDialect;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DolphinSchedulerManagerTest {

    private SchemaDao schemaDao;

    private DatabaseDialect databaseDialect;

    private DolphinSchedulerManager manager;

    @BeforeEach
    void setUp() {
        schemaDao = mock(SchemaDao.class);
        databaseDialect = mock(DatabaseDialect.class);
        manager = new DolphinSchedulerManager(schemaDao, databaseDialect);
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
        when(databaseDialect.tableExists("t_ds_user")).thenReturn(true);
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
