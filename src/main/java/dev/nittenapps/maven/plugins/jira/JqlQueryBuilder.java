/*
 * Copyright 2020 Sergio Walberto Del Valle y Gutiérrez (NittenApps)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.nittenapps.maven.plugins.jira;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;

class JqlQueryBuilder implements JiraQueryBuilder {
    private String filter;

    private boolean urlEncode = true;

    private final Log log;

    private StringBuilder orderBy = new StringBuilder();

    private StringBuilder query = new StringBuilder();

    public JqlQueryBuilder(@NotNull Log log) {
        this.log = log;
    }

    @Override
    @NotNull
    public String build() {
        String jql;
        if (StringUtils.isNotBlank(filter)) {
            jql = filter;
        } else {
            jql = query.toString() + orderBy.toString();
        }

        if (urlEncode) {
            log.debug("Encoding JQL query " + jql);
            String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);
            log.debug("Encoded JQL query " + encodedJql);
            return encodedJql;
        }
        return jql;
    }

    @Override
    @NotNull
    public JiraQueryBuilder components(String... components) {
        addValues("component", components);
        return this;
    }

    @Override
    @NotNull
    public JiraQueryBuilder filter(String filter) {
        this.filter = filter;
        return this;
    }

    @Override
    @NotNull
    public JiraQueryBuilder fixVersion(String fixVersion) {
        addValue("fixVersion", fixVersion);
        return this;
    }

    @Override
    @NotNull
    public JiraQueryBuilder fixVersionIds(String... fixVersionIds) {
        addValues("fixVersion", fixVersionIds);
        return this;
    }

    @Override
    @NotNull
    public JiraQueryBuilder priorities(String... priorities) {
        addValues("priority", priorities);
        return this;
    }

    @Override
    @NotNull
    public JiraQueryBuilder project(String project) {
        addValue("project", project);
        return this;
    }

    @Override
    @NotNull
    public JiraQueryBuilder resolutions(String... resolutions) {
        addValues("resolution", resolutions);
        return this;
    }

    @Override
    @NotNull
    public JiraQueryBuilder sortColumnNames(String sortColumnNames) {
        if (StringUtils.isNotBlank(sortColumnNames)) {
            orderBy.append(" ORDER BY ");
            String[] sortColumnNamesArray = sortColumnNames.split(",");

            for (int i = 0; i < sortColumnNamesArray.length - 1; i++) {
                addSortColumn(sortColumnNamesArray[i]);
                orderBy.append(',');
            }
            addSortColumn(sortColumnNamesArray[sortColumnNamesArray.length - 1]);
        }
        return this;
    }

    @Override
    @NotNull
    public JiraQueryBuilder statuses(String... statuses) {
        addValues("status", statuses);
        return this;
    }

    @Override
    @NotNull
    public JiraQueryBuilder types(String... types) {
        addValues("type", types);
        return this;
    }

    @NotNull
    public JiraQueryBuilder urlEncode(boolean encode) {
        urlEncode = encode;
        return this;
    }

    private void addSortColumn(@NotNull String name) {
        boolean desc = false;
        name = name.trim().toLowerCase(Locale.ENGLISH);
        if (name.endsWith("desc")) {
            desc = true;
            name = name.substring(0, name.length() - 4).trim();
        } else if (name.endsWith("asc")) {
            name = name.substring(0, name.length() - 3).trim();
        }
        name = name.replaceAll(" ", "");
        orderBy.append(name).append(desc ? " DESC" : " ASC");
    }

    private void addValue(String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            if (query.length() > 0) {
                query.append(" AND ");
            }
            query.append(key).append("=").append(trimAndQuoteValue(value));
        }
    }

    private void addValues(String key, String... values) {
        if (ArrayUtils.isNotEmpty(values)) {
            if (query.length() > 0) {
                query.append(" AND ");
            }

            query.append(key).append(" IN (");

            for (int i = 0; i < values.length - 1; i++) {
                query.append(trimAndQuoteValue(values[i])).append(',');
            }
            query.append(trimAndQuoteValue(values[values.length - 1])).append(')');
        }
    }

    private String trimAndQuoteValue(String value) {
        String trimmedValue = value.trim();
        if (trimmedValue.contains(" ") || trimmedValue.contains(".")) {
            return "\"" + trimmedValue + "\"";
        }
        return trimmedValue;
    }
}
