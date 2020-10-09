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

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;

class JiraHelper {
    private static final String PID = "?pid=";

    /**
     * Get the base URL for the JIRA server.
     *
     * @param url URL.
     * @return the base URL.
     */
    public static String getBaseUrl(String url) {
        int index = url.indexOf("/", 8); // Ignore http:// or https://
        return url.substring(0, index);
    }

    static Map<String, String> getJiraUrlAndProjectId(String issueManagementUrl) {
        String url = StringUtils.removeEnd(issueManagementUrl, "/");
        int pos = url.indexOf('?');
        String id = "";
        if (pos >= 0) {
            id = url.substring(url.lastIndexOf('=') + 1);
        }

        String jiraUrl = url.substring(0, url.lastIndexOf('/'));
        if (jiraUrl.endsWith("secure")) {
            jiraUrl = jiraUrl.substring(0, jiraUrl.lastIndexOf('/'));
        } else {
            final int index = jiraUrl.indexOf("/browse");
            if (index != -1) {
                jiraUrl = jiraUrl.substring(0, index);
            }
        }

        Map<String, String> urlMap = new HashMap<>(4);
        urlMap.put("url", jiraUrl);
        urlMap.put("id", id);
        return urlMap;
    }

    /**
     * Parse out the base URL for JIRA and the JIRA project name from the issue management URL. The issue management URL
     * is assumed to be of the format http(s)://host:port/browse/{projectname}
     *
     * @param issueManagementUrl The URL to the issue management system
     * @return A <code>Map</code> containing the URL and project name
     * @since 2.8
     */
    public static Map<String, String> getJiraUrlAndProjectName(String issueManagementUrl) {
        final int indexBrowse = issueManagementUrl.indexOf("/browse");

        String jiraUrl;
        String project;

        if (indexBrowse == -1) {
            throw new IllegalArgumentException("Invalid browse URL");
        } else {
            jiraUrl = issueManagementUrl.substring(0, indexBrowse);
            final int indexBrowseEnd = indexBrowse + "/browse/".length();
            final int indexProject = issueManagementUrl.indexOf('/', indexBrowseEnd);

            if (indexProject == -1) {
                // Project name without trailing '/'
                project = issueManagementUrl.substring(indexBrowseEnd);
            } else {
                // Project name has trailing '/'
                project = issueManagementUrl.substring(indexBrowseEnd, indexProject);
            }
        }

        HashMap<String, String> urlMap = new HashMap<>(4);
        urlMap.put("url", jiraUrl);
        urlMap.put("project", project);
        return urlMap;
    }

    /**
     * Try to get a JIRA pid from the issue management URL.
     *
     * @param log                Used to tell the user what happened
     * @param issueManagementUrl The URL to the issue management system
     * @param client             The client used to connect to JIRA
     * @return The JIRA id for the project, or null if it can't be found
     */
    public static String getPidFromJira(Log log, String issueManagementUrl, HttpClient client) {
        String jiraId = null;
        GetMethod gm = new GetMethod(issueManagementUrl);

        String projectPage;
        try {
            client.executeMethod(gm);
            log.debug("Successfully reached JIRA.");
            projectPage = gm.getResponseBodyAsString();
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.error("Unable to reach the JIRA project page:", ex);
            } else {
                log.error("Unable to reach the JIRA project page. Cause is: " + ex.getLocalizedMessage());
            }
            return null;
        }

        int pidIndex = projectPage.indexOf(PID);
        if (pidIndex == -1) {
            log.error("Unable to extract a JIRA pid from the page at the url " + issueManagementUrl);
        } else {
            NumberFormat format = NumberFormat.getInstance();
            Number pidNumber = format.parse(projectPage, new ParsePosition(pidIndex + PID.length()));
            jiraId = Integer.toString(pidNumber.intValue());
            log.debug("Found the pid " + jiraId + " at " + issueManagementUrl);
        }
        return jiraId;
    }

    private JiraHelper() {
    }
}
