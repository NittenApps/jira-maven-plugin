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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import dev.nittenapps.maven.plugins.issues.Issue;
import dev.nittenapps.maven.plugins.issues.IssueUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyUtils;

abstract class AbstractJiraDownloader {
    protected Log log;

    /**
     * Output file for xml document.
     */
    protected File output;

    /**
     * Maximum number of entries to show.
     */
    protected int maxEntries;

    /**
     * Filter to apply in JIRA query.
     */
    protected String filter;

    /**
     * Ids of fix versions to show, as comma separated string.
     */
    protected String fixVersionIds;

    /**
     * Statuses to show, as comma separated string.
     */
    protected String statuses;

    /**
     * Resolutions to show, as comma separated string.
     */
    protected String resolutions;

    /**
     * Priorities to show, as comma separated string.
     */
    protected String priorities;

    /**
     * The component ids to show.
     */
    protected String componentIds;

    /**
     * Issue types to show, as comma separated string.
     */
    protected String types;

    /**
     * Column names to sort by, as comma separated string.
     */
    protected String sortColumnNames;

    /**
     * The username to log into JIRA.
     */
    protected String jiraUser;

    /**
     * The password to log into JIRA.
     */
    protected String jiraPassword;

    /**
     * The maven project.
     */
    protected MavenProject project;

    /**
     * The maven settings.
     */
    protected Settings settings;

    /**
     * Filter the JIRA query based on the current version.
     */
    protected boolean onlyCurrentVersion;

    /**
     * The versionPrefix to apply to the POM version.
     */
    protected String versionPrefix;

    /**
     * The pattern used to parse dates from the JIRA xml file.
     */
    protected String jiraDatePattern;

    protected String proxyHost;

    protected int proxyPort;

    protected String proxyUser;

    protected String proxyPass;

    protected int connectionTimeout;

    protected int responseTimeout;

    public void setComponentIds(String componentIds) {
        this.componentIds = componentIds;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public void setFixVersionIds(String fixVersionIds) {
        this.fixVersionIds = fixVersionIds;
    }

    public void setJiraDatePattern(String jiraDatePattern) {
        this.jiraDatePattern = jiraDatePattern;
    }

    public void setJiraUser(String jiraUser) {
        this.jiraUser = jiraUser;
    }

    public void setJiraPassword(String jiraPassword) {
        this.jiraPassword = jiraPassword;
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public void setOnlyCurrentVersion(boolean onlyCurrentVersion) {
        this.onlyCurrentVersion = onlyCurrentVersion;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    public void setPriorities(String priorities) {
        this.priorities = priorities;
    }

    public void setProject(Object project) {
        this.project = (MavenProject) project;
    }

    public void setResolutions(String resolutions) {
        this.resolutions = resolutions;
    }

    public void setResponseTimeout(int responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public void setSortColumnNames(String sortColumnNames) {
        this.sortColumnNames = sortColumnNames;
    }

    public void setStatuses(String statuses) {
        this.statuses = statuses;
    }

    public void setTypes(String types) {
        this.types = types;
    }

    public String getVersionPrefix() {
        return versionPrefix;
    }

    public void setVersionPrefix(String versionPrefix) {
        this.versionPrefix = versionPrefix;
    }

    /**
     * Execute the query on the JIRA server.
     *
     * @throws Exception on error
     */
    public abstract void doExecute() throws Exception;

    protected void getProxyInfo(String jiraUrl) {
        Proxy proxy = null;

        if (project == null) {
            log.error("No project set. No proxy info available");
            return;
        }

        if (settings != null) {
            proxy = settings.getActiveProxy();
        }

        if (proxy != null) {
            ProxyInfo proxyInfo = new ProxyInfo();
            proxyInfo.setNonProxyHosts(proxy.getNonProxyHosts());

            URL url = null;
            try {
                url = new URL(jiraUrl);
            } catch (MalformedURLException ex) {
                log.error("Invalid JIRA URL: " + jiraUrl + ". " + ex.getMessage());
            }
            String jiraHost = null;
            if (url != null) {
                jiraHost = url.getHost();
            }

            if (ProxyUtils.validateNonProxyHosts(proxyInfo, jiraHost)) {
                return;
            }

            proxyHost = proxy.getHost();
            proxyPort = proxy.getPort();
            proxyUser = proxy.getUsername();
            proxyPass = proxy.getPassword();
        }
    }

    /**
     * Override this method if you need to get issues for a specific Fix For.
     *
     * @return A Fix For id or <code>null</code> if you don't have that need
     */
    protected String getFixFor() {
        String version = (versionPrefix == null ? "" : versionPrefix) + project.getVersion();

        // Remove "-SNAPSHOT" from the end of the version, if present
        return StringUtils.removeEnd(version, IssueUtils.SNAPSHOT_SUFFIX);
    }

    /**
     * Check to see if we think that JIRA authentication is needed.
     *
     * @return <code>true</code> if jiraUser and jiraPassword are set, otherwise <code>false</code>
     */
    protected boolean isJiraAuthenticationConfigured() {
        return StringUtils.isNoneBlank(jiraUser, jiraPassword);
    }

    public abstract List<Issue> getIssueList() throws MojoExecutionException;
}
