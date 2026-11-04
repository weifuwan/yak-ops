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

package io.yak.ops.alert.service;

import io.yak.ops.alert.api.AlertData;
import io.yak.ops.alert.api.AlertResult;
import io.yak.ops.dao.entity.AlertPluginInstance;
import io.yak.ops.extract.alert.request.AlertSendResponse;

public interface EventSender<T> {

    void sendEvent(T event);

    AlertResult doSendEvent(AlertPluginInstance instance, AlertData alertData);

    AlertSendResponse syncTestSend(int pluginDefineId, String pluginInstanceParams);

}
