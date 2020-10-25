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

import java.io.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.doxia.site.decoration.Body;
import org.apache.maven.doxia.site.decoration.DecorationModel;
import org.apache.maven.doxia.site.decoration.Skin;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.doxia.siterenderer.RendererException;
import org.apache.maven.doxia.siterenderer.RenderingContext;
import org.apache.maven.doxia.siterenderer.SiteRenderingContext;
import org.apache.maven.doxia.siterenderer.sink.SiteRendererSink;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.codehaus.plexus.i18n.I18N;
import org.codehaus.plexus.util.ReaderFactory;

public abstract class AbstractJiraReport extends AbstractMavenReport {
    /**
     * The current project base directory.
     */
    @Parameter(property = "basedir", required = true)
    protected String basedir;

    /**
     * Report output directory. Note that this parameter is only relevant if the goal is run from the command line or
     * from the default build lifecycle. If the goal is run indirectly as part of a site generation, the output
     * directory configured in the Maven Site Plugin is used instead.
     */
    @Parameter(defaultValue = "${project.reporting.outputDirectory}")
    private File outputDirectory;

    /**
     * Report output encoding. Note that this parameter is only relevant if the goal is run from the command line or
     * from the default build lifecycle. If the goal is run indirectly as part of a site generation, the output encoding
     * configured in the Maven Site Plugin is used instead.
     */
    @Parameter(property = "outputEncoding", defaultValue = "${project.reporting.outputEncoding}")
    private String outputEncoding;

    /**
     * This will cause the execution to be run only at the top of a given module tree. That is, run in the project
     * contained in the same folder where the mvn execution was launched.
     */
    @Parameter(property = "jira.runOnlyAtExecutionRoot", defaultValue = "false")
    protected boolean runOnlyAtExecutionRoot;

    /**
     * The Maven session.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession mavenSession;

    /**
     * Doxia Site Renderer.
     */
    @Component
    protected Renderer siteRenderer;

    /**
     * The Maven Project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Component
    protected ArtifactResolver resolver;

    /**
     * Internationalization.
     */
    @Component
    protected I18N i18n;

    protected String getOutputDirectory() {
        return outputDirectory.getAbsolutePath();
    }

    protected String getOutputEncoding() {
        return outputEncoding == null ? ReaderFactory.UTF_8 : outputEncoding;
    }

    protected MavenProject getProject() {
        return project;
    }

    protected Renderer getSiteRenderer() {
        return siteRenderer;
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (!canGenerateReport()) {
            return;
        }

        Writer writer = null;
        try {
            DecorationModel model = new DecorationModel();
            model.setBody(new Body());
            Map<String, String> attributes = new HashMap<>();
            attributes.put("outputEncoding", getOutputEncoding());
            Locale locale = Locale.getDefault();
            SiteRenderingContext siteContext = siteRenderer.createContextForSkin(getSkinArtifact(), attributes, model,
                    getName(locale), locale);
            siteContext.setOutputEncoding(getOutputEncoding());

            RenderingContext context = new RenderingContext(outputDirectory, getOutputName() + ".html");

            SiteRendererSink sink = new SiteRendererSink(context);
            generate(sink, null, locale);

            //noinspection ResultOfMethodCallIgnored
            outputDirectory.mkdirs();

            File file = new File(outputDirectory, getOutputName() + ".html");
            writer = new OutputStreamWriter(new FileOutputStream(file), getOutputEncoding());

            siteRenderer.generateDocument(writer, sink, siteContext);

            writer.close();
            writer = null;

            siteRenderer.copyResources(siteContext, outputDirectory);
        } catch (RendererException | IOException | MavenReportException ex) {
            throw new MojoExecutionException("An error has occurred in " + getName(Locale.ENGLISH)
                    + " report generation.", ex);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }

    protected boolean isThisTheExecutionRoot() {
        getLog().debug("Root Folder: " + mavenSession.getExecutionRootDirectory());
        getLog().debug("Current Folder: " + basedir);
        boolean result = mavenSession.getExecutionRootDirectory().equalsIgnoreCase(basedir);
        if (result) {
            getLog().debug("This is the execution root.");
        } else {
            getLog().debug("This is NOT the execution root");
        }
        return result;
    }

    private Artifact getSkinArtifact() throws MojoExecutionException {
        Skin skin = Skin.getDefaultSkin();
        DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
        coordinate.setGroupId(skin.getGroupId());
        coordinate.setArtifactId(skin.getArtifactId());
        coordinate.setVersion(skin.getVersion());
        ProjectBuildingRequest pbr = new DefaultProjectBuildingRequest(mavenSession.getProjectBuildingRequest());
        pbr.setRemoteRepositories(project.getRemoteArtifactRepositories());
        try {
            return resolver.resolveArtifact(pbr, coordinate).getArtifact();
        } catch (ArtifactResolverException ex) {
            throw new MojoExecutionException("Couldn't resolve the skin.", ex);
        }
    }
}
