package com.opencellsoft.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.opencellsoft.plugin.RestPlugin;

import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;

/**
 * {@link FileSetTransformer} class
 *
 * @author mohammed stitane
 */
public class FileSetTransformer {

    private RestPlugin plugin;
    private final FileSet fileSet;

    public FileSetTransformer(RestPlugin plugin, FileSet fileSet) {
        this.plugin = plugin;
        this.fileSet = fileSet;
    }

    public List<File> toFileList() throws MojoExecutionException {
        return toFileList(fileSet);
    }

    public List<File> toFileList(FileSet fs) throws MojoExecutionException {
        try {
            if (fs.getDirectory() != null) {
                File directory = new File(fs.getDirectory());
                String includes = toString(fs.getIncludes());
                String excludes = toString(fs.getExcludes());
                return FileUtils.getFiles(directory, includes, excludes);
            } else {
                plugin.getLog().warn(String.format("Fileset [%s] directory empty", fs.toString()));
                return new ArrayList<>();
            }
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Unable to get paths to fileset [%s]", fs.toString()), e);
        }
    }

    private String toString(List<String> strings) {
        StringBuilder sb = new StringBuilder();
        for (String string : strings) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(string);
        }
        return sb.toString();
    }
}
