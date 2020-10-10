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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import dev.nittenapps.maven.plugins.issues.Issue;
import org.apache.commons.lang.StringUtils;
import org.apache.cxf.configuration.security.ProxyAuthorizationPolicy;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.transports.http.configuration.ProxyServerType;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Implementation of the JIRA REST Api client, that uses the version 3 of the Api.
 *
 * @see <a href="https://developer.atlassian.com/cloud/jira/platform/rest/v3/">JIRA Cloud REST API v3</a>
 */
class RestJiraDownloader extends AbstractJiraDownloader {
    private List<Issue> issueList;

    private final JsonFactory jsonFactory;

    private final SimpleDateFormat dateFormat;

    private final Locale locale;

    public RestJiraDownloader(Locale locale) {
        jsonFactory = new MappingJsonFactory();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        this.locale = locale;
    }

    @Override
    public void doExecute() throws Exception {
        Map<String, String> urlMap = JiraHelper.getJiraUrlAndProjectName(project.getIssueManagement().getUrl());
        String jiraUrl = urlMap.get("url");
        WebClient client = setupWebClient(jiraUrl);

        // check if version 3 of the REST API is available
        client.replacePath("/rest/api/3/serverInfo");
        client.accept(MediaType.APPLICATION_JSON_TYPE);
        Response response = client.get();
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
            throw new IllegalArgumentException("This JIRA server does not support version 3 of the REST API");
        }

        addAuthHeader(client);

        String jiraProject = urlMap.get("project");
        String jqlQuery = new JqlQueryBuilder(log)
                .urlEncode(false)
                .project(jiraProject)
                .fixVersion(getFixFor())
                .fixVersionIds(fixVersionIds == null ? null : fixVersionIds.split(","))
                .statuses(statuses == null ? null : statuses.split(","))
                .priorities(priorities == null ? null : priorities.split(","))
                .resolutions(resolutions == null ? null : resolutions.split(","))
                .components(componentIds == null ? null : componentIds.split(","))
                .types(types == null ? null : types.split(","))
                .sortColumnNames(sortColumnNames)
                .filter(filter)
                .build();
        StringWriter searchParamStringWriter = new StringWriter();
        JsonGenerator gen = jsonFactory.createGenerator(searchParamStringWriter);
        gen.writeStartObject();
        gen.writeStringField("jql", jqlQuery);
        gen.writeNumberField("maxResults", maxEntries);
        gen.writeArrayFieldStart("fields");
        gen.writeString("*all");
        gen.writeEndArray();
        gen.writeEndObject();
        gen.close();
        client.replacePath("/rest/api/3/search");
        client.type(MediaType.APPLICATION_JSON_TYPE);
        client.accept(MediaType.APPLICATION_JSON_TYPE);
        client.acceptLanguage(locale.getLanguage());
        client.header("X-Force-Accept-Language", true);
        Response searchResponse = client.post(searchParamStringWriter.toString());
        if (searchResponse.getStatus() != Response.Status.OK.getStatusCode()) {
            reportErrors(searchResponse);
        }

        JsonNode issueTree = getResponseTree(searchResponse);
        assert issueTree.isObject();
        JsonNode issuesNode = issueTree.get("issues");
        assert issuesNode.isArray();
        buildIssues(issuesNode, jiraUrl);
    }

    @Override
    public List<Issue> getIssueList() {
        return issueList;
    }

    private JsonNode getResponseTree(Response response) throws IOException {
        JsonParser jsonParser = jsonFactory.createParser((InputStream) response.getEntity());
        return jsonParser.readValueAsTree();
    }

    private void addAuthHeader(WebClient client) {
        if (isJiraAuthenticationConfigured()) {
            String authorization = Base64.getEncoder().encodeToString((jiraUser + ":" + jiraPassword).getBytes());
            client.header("Authorization", "Basic " + authorization);
        }
    }

    private void buildIssues(JsonNode issuesNode, String jiraUrl) {
        issueList = new ArrayList<>();
        for (int ix = 0; ix < issuesNode.size(); ix++) {
            JsonNode issueNode = issuesNode.get(ix);
            assert issueNode.isObject();
            Issue issue = new Issue();
            JsonNode val;

            val = issueNode.get("id");
            if (val != null) {
                issue.setId(val.asText());
            }

            val = issueNode.get("key");
            if (val != null) {
                issue.setKey(val.asText());
                issue.setLink(String.format("%s/browse/%s", jiraUrl, val.asText()));
            }

            // much of what we want is in here.
            JsonNode fieldsNode = issueNode.get("fields");

            val = fieldsNode.get("assignee");
            processAssignee(issue, val);

            val = fieldsNode.get("created");
            processCreated(issue, val);

            val = fieldsNode.get("comment");
            processComments(issue, val);

            val = fieldsNode.get("components");
            processComponents(issue, val);

            val = fieldsNode.get("fixVersions");
            processFixVersions(issue, val);

            val = fieldsNode.get("issuetype");
            processIssueType(issue, val);

            val = fieldsNode.get("priority");
            processPriority(issue, val);

            val = fieldsNode.get("reporter");
            processReporter(issue, val);

            val = fieldsNode.get("resolution");
            processResolution(issue, val);

            val = fieldsNode.get("status");
            processStatus(issue, val);

            val = fieldsNode.get("summary");
            if (val != null) {
                issue.setSummary(val.asText());
            }

            val = fieldsNode.get("title");
            if (val != null) {
                issue.setTitle(val.asText());
            }

            val = fieldsNode.get("updated");
            processUpdated(issue, val);

            val = fieldsNode.get("versions");
            processVersions(issue, val);

            issueList.add(issue);
        }
    }

    private String getPerson(JsonNode val) {
        JsonNode nameNode = val.get("displayName");
        if (nameNode == null) {
            nameNode = val.get("name");
        }
        if (nameNode != null) {
            return nameNode.asText();
        } else {
            return null;
        }
    }

    private MediaType getResponseMediaType(Response response) {
        String header = (String) response.getMetadata().getFirst(HttpHeaders.CONTENT_TYPE);
        return header == null ? null : MediaType.valueOf(header);
    }

    private Date parseDate(JsonNode val) throws ParseException {
        return dateFormat.parse(val.asText());
    }

    private void processAssignee(Issue issue, JsonNode val) {
        if (val != null) {
            String text = getPerson(val);
            if (text != null) {
                issue.setAssignee(text);
            }
        }
    }

    private void processComments(Issue issue, JsonNode val) {
        if (val != null) {
            JsonNode commentsArray = val.get("comments");
            for (int cx = 0; cx < commentsArray.size(); cx++) {
                JsonNode node = commentsArray.get(cx);
                issue.addComment(node.get("body").asText());
            }
        }
    }

    private void processComponents(Issue issue, JsonNode val) {
        if (val != null) {
            assert val.isArray();
            for (int cx = 0; cx < val.size(); cx++) {
                JsonNode node = val.get(cx);
                issue.addComponent(node.get("name").asText());
            }
        }
    }

    private void processCreated(Issue issue, JsonNode val) {
        if (val != null) {
            try {
                issue.setCreated(parseDate(val));
            } catch (ParseException e) {
                log.warn("Invalid created date " + val.asText());
            }
        }
    }

    private void processFixVersions(Issue issue, JsonNode val) {
        if (val != null) {
            assert val.isArray();
            for (int vx = 0; vx < val.size(); vx++) {
                JsonNode node = val.get(vx);
                issue.addFixVersion(node.get("name").asText());
            }
        }
    }

    private void processIssueType(Issue issue, JsonNode val) {
        if (val != null) {
            issue.setType(val.get("name").asText());
        }
    }

    private void processReporter(Issue issue, JsonNode val) {
        if (val != null) {
            String text = getPerson(val);
            if (text != null) {
                issue.setReporter(text);
            }
        }
    }

    private void processPriority(Issue issue, JsonNode val) {
        if (val != null) {
            issue.setPriority(val.get("name").asText());
        }
    }

    private void processResolution(Issue issue, JsonNode val) {
        if (val != null) {
            issue.setResolution(val.get("name").asText());
        }
    }

    private void processStatus(Issue issue, JsonNode val) {
        if (val != null) {
            issue.setStatus(val.get("name").asText());
        }
    }

    private void processUpdated(Issue issue, JsonNode val) {
        if (val != null) {
            try {
                issue.setUpdated(parseDate(val));
            } catch (ParseException e) {
                log.warn("Invalid updated date " + val.asText());
            }
        }
    }

    private void processVersions(Issue issue, JsonNode val) {
        StringBuilder sb = new StringBuilder();
        if (val != null) {
            for (int vx = 0; vx < val.size(); vx++) {
                sb.append(val.get(vx).get("name").asText());
                sb.append(", ");
            }
        }
        if (sb.length() > 0) {
            // remove last ", "
            issue.setVersion(StringUtils.removeEnd(sb.toString(), ", "));
        }
    }

    private void reportErrors(Response response) throws IOException, MojoExecutionException {
        if (MediaType.APPLICATION_JSON_TYPE.equals(getResponseMediaType(response))) {
            JsonNode errorTree = getResponseTree(response);
            assert errorTree.isObject();
            JsonNode messages = errorTree.get("errorMessages");
            if (messages != null) {
                for (int i = 0; i < messages.size(); i++) {
                    log.error(messages.get(i).asText());
                }
            } else {
                JsonNode message = errorTree.get("message");
                if (message != null) {
                    log.error(message.asText());
                }
            }
        }
        throw new MojoExecutionException(String.format("Failed to query issues; response %d", response.getStatus()));
    }

    private WebClient setupWebClient(String jiraUrl) {
        WebClient client = WebClient.create(jiraUrl);

        ClientConfiguration clientConfiguration = WebClient.getConfig(client);
        HTTPConduit http = clientConfiguration.getHttpConduit();
        clientConfiguration.getRequestContext().put(Message.MAINTAIN_SESSION, Boolean.TRUE);

        if (log.isDebugEnabled()) {
            clientConfiguration.getInInterceptors().add(new LoggingInInterceptor());
            clientConfiguration.getOutInterceptors().add(new LoggingOutInterceptor());
        }

        HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
        log.debug("RestJiraDownloader - connectionTimeout: " + connectionTimeout);
        log.debug("RestJiraDownloader - responseTimeout: " + responseTimeout);
        httpClientPolicy.setConnectionTimeout(connectionTimeout);
        httpClientPolicy.setReceiveTimeout(responseTimeout);
        httpClientPolicy.setAllowChunking(false);

        getProxyInfo(jiraUrl);
        if (proxyHost != null) {
            log.debug("Using proxy " + proxyHost + " at port " + proxyPort);
            httpClientPolicy.setProxyServer(proxyHost);
            httpClientPolicy.setProxyServerPort(proxyPort);
            httpClientPolicy.setProxyServerType(ProxyServerType.HTTP);
            if (proxyUser != null) {
                ProxyAuthorizationPolicy proxyAuthorizationPolicy = new ProxyAuthorizationPolicy();
                proxyAuthorizationPolicy.setAuthorizationType("Basic");
                proxyAuthorizationPolicy.setUserName(proxyUser);
                proxyAuthorizationPolicy.setPassword(proxyPass);
                http.setProxyAuthorization(proxyAuthorizationPolicy);
            }
        }

        http.setClient(httpClientPolicy);
        return client;
    }
}
