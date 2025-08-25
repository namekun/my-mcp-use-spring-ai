package org.springframework.ai.mcp.util;

import org.springframework.ai.mcp.service.LLMCommitMessageService;

import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

final class GitRootResolver {
    /**
     * Resolve a stable working directory that equals the nearest Git repository root
     * regardless of where the launcher (e.g., IntelliJ AI Assistant) starts the JAR.
     * Search order: GIT_WORK_DIR -> -Dgit.work.dir -> user.dir -> JAR location -> PWD.
     */
    static File resolve() {
        List<File> candidates = new ArrayList<>();

        String env = System.getenv("GIT_WORK_DIR");
        if (env != null && !env.isBlank()) candidates.add(new File(env));
        String prop = System.getProperty("git.work.dir");
        if (prop != null && !prop.isBlank()) candidates.add(new File(prop));

        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) candidates.add(new File(userDir));

        File jarLoc = locationOfThisJar();
        if (jarLoc != null) candidates.add(jarLoc);

        String pwd = System.getenv("PWD");
        if (pwd != null && !pwd.isBlank()) candidates.add(new File(pwd));

        for (File start : candidates) {
            File root = findGitRoot(start);
            if (root != null) {
                return root;
            }
        }

        if (!candidates.isEmpty()) return candidates.get(0).getAbsoluteFile();
        return new File(".").getAbsoluteFile();
    }

    /**
     * Walk upward from start until a `.git` directory or file is found (worktrees supported).
     */
    static File findGitRoot(File start) {
        File dir = start;
        try {
            while (dir != null) {
                File dotGit = new File(dir, ".git");
                if (dotGit.exists()) {
                    return dir.getCanonicalFile();
                }
                dir = dir.getParentFile();
            }
        } catch (IOException ignore) {
            // fall through
        }
        return null;
    }

    /**
     * Return the directory containing the running JAR, or the classes directory in IDE runs.
     */
    static File locationOfThisJar() {
        try {
            java.net.URL url = LLMCommitMessageService.class.getProtectionDomain().getCodeSource().getLocation();
            if (url == null) return null;
            File f = new File(url.toURI());
            File base = f.isFile() ? f.getParentFile() : f; // fat-jar vs classes dir
            return base != null ? base.getCanonicalFile() : null;
        } catch (Exception e) {
            return null;
        }
    }
}