package com.opencellsoft.plugin.tests.rest;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class TestRegex {

    @Test
    public void validateRegexTest() {
        final String regex = "/\\*\\*.+?\\*/(?!public class)";
        final String string = "package com.stitane.rest.utils; /** * javadoc for class */ public class ErrorInfo { private int errorCode; private String message; public ErrorInfo(int code, String msg) { errorCode = code; message = msg; } public ErrorInfo(String msg) { errorCode = -1; message = msg; } @Override public String toString() { StringBuilder sb = new StringBuilder(); sb.append(\" [\").append(errorCode).append(\":\").append(message).append(\"]\"); return sb.toString(); } public int getErrorCode() { return errorCode; } public void setErrorCode(int errorCode) { this.errorCode = errorCode; } public String getMessage() { return message; } public void setMessage(String message) { this.message = message; } } ";

        final Pattern pattern = Pattern.compile(regex, Pattern.COMMENTS);
        final Matcher matcher = pattern.matcher(string);

        if (matcher.find()) {
            System.out.println("Full match: " + matcher.group(0));
            for (int i = 1; i <= matcher.groupCount(); i++) {
                System.out.println("Group " + i + ": " + matcher.group(i));
            }
        } else {
            System.err.println("regex does not wrok " + regex);
        }
    }

    @Test
    public void testGitCommit() throws Exception {
        //Load repository
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder.setGitDir(new File(".git")).setMustExist(true).build();
        Git git = new Git(repository);
        Ref head = repository.findRef("HEAD");

        // a RevWalk allows to walk over commits based on some filtering that is defined
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(head.getObjectId());
            RevTree tree = commit.getTree();
            System.out.println("Having tree: " + tree);
            System.out.println("Last modified by: " + commit.getAuthorIdent().getName());
            System.out.println("Last modified at: " + LocalDateTime.ofEpochSecond(commit.getCommitTime() ,0, ZoneOffset.UTC));

            String path = "src/main/java/com/stitane/rest/dto/test.java";
            TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);

            if (treeWalk == null) {
                System.err.println("Did not find expected file '" + path + "' in tree '" + tree.getName() + "'");
            } else {
                System.out.println("found");
            }
        }
        git.close();
    }

    @Test
    public void extractJavadocFromClassTest() {
        final String string = "package com.stitane.rest.utils; /** * javadoc for class */ public class ErrorInfo { private int errorCode; private String message; public ErrorInfo(int code, String msg) { errorCode = code; message = msg; } public ErrorInfo(String msg) { errorCode = -1; message = msg; } @Override public String toString() { StringBuilder sb = new StringBuilder(); sb.append(\" [\").append(errorCode).append(\":\").append(message).append(\"]\"); return sb.toString(); } public int getErrorCode() { return errorCode; } public void setErrorCode(int errorCode) { this.errorCode = errorCode; } public String getMessage() { return message; } public void setMessage(String message) { this.message = message; } } ";

        int i = string.lastIndexOf("/**") + 3;
        int j = string.lastIndexOf("public class") - 2;

        String substring = string.substring(i, j).replaceAll("\\s\\*\\s", "");
        System.out.println(substring);
    }
}
