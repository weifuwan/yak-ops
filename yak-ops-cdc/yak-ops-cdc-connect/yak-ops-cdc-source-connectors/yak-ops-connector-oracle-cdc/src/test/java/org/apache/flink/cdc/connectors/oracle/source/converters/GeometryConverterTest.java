/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.cdc.connectors.oracle.source.converters;

import org.locationtech.jts.geom.MultiPolygon;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for Oracle Spatial geometry conversion. */
class GeometryConverterTest {

    @Test
    void convertsSimpleTwoDimensionalPolygon() {
        OracleGeometry oracleGeometry = new OracleGeometry();
        oracleGeometry.setElemInfo(new int[] {1, 1003, 1});
        oracleGeometry.setOrdinates(new double[] {0, 0, 0, 10, 10, 10, 10, 0, 0, 0});

        MultiPolygon converted = new GeometryConverter().createJTSMultiPolygon(oracleGeometry);

        assertThat(converted).isNotNull();
        assertThat(converted.getNumGeometries()).isEqualTo(1);
        assertThat(converted.getGeometryN(0).getNumPoints()).isEqualTo(5);
        assertThat(converted.getGeometryN(0).getArea()).isEqualTo(100.0);
    }
}
