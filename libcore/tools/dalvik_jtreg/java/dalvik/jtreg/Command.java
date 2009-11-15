/*
 * Copyright (C) 2009 The Android Open Source Project
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

package dalvik.jtreg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * An out of process executable.
 */
final class Command {

    private final List<String> args;
    private final boolean permitNonZeroExitStatus;
    private Process process;

    Command(String... args) {
        this(Arrays.asList(args));
    }

    Command(List<String> args) {
        this.args = new ArrayList<String>(args);
        this.permitNonZeroExitStatus = false;
    }

    private Command(Builder builder) {
        this.args = new ArrayList<String>(builder.args);
        this.permitNonZeroExitStatus = builder.permitNonZeroExitStatus;
    }

    static String path(Object... objects) {
        return Strings.join(objects, ":");
    }

    public synchronized void start() throws IOException {
        if (isStarted()) {
            throw new IllegalStateException("Already started!");
        }

        process = new ProcessBuilder()
                .command(args)
                .redirectErrorStream(true)
                .start();
    }

    public boolean isStarted() {
        return process != null;
    }

    public Process getProcess() {
        if (!isStarted()) {
            throw new IllegalStateException("Not started!");
        }

        return process;
    }

    public synchronized List<String> gatherOutput()
            throws IOException, InterruptedException {
        if (!isStarted()) {
            throw new IllegalStateException("Not started!");
        }

        BufferedReader in = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        List<String> outputLines = new ArrayList<String>();
        String outputLine;
        while ((outputLine = in.readLine()) != null) {
            outputLines.add(outputLine);
        }

        if (process.waitFor() != 0 && !permitNonZeroExitStatus) {
            StringBuilder message = new StringBuilder();
            for (String line : outputLines) {
                message.append("\n").append(line);
            }
            throw new CommandFailedException(args, outputLines);
        }

        return outputLines;
    }

    public synchronized List<String> execute() {
        try {
            start();
            return gatherOutput();
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute process: " + args, e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while executing process: " + args, e);
        }
    }

    static class Builder {
        private final List<String> args = new ArrayList<String>();
        private boolean permitNonZeroExitStatus = false;

        public Builder args(String... args) {
            return args(Arrays.asList(args));
        }

        public Builder args(Collection<String> args) {
            this.args.addAll(args);
            return this;
        }

        public Builder permitNonZeroExitStatus() {
            permitNonZeroExitStatus = true;
            return this;
        }

        public Command build() {
            return new Command(this);
        }

        public List<String> execute() {
            return build().execute();
        }
    }
}