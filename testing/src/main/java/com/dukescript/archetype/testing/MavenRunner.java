/**
 * The MIT License (MIT)
 *
 * Copyright (C) 2015 Anton Epple <toni.epple@eppleton.de>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.dukescript.archetype.testing;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.testng.reporters.Files;

final class MavenRunner extends Verifier {
    private static Logger LOG;
    private static final Timer TIMEOUT = new Timer("mvn timeout");

    static void initializeOutput() {
        if (LOG == null) {
            LOG = Logger.getLogger("maven");
            LOG.info("Initializing Maven Runner");
        }
    }
    static {
        initializeOutput();
    }

    public MavenRunner(String basedir) throws VerificationException {
        super(basedir);
    }

    @Override
    public void executeGoals(List<String> goals, Map envVars) throws VerificationException {
        long now = System.currentTimeMillis();
        final File log = new File(getBasedir(), getLogFileName());
        final AtomicInteger logPos = new AtomicInteger(0);
        LOG.log(Level.INFO, "executeGoals {0} in {1}", new Object[]{goals, log});
        TimerTask tt = new TimerTask() {
            int emptyCount;

            @Override
            public void run() {
                int at = logPos.get();
                if (at < 0) {
                    cancel();
                    return;
                }

                String content;
                try {
                    final String logContent = Files.readFile(log);
                    content = logContent.substring(at);
                    logPos.compareAndSet(at, logContent.length());
                } catch (IOException ex) {
                    content = "Cannot read " + log + ": " + ex.getMessage();
                }
                if (content.isEmpty()) {
                    if (emptyCount++ == 180) {
                        LOG.log(Level.WARNING, "No log output for three minutes");
                    }
                } else {
                    emptyCount = 0;
                    LOG.log(Level.WARNING, "\n{0}", content);
                }
            }
        };
        TIMEOUT.schedule(tt, 10 * 1000, 1 * 1000);
        try {
            super.executeGoals(goals, envVars);
            LOG.log(Level.INFO, "OK for {0}", goals);
        } catch (VerificationException ex) {
            LOG.log(Level.WARNING, "FAIL log file at {0}/{1}", new Object[]{getBasedir(), getLogFileName()});
            throw ex;
        } finally {
            long took = System.currentTimeMillis() - now;
            if (took > 10000) {
                LOG.log(Level.INFO, "Took {0}s", took / 1000);
            }
            logPos.set(-1);
            tt.cancel();
        }
    }

}
