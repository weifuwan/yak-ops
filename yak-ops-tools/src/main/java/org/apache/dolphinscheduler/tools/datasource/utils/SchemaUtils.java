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

package org.apache.dolphinscheduler.tools.datasource.utils;

import org.apache.dolphinscheduler.common.utils.FileUtils;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;

public final class SchemaUtils {

    private SchemaUtils() {
        throw new UnsupportedOperationException("Construct SchemaUtils");
    }

    public static String getSoftVersion() throws IOException {
        ClassPathResource resource = new ClassPathResource("sql/soft_version");
        try (InputStream inputStream = resource.getInputStream()) {
            String version = FileUtils.readFile2Str(inputStream).trim();
            if (version.isEmpty()) {
                throw new IllegalStateException("Database schema baseline version must not be empty");
            }
            return version;
        }
    }
}
