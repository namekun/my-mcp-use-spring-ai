package org.springframework.ai.mcp.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class AutoGitExecutor {
    private final File workingDirectory;

    public AutoGitExecutor() {
        this.workingDirectory = GitRootResolver.resolve();
    }

    public String execCapture(List<String> args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(this.workingDirectory);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(bout);
        }
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IOException("Git command timed out");
        }
        return bout.toString(StandardCharsets.UTF_8);
    }

    public int exec(List<String> args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(this.workingDirectory);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        boolean finished = p.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IOException("Git command timed out");
        }
        int exit = p.exitValue();
        if (exit != 0) {
            System.err.println("[Git Debug] Exit=" + exit + "\n" + output);
        }
        return exit;
    }
}
