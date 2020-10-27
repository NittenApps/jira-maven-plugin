/*
 * Copyright (c) 2020. Sergio Walberto Del Valle y Gutiérrez (NittenApps)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.nittenapps.maven.plugins.jira.report;

import org.jetbrains.annotations.NotNull;

public interface JiraQueryBuilder {
    @NotNull
    String build();

    @NotNull
    JiraQueryBuilder components(String... components);

    @NotNull
    JiraQueryBuilder filter(String filter);

    @NotNull
    JiraQueryBuilder fixVersion(String fixVersion);

    @NotNull
    JiraQueryBuilder fixVersionIds(String... fixVersionIds);

    @NotNull
    JiraQueryBuilder priorities(String... priorities);

    @NotNull
    JiraQueryBuilder project(String project);

    @NotNull
    JiraQueryBuilder resolutions(String... resolutions);

    @NotNull
    JiraQueryBuilder sortColumnNames(String sortColumnNames);

    @NotNull
    JiraQueryBuilder statuses(String... statuses);

    @NotNull
    JiraQueryBuilder types(String... types);
}
