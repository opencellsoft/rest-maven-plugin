
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.opencellsoft.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.opencellsoft.dto.ScriptInstance;
import com.opencellsoft.utils.ErrorInfo;
import com.opencellsoft.utils.FileErrorInfo;
import com.opencellsoft.utils.FileSetTransformer;

import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.components.io.filemappers.FileMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Convert a java file to opencell scripts xml format
 * Make REST request, sending file contents and saving results to a file if needed.
 * <p>
 * This plugin is meant to provide an easy way to interface to REST services via
 * the POST operation to send data files to the REST URL and retrieve (and
 * store) the results.
 *
 * @author mohammed stitane
 */
@Mojo(name = "rest-request")
public class RestPlugin extends AbstractMojo {

    private static final String PACKAGE_PATTERN = "package([\\s)+\\w\\\\.]+)";
    private static final String CLASSNAME_PATTERN = "(?<=\\n|\\A)(?:public\\s)?(class|interface|enum)\\s([^\\n\\s]*)";

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    private MojoExecution mojo;

    @Parameter(defaultValue = "${plugin}", readonly = true) // Maven 3 only
    private PluginDescriptor plugin;

    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Base directory for build.
     * <p>
     * Currently unused, but exists for possible future use.
     * <p>
     * Default <code>${project.basedir}</code>
     */
    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File basedir;

    /**
     * Base directory for target.
     * <p>
     * Currently unused, but exists for possible future use.
     * <p>
     * Default <code>${project.build.directory}</code>
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File target;

    /**
     * A URL path to the base of the REST request resource.
     * <p>
     * This URL path is the base path, and can be used with multiple instances
     * (executions) in combination with the <code>resource</code> element to
     * specify different URL resources with a common base URL.
     */
    @Parameter(property = "endpoint")
    private URI endpoint;

    /**
     * A resource path added to the endpoint URL to access the REST resource.
     * <p>
     * The <code>resource</code> path will be concatenated onto the
     * <code>endpoint</code> URL to create the full resource path.
     * <p>
     * Query parameters can be added to the URL <code>resource</code> but the
     * preference is to use the <code>queryParams</code> map to add parameters
     * to the URL.
     */
    @Parameter(property = "resource")
    private String resource;

    /**
     * The method to use for the REST request.
     * <p>
     * The REST request method can be configured via the <code>method</code>
     * tag. Currently only the <code>POST</code> and <code>GET</code> requests
     * are fully tested and supported. Other methods requiring data upload
     * (<code>PUT</code>, <code>PATCH</code>) should be supported identically to
     * the <code>POST</code> request, but have not been tested.
     * <p>
     * If <code>GET</code> is used, the code will upload a file if the
     * <code>fileset<code> is defined when making the <code>GET</code> request.
     * <p>
     * Defaults to <code>POST</code>
     */
    @Parameter(property = "method")
    private String method = "POST";

    /**
     * A list of {@link FileSet} rules to select files
     * and directories.
     * <p>
     * This list of <code>fileset</code> elements will be used to gather all the
     * files to be submitted in the REST request. One REST request will be made
     * per file.
     */
    @Parameter(property = "filesets")
    private List<FileSet> filesets = new ArrayList<>();

    /**
     * A {@link FileSet} rule to select files to send in
     * the REST request.
     * <p>
     * The fileset will be used to gather all the files to be submitted in the
     * REST request. One REST request will be made per file.
     * <p>
     * Internally, this element will be added to the list of
     * <code>filesets</code>, so it will be processed in addition to the list of
     * <code>filesets</code>
     */
    @Parameter(property = "fileset")
    private FileSet fileset;

    /**
     * Path where REST query result files are stored.
     * <p>
     * Defaults to <code>${project.build.directory}/rest</code>
     */
    @Parameter(defaultValue = "${project.build.directory}/rest", property = "outputDir")
    private File outputDir;

    /**
     * Filename where REST GET query result files are stored, if no fileset is
     * defined.
     * <p>
     * Defaults to <code>rest.file</code>
     */
    @Parameter(defaultValue = "rest.file", property = "outputFilename")
    private File outputFilename;

    /**
     * A <code>map</code> of query parameters to add to the REST request URL.
     * <p>
     * The <code>queryParams</code> element will provide a way to add multiple
     * query params to the final REST URL.
     */
    @Parameter(property = "queryParams")
    private Map<String, String> queryParams;

    /**
     * A <code>map</code> of query headers to add to the REST request.
     * <p>
     * The <code>headers</code> element will provide a way to add multiple
     * header elements to the final REST request.
     */
    @Parameter(property = "headers")
    private Map<String, String> headers;

    /**
     * A {@link FileMapper} object
     * to generate output filenames.
     * <p>
     * Provide a FileMapper to generate the output filename which is used to
     * store the REST query results.
     * <p>
     * Unlike the <code>fileset</code> process, an individual
     * <code>fileMapper</code> element will be used *instead of* the
     * <code>fileMappers</code> list. If multiple <code>fileMapper</code>
     * elements must be applied to each file, then do not specify the individual
     * <code>fileMapper</code> element.
     */
    @Parameter(property = "filemapper")
    private FileMapper fileMapper;

    /**
     * A list of <code>fileMapper</code> rules to generate output filenames.
     */
    @Parameter(property = "filemappers")
    private List<FileMapper> fileMappers;

    /**
     * The type of the data sent by the REST request.
     * <p>
     * The data type of the REST request data. Default
     * <code>MediaType.TEXT_PLAIN_TYPE</code>
     * <p>
     * If this is specified, use the elements for MediaType class:
     *
     * <pre>
     *     &lt;requestType&gt;
     *       &lt;type&gt;application&lt;/type&gt;
     *       &lt;subtype&gt;json&lt;/subtype&gt;
     *     &lt;/requestType&gt;
     * </pre>
     */
    @Parameter
    private MediaType requestType = MediaType.TEXT_PLAIN_TYPE;

    /**
     * The type of the data returned by the REST request.
     * <p>
     * The expected data type of the REST response. Default
     * <code>MediaType.APPLICATION_OCTET_STREAM_TYPE</code>
     * <p>
     * See <code>requestType</code> for example of usage.
     */
    @Parameter
    private MediaType responseType = MediaType.APPLICATION_OCTET_STREAM_TYPE;

    /**
     * The Plexus BuildContext is used to identify files or directories modified
     * since last build, implying functionality used to define if java
     * generation must be performed again.
     */
    @Component(role = BuildContext.class)
    private BuildContext buildContext;

    /**
     * Note that the execution parameter will be injected ONLY if this plugin is
     * executed as part of a maven standard lifecycle - as opposed to directly
     * invoked with a direct invocation. When firing this mojo directly (i.e.
     * {@code mvn rest:something} ), the {@code execution} object will not be
     * injected.
     */
    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    private MojoExecution execution;
    /**
     * A <code>boolean</code> that indicate if save response or not
     * <p>
     * <p>
     * default value is false
     */
    @Parameter(property = "saveResponse", defaultValue = "false")
    private Boolean saveResponse;

    @Parameter(property = "inputDir", defaultValue = "")
    private String inputDir;

    @Parameter(property = "javaFile")
    private String javaFile;

    private <T> T getInjectedObject(final T objectOrNull, final String objectName) {
        if (objectOrNull == null) {
            getLog().error(String.format("Found null [%s]: Maven @Component injection was not done properly.", objectName));
        }

        return objectOrNull;
    }

    /**
     * The Plexus BuildContext is used to identify files or directories modified
     * since last build, implying functionality used to define if java
     * generation must be performed again.
     *
     * @return the active Plexus BuildContext.
     */
    protected final BuildContext getBuildContext() {
        return getInjectedObject(buildContext, "buildContext");
    }

    /**
     * @return The active MavenProject.
     */
    protected final MavenProject getProject() {
        return getInjectedObject(project, "project");
    }

    /**
     * @return The active MojoExecution.
     */
    public MojoExecution getExecution() {
        return getInjectedObject(execution, "execution");
    }

    private List<File> getFilesToProcess() throws MojoExecutionException {
        List<File> files = new ArrayList<>();
        if (null != getFileset()) {
            if (null == getFilesets()) {
                filesets = new ArrayList<>();
            }
            getFilesets().add(getFileset());

        }
        if (null != getFilesets()) {
            for (FileSet fs : getFilesets()) {
                if ((null != fs) && (null != fs.getDirectory())) {
                    FileSetTransformer fileMgr = new FileSetTransformer(this, fs);
                    files.addAll(fileMgr.toFileList());
                }
            }
        }
        return files;
    }

    protected String readStream(InputStream in) throws MojoExecutionException {
        byte[] buf = new byte[1024];
        int sz = 0;
        StringBuilder result = new StringBuilder();
        try {
            while (sz != -1) {
                sz = in.read(buf);
                result.append(buf);
            }
            return result.toString();
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to read result stream", e);
        }

    }

    protected <T> String wrap(String prefix, String suffix, List<T> tokens) {
        StringBuilder str = new StringBuilder();
        for (T s : tokens) {
            str.append(prefix);
            str.append(s.toString());
            str.append(suffix);
        }
        return str.toString();
    }

    protected <T> String join(String delim, List<T> tokens) {
        StringBuilder str = new StringBuilder();
        for (T s : tokens) {
            str.append(s.toString());
            str.append(delim);
        }
        return str.toString().substring(0, -delim.length());
    }

    protected void pipeToFile(InputStream stream, File outputFile) throws IOException {
        getLog().debug(String.format("Writing file [%s]", outputFile.getCanonicalPath()));
        try (OutputStream outStream = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
            IOUtils.closeQuietly(stream);
            IOUtils.closeQuietly(outStream);
        }
    }

    protected String remapFilename(String filename) {
        String remappedName = filename;
        if (null != getFileMapper()) {
            return getFileMapper().getMappedFileName(filename);
        } else { // iteratively modify the filename, apply all mappers in order
            if (null != getFileMappers()) {
                for (FileMapper fm : getFileMappers()) {
                    if (null != fm) {
                        remappedName = fm.getMappedFileName(remappedName);
                    }
                }
            }
        }

        return remappedName;
    }

    protected boolean validateOutputDir() throws MojoExecutionException {
        try {
            if (null == getOutputDir()) {
                MavenProject mavenProject = getProject();
                if (mavenProject != null) {
                    outputDir = new File(mavenProject.getBuild().getDirectory(), "scripts");
                } else {
                    throw new MojoExecutionException(String.format("output dir is missing [%s]", outputDir));
                }
            }

            if (!outputDir.isDirectory()) {
                if (outputDir.isFile()) {
                    getLog().error(String.format("Error: [%s] is not a directory", outputDir.getCanonicalPath()));
                } else {
                    if (!outputDir.mkdirs()) {
                        getLog().error(String.format("Error: Unable to create path[%s]", outputDir.getCanonicalPath()));

                    }
                }
            }
        } catch (IOException ex) {
            getLog().error(String.format("Exception : [%s]", ex.toString()));
            throw new MojoExecutionException(String.format("Unable to create destination dir [%s]: [%s]", outputDir.toString(), ex.toString()));
        }
        return true;
    }

    @Override
    public void execute() throws MojoExecutionException {
        validateOutputDir();
        getLog().info(String.format("Output dir [%s]", new File(getOutputDir().toString()).getAbsolutePath()));

        Client client = ClientBuilder.newClient();

        WebTarget baseTarget = client.target(getEndpoint());
        baseTarget = validateAndAddResource(baseTarget);
        baseTarget = validateAndAddQueryParams(baseTarget);

        Invocation.Builder builder = baseTarget.request(getRequestType()).accept(getResponseType());
        builder = validateAndAddHeadrs(builder);
        getLog().info(String.format("Endpoint: [%s %s]", getMethod(), baseTarget.getUri()));

        getLog().info(String.format("Generating xml scripts into [%s]", getOutputDir().getAbsolutePath()));

        createScriptsFromJavaFiles(getInputDir(), getJavaFile());
        List<ErrorInfo> errorFiles = new ArrayList<>();
        List<File> files = getFilesToProcess();

        if ((null == files) || (files.isEmpty())) {
            if (!getMethod().equalsIgnoreCase("GET")) {
                getLog().error("No files to process");
                return;
            } else {
                getLog().debug("GET request");
                Response response = builder.method(getMethod());
                ErrorInfo result = processResponse(response, remapFilename(getOutputFilename().getName()));
                if (result != null) {
                    errorFiles.add(result);
                }
            }
        }

        for (File f : files) {
            getLog().debug(String.format("Submitting file [%s]", f.toString()));
            Response response = builder.method(getMethod(), Entity.entity(f, getRequestType()));
            ErrorInfo result = processResponse(response, remapFilename(f.getName()));
            if (result != null) {
                errorFiles.add(new FileErrorInfo(f.getPath(), result));
            }
        }

        if (!errorFiles.isEmpty()) {
            throw new MojoExecutionException(String.format("Unable to process files:%n%s", wrap("  ", "%n", errorFiles)));
        }
    }

    private Invocation.Builder validateAndAddHeadrs(Invocation.Builder builder) {
        if (null != getHeaders()) {
            getLog().info("load up the header info");
            for (String k : getHeaders().keySet()) {
                String hdr = getHeaders().get(k);
                builder = builder.header(k, hdr);
                getLog().debug(String.format("Header [%s:%s]", k, hdr));
            }
        }
        return builder;
    }

    private WebTarget validateAndAddQueryParams(WebTarget baseTarget) {
        if (null != getQueryParams()) {
            getLog().info("load up the query parameters");
            for (String k : getQueryParams().keySet()) {
                String param = getQueryParams().get(k);
                baseTarget = baseTarget.queryParam(k, param);
                getLog().debug(String.format("Param [%s:%s]", k, param));
            }
        }
        return baseTarget;
    }

    private WebTarget validateAndAddResource(WebTarget baseTarget) {
        if (null != getResource()) {
            getLog().info(String.format("Setting resource [%s]", getResource()));
            baseTarget = baseTarget.path(getResource());
        }
        return baseTarget;
    }

    /**
     * generate scripts xml format from java files
     *
     * @param javaFilesDir a package to process
     * @param javaFile     a java file to process
     */
    private void createScriptsFromJavaFiles(String javaFilesDir, String javaFile) {
        List<File> javaFiles = new ArrayList<>();
        if (javaFilesDir != null) {
            File dir = new File(javaFilesDir);
            if (!dir.exists() || dir.isDirectory()) {
                javaFiles.addAll(getAllJavaFilesInDir(dir));
            }
        }
        if (javaFile != null) {
            File singleJavaFile = new File(javaFile);
            if (singleJavaFile.exists() && singleJavaFile.getName().endsWith(".java")) {
                javaFiles.add(singleJavaFile);
            } else {
                getLog().warn(String.format("can not process this file %s", javaFile));
            }
        }
        getLog().debug(String.format("processing files %d", javaFiles.size()));
        processConversion(javaFiles, getOutputDir());
    }

    private List<File> getAllJavaFilesInDir(File dir) {
        List<File> javaFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Paths.get(dir.getAbsolutePath()))) {

            return walk.filter(Files::isRegularFile).filter(f -> f.toFile().getName().endsWith(".java")).map(Path::toFile).collect(Collectors.toList());

        } catch (Exception e) {
            getLog().info(String.format("error reading dir %s", dir), e);
        }
        return javaFiles;
    }

    private ErrorInfo processResponse(Response response, String outputFilename) {
        if (Boolean.TRUE.equals(getSaveResponse())) {
            InputStream in = response.readEntity(InputStream.class);
            try {
                File of = new File(getOutputDir(), outputFilename + ".response");
                pipeToFile(in, of);
            } catch (IOException ex) {
                getLog().debug(String.format("IOException: [%s]", ex.toString()));
                return new ErrorInfo(String.format("IOException: [%s]", ex.getMessage()));
            }
        }
        if (response.getStatusInfo().getFamily() == Family.SUCCESSFUL) {
            getLog().debug(String.format("Status: [%d]", response.getStatus()));
        } else {
            getLog().error(String.format("Error code: [%d]", response.getStatus()));
            getLog().debug(response.getEntity().toString());
            return new ErrorInfo(response.getStatus(), response.getEntity().toString());
        }
        return null;
    }

    private void processConversion(List<File> javaFiles, File outDir) {
        if (outDir.exists()) {
            // delete old content
            for (File file : outDir.listFiles()) {
                try {
                    Files.delete(file.toPath());
                } catch (IOException e) {
                    getLog().debug(e);
                }
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        for (File file : javaFiles) {
            ScriptInstance dto = new ScriptInstance();
            try {
                String source = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
                String code = getFullClassName(source);
                String description = getDescription(source) + getGitInformation(file);
                dto.setDescription(description);
                dto.setScript(source);
                dto.setCode(code);

                mapper.writeValue(new File(outDir, dto.getCode().concat(".json")), dto);

            } catch (Exception e) {
                getLog().error("Error when reading " + file.getName(), e);
            }
        }
    }

    private String getGitInformation(File file) {
        String result = "";
        try {
            //Load repository
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder.setGitDir(new File(".git")).setMustExist(true).build();
            Git git = new Git(repository);
            Ref head = repository.findRef("HEAD");

            // a RevWalk allows to walk over commits based on some filtering that is defined
            String name;
            String time;
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(head.getObjectId());
                RevTree tree = commit.getTree();
                name = commit.getAuthorIdent().getName();
                time = LocalDateTime.ofEpochSecond(commit.getCommitTime(), 0, ZoneOffset.UTC).toString();

                getLog().debug("file " + file);
                String path = file.getPath().substring(file.getPath().indexOf("src")).replaceAll("\\\\", "/");
                getLog().debug("path " + path);
                TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);

                if (treeWalk == null) {
                    getLog().info("Did not find expected file '" + path + "' in tree '" + tree.getName() + "'");
                } else {
                    result = String.format("\tLast updated By %s on %s", name, time);
                }
            }
            git.close();
        } catch (Exception e) {
            getLog().error("can not get git information for file " + file.getName());
        }
        return result;
    }

    private String patternMatches(String source, String regex, int group) {
        Pattern pattern = Pattern.compile(regex);

        Matcher m = pattern.matcher(source);
        String text = "";
        if (m.find()) {
            text = m.group(group);
        }
        return text;
    }

    private String getDescription(String source) {
        int i = source.indexOf("/**");
        int j = source.indexOf("public class") - 2;

        if (i > 0 && i < j) {
            String javadoc = source.substring(i + 3, j);

            javadoc = javadoc.trim();
            javadoc = javadoc.replaceAll("\\r|\\n", "");
            javadoc = javadoc.replaceAll("\\s\\*|\\*\\s", "");
            javadoc = javadoc.replaceAll("\\*/", "");
            javadoc = javadoc.replaceAll("/", "");
            return javadoc;
        }
        return "";
    }

    private String getFullClassName(String source) {
        String packageName = patternMatches(source, PACKAGE_PATTERN, 1);
        packageName = Strings.isNullOrEmpty(packageName) ? packageName : packageName.concat(".");
        String className = patternMatches(source, CLASSNAME_PATTERN, 2);

        return packageName.concat(className).trim();
    }

    /**
     * @return the endpoint
     */
    public URI getEndpoint() {
        return endpoint;
    }

    /**
     * @return the resource
     */
    public String getResource() {
        return resource;
    }

    /**
     * @return the filesets
     */
    public List<FileSet> getFilesets() {
        return filesets;
    }

    /**
     * @return the fileset
     */
    public FileSet getFileset() {
        return fileset;
    }

    /**
     * @return the outputDir
     */
    public File getOutputDir() {
        return outputDir;
    }

    /**
     * @return the outputFilename
     */
    public File getOutputFilename() {
        return outputFilename;
    }

    /**
     * @return the requestType
     */
    public MediaType getRequestType() {
        return requestType;
    }

    /**
     * @return the responseType
     */
    public MediaType getResponseType() {
        return responseType;
    }

    /**
     * @return the queryParams
     */
    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    /**
     * @return the headers
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * @return the fileMapper
     */
    public FileMapper getFileMapper() {
        return fileMapper;
    }

    /**
     * @return the fileMappers
     */
    public List<FileMapper> getFileMappers() {
        return fileMappers;
    }

    /**
     * @return the basedir
     */
    public File getBasedir() {
        return basedir;
    }

    /**
     * @return the target
     */
    public File getTarget() {
        return target;
    }

    /**
     * @return the projectHelper
     */
    public MavenProjectHelper getProjectHelper() {
        return projectHelper;
    }

    /**
     * @return the method
     */
    public String getMethod() {
        return method;
    }

    /**
     * @param method the method to set
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * @return the save response
     */
    public Boolean getSaveResponse() {
        return saveResponse;
    }

    public void setSaveResponse(Boolean saveResponse) {
        this.saveResponse = saveResponse;
    }

    /**
     * @return the input dir
     */
    public String getInputDir() {
        return inputDir;
    }

    public void setInputDir(String inputDir) {
        this.inputDir = inputDir;
    }

    /**
     * @return the java file
     */
    public String getJavaFile() {
        return javaFile;
    }

    public void setJavaFile(String javaFile) {
        this.javaFile = javaFile;
    }
}
