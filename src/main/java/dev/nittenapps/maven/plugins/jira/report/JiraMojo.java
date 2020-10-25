/*
 * Copyright (c) 2020. Sergio Walberto Del Valle y Gutiérrez (NittenApps)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.nittenapps.maven.plugins.jira.report;

import java.io.File;
import java.util.*;

import dev.nittenapps.maven.plugins.issues.Issue;
import dev.nittenapps.maven.plugins.issues.IssueUtils;
import dev.nittenapps.maven.plugins.issues.IssuesReportGenerator;
import dev.nittenapps.maven.plugins.issues.IssuesReportHelper;
import dev.nittenapps.maven.plugins.jira.ProjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;

@Mojo(name = "report", defaultPhase = LifecyclePhase.SITE, threadSafe = true)
public class JiraMojo extends AbstractJiraReport {
    /**
     * Valid JIRA columns.
     */
    private static final Map<String, Integer> JIRA_COLUMNS = new HashMap<>(16);

    static {
        JIRA_COLUMNS.put("Assignee", IssuesReportHelper.COLUMN_ASSIGNEE);
        JIRA_COLUMNS.put("Component", IssuesReportHelper.COLUMN_COMPONENT);
        JIRA_COLUMNS.put("Created", IssuesReportHelper.COLUMN_CREATED);
        JIRA_COLUMNS.put("Fix Version", IssuesReportHelper.COLUMN_FIX_VERSION);
        JIRA_COLUMNS.put("Id", IssuesReportHelper.COLUMN_ID);
        JIRA_COLUMNS.put("Key", IssuesReportHelper.COLUMN_KEY);
        JIRA_COLUMNS.put("Priority", IssuesReportHelper.COLUMN_PRIORITY);
        JIRA_COLUMNS.put("Reporter", IssuesReportHelper.COLUMN_REPORTER);
        JIRA_COLUMNS.put("Resolution", IssuesReportHelper.COLUMN_RESOLUTION);
        JIRA_COLUMNS.put("Status", IssuesReportHelper.COLUMN_STATUS);
        JIRA_COLUMNS.put("Summary", IssuesReportHelper.COLUMN_SUMMARY);
        JIRA_COLUMNS.put("Type", IssuesReportHelper.COLUMN_TYPE);
        JIRA_COLUMNS.put("Updated", IssuesReportHelper.COLUMN_UPDATED);
        JIRA_COLUMNS.put("Version", IssuesReportHelper.COLUMN_VERSION);
    }

    /**
     * Sets the names of the columns that you want in the report. The columns will appear in the report in the same
     * order as you specify them here. Multiple values can be separated by commas.
     * <p>
     * Valid columns are: <code>Assignee</code>, <code>Component</code>, <code>Created</code>, <code>Fix Version</code>,
     * <code>Id</code>, <code>Key</code>, <code>Priority</code>, <code>Reporter</code>, <code>Resolution</code>,
     * <code>Status</code>, <code>Summary</code>, <code>Type</code>, <code>Updated</code> and <code>Version</code>.
     * </p>
     */
    @Parameter(defaultValue = "Key,Summary,Status,Resolution,Assignee")
    private String columnNames;

    /**
     * Sets the component(s) that you want to limit your report to include. Multiple values can be separated by commas
     * (such as 10011,10012). If this is set to empty - that means all components will be included.
     */
    @Parameter
    private String componentIds;

    /**
     * Defines the filter parameters to restrict which issues are retrieved from JIRA. The filter parameter uses the
     * same format of url parameters that is used in a JIRA search.
     */
    @Parameter()
    private String filter;

    /**
     * Sets the fix version id(s) that you want to limit your report to include. These are JIRA's internal version ids,
     * <b>NOT</b> the human readable display ones. Multiple fix versions can be separated by commas. If this is set to
     * empty - that means all fix versions will be included.
     *
     * @since 2.0
     */
    @Parameter
    private String fixVersionIds;

    /**
     * The pattern used by dates in the JIRA XML-file. This is used to parse the Created and Updated fields.
     */
    @Parameter(defaultValue = "EEE, d MMM yyyy HH:mm:ss Z")
    private String jiraDatePattern;

    /**
     * The settings.xml server id to be used for authentication into a private JIRA installation.
     */
    @Parameter(property = "jira.serverId", defaultValue = "jira")
    private String jiraServerId;

    /**
     * Path to the JIRA XML file, which will be parsed.
     */
    @Parameter(defaultValue = "${project.build.directory}/jira-results.xml", required = true, readonly = true)
    private File jiraXmlPath;

    /**
     * Maximum number of entries to be fetched from JIRA.
     */
    @Parameter(defaultValue = "100")
    private int maxEntries;

    /**
     * If you only want to show issues for the current version in the report. The current version being used is
     * <code>${project.version}</code> minus any "-SNAPSHOT" suffix.
     */
    @Parameter(defaultValue = "false")
    private boolean onlyCurrentVersion;

    /**
     * Sets the priority(s) that you want to limit your report to include. Valid statuses are <code>Highest</code>,
     * <code>High</code>, <code>Medium</code>, <code>Low</code> and <code>Lowest</code>. Multiple values can be
     * separated by commas. If this is set to empty - that means all priorities will be included.
     */
    @Parameter
    private String priorities;

    /**
     * Sets the resolution(s) that you want to fetch from JIRA. Valid resolutions are: <code>Cannot Reproduce</code>,
     * <code>Declined</code>, <code>Done</code>, <code>Duplicate</code>, <code>Unresolved</code> and
     * <code>Won't Do</code>. Multiple values can be separated by commas.
     */
    @Parameter(defaultValue = "Done")
    private String resolutions;

    /**
     * Settings XML configuration.
     */
    @Parameter(defaultValue = "${settings}", readonly = true, required = true)
    private Settings settings;

    /**
     * If set to <code>true</code>, then the JIRA report will not be generated.
     */
    @Parameter(property = "jira.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Sets the column names that you want to sort the report by. Add <code>DESC</code> following the column name to
     * specify <i>descending</i> sequence. For example <code>Fix Version DESC, Type</code> sorts first by the Fix
     * Version in descending order and then by Type in ascending order. By default sorting is done in ascending order,
     * but is possible to specify <code>ASC</code> for consistency. The previous example would then become
     * <code>Fix Version DESC, Type ASC</code>.
     * <p>
     * Valid columns are: <code>Assignee</code>, <code>Component</code>, <code>Created</code>, <code>Fix Version</code>,
     * <code>Id</code>, <code>Key</code>, <code>Priority</code>, <code>Reporter</code>, <code>Resolution</code>,
     * <code>Status</code>, <code>Summary</code>, <code>Type</code>, <code>Updated</code> and <code>Version</code>.
     * </p>
     */
    @Parameter(defaultValue = "Priority DESC, Created DESC")
    private String sortColumnNames;

    /**
     * Sets the status(es) that you want to fetch from JIRA. Valid statuses are: <code>Open</code>, <code>To Do</code>,
     * <code>In Progress</code>, <code>Reopened</code>, <code>Resolved</code>, <code>Done</code> and
     * <code>Closed</code>. Multiple values can be separated by commas.
     */
    @Parameter(defaultValue = "Resolved,Done")
    private String statuses;

    /**
     * Sets the types(s) that you want to limit your report to include. Valid types are: <code>Bug</code>,
     * <code>Epic</code>, <code>Story</code>, <code>Task</code> and <code>Sub-task</code>. Multiple values can be
     * separated by commas. If this is set to empty - that means all types will be included.
     */
    @Parameter
    private String types;

    /**
     * The prefix used when naming versions in JIRA.
     * <p>
     * If you have a project in JIRA with several components that have different release cycles, it is an often used
     * pattern to prefix the version with the name of the component, e.g. maven-filtering-1.0 etc. To fetch issues from
     * JIRA for a release of the "maven-filtering" component you would need to set this parameter to "maven-filtering-".
     * </p>
     */
    @Parameter
    private String versionPrefix;

    @Override
    public boolean canGenerateReport() {
        if (skip) {
            return false;
        }

        // Run only at the execution root
        if (runOnlyAtExecutionRoot && !isThisTheExecutionRoot()) {
            getLog().info("Skipping the JIRA Report in this project because it's not the Execution Root");
            return false;
        }

        String message = ProjectUtils.validateIssueManagement(project, "JIRA", "JIRA Report");
        if (message != null) {
            getLog().warn(message);
        }
        return message == null;
    }

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        List<Integer> columnIds = IssuesReportHelper.getColumnIds(columnNames, JIRA_COLUMNS);
        if (columnIds.isEmpty()) {
            // This happens if the user has configured column names that are all invalid
            throw new MavenReportException("jira-maven-plugin: None of the configured columnNames '" + columnNames
                    + "' are valid.");
        }

        try {
            AbstractJiraDownloader issueDownloader = new RestJiraDownloader(locale);
            configureIssueDownloader(issueDownloader);
            issueDownloader.doExecute();

            List<Issue> issues = issueDownloader.getIssueList();

            if (StringUtils.isNotBlank(versionPrefix)) {
                int originalNumberOfIssues = issues.size();
                issues = IssueUtils.filterIssuesWithVersionPrefix(issues, versionPrefix);
                getLog().debug("Filtered out " + issues.size() + " issues of " + originalNumberOfIssues
                        + " that matched the versionPrefix '" + versionPrefix + "'.");
            }

            if (onlyCurrentVersion) {
                String version = (versionPrefix == null ? "" : versionPrefix) + project.getVersion();
                issues = IssueUtils.getIssuesForVersion(issues, version);
                getLog().info("The JIRA Report will contain issues only for the current version.");
            }

            // Generate the report
            IssuesReportGenerator report = new IssuesReportGenerator(IssuesReportHelper.toIntArray(columnIds));
            if (issues.isEmpty()) {
                report.doGenerateEmptyReport(getBundle(locale), getSink());
            } else {
                report.doGenerateReport(getBundle(locale), getSink(), issues);
            }
        } catch (Exception ex) {
            getLog().warn(ex);
        }
    }

    @Override
    public String getOutputName() {
        return "jira-report";
    }

    @Override
    public String getName(Locale locale) {
        return getBundle(locale).getString("report.issues.name");
    }

    @Override
    public String getDescription(Locale locale) {
        return getBundle(locale).getString("report.issues.description");
    }

    private ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle("jira-report", locale, this.getClass().getClassLoader());
    }

    private void configureIssueDownloader(AbstractJiraDownloader issueDownloader) {
        issueDownloader.setLog(getLog());

        issueDownloader.setProject(project);

        issueDownloader.setOutput(jiraXmlPath);

        issueDownloader.setMaxEntries(maxEntries);

        issueDownloader.setComponentIds(componentIds);

        issueDownloader.setFixVersionIds(fixVersionIds);

        issueDownloader.setStatuses(statuses);

        issueDownloader.setResolutions(resolutions);

        issueDownloader.setPriorities(priorities);

        issueDownloader.setSortColumnNames(sortColumnNames);

        issueDownloader.setFilter(filter);

        issueDownloader.setJiraDatePattern(jiraDatePattern);

        if (jiraServerId != null) {
            final Server server = mavenSession.getSettings().getServer(jiraServerId);
            if (server != null) {
                issueDownloader.setJiraUser(server.getUsername());
                issueDownloader.setJiraPassword(server.getPassword());
            }
        }

        issueDownloader.setTypes(types);

        issueDownloader.setSettings(settings);

        issueDownloader.setOnlyCurrentVersion(onlyCurrentVersion);

        issueDownloader.setVersionPrefix(versionPrefix);
    }
}
