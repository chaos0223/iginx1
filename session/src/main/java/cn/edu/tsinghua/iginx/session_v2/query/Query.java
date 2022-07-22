/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package cn.edu.tsinghua.iginx.session_v2.query;

import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class Query {

    protected final Set<String> measurements;

    protected final Map<String, List<String>> tagsList;

    public Query(Set<String> measurements) {
        this(measurements, null);
    }

    public Query(Set<String> measurements, Map<String, List<String>> tagsList) {
        this.measurements = measurements;
        this.tagsList = tagsList;
    }

    public Set<String> getMeasurements() {
        return measurements;
    }

    public Map<String, List<String>> getTagsList() {
        return tagsList;
    }
}
