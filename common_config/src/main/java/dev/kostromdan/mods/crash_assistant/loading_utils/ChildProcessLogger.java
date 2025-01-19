package dev.kostromdan.mods.crash_assistant.loading_utils;

import com.mojang.logging.LogUtils;
import org.apache.logging.log4j.Level;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

public class ChildProcessLogger extends Thread {
    private final InputStream is;
    private final Level logLevel;
    public ChildProcessLogger anotherChildProcessLogger;
    private static Process crashAssistantAppProcess;
    private static final String startedSuccessfullyMessage = "CrashAssistantApp started successfully. Waiting for PID " + ProcessHandle.current().pid() + " to stop.";
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();


    public ChildProcessLogger(InputStream is, Level logLevel) {
        this.setDaemon(true);
        this.setName("CrashAssistantApp process logger");
        this.is = is;
        this.logLevel = logLevel;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            boolean readerReady;
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (ChildProcessLogger.class) {
                    readerReady = reader.ready();
                    if (!readerReady && !crashAssistantAppProcess.isAlive()) {
                        if (anotherChildProcessLogger.isInterrupted()) {
                            LOGGER.error("CrashAssistantApp failed to start with exit code " + crashAssistantAppProcess.exitValue() + ";  Stopping child process loggers.");
                            break;
                        }
                        this.interrupt();
                        return;
                    }
                }
                if (!readerReady) {
                    Thread.sleep(50);
                    continue;
                }
                String line = reader.readLine();
                if (line == null) return;
                if (logLevel == Level.INFO) {
                    LOGGER.info(line);
                    if (line.endsWith(startedSuccessfullyMessage)) {
                        LOGGER.info("CrashAssistantApp started successfully. Stopping child process loggers.");
                        anotherChildProcessLogger.interrupt();
                        return;
                    }
                }
                else {
                    LOGGER.error(line);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error while reading CrashAssistantApp process stream:", e);
        } catch (InterruptedException ignored) {
        }
    }


    public static void captureOutput(Process p) {
        crashAssistantAppProcess = p;
        ChildProcessLogger err = new ChildProcessLogger(p.getErrorStream(), Level.ERROR);
        ChildProcessLogger out = new ChildProcessLogger(p.getInputStream(), Level.INFO);
        err.anotherChildProcessLogger = out;
        out.anotherChildProcessLogger = err;
        err.start();
        out.start();
        new Timer().schedule( // If for some reason app Process not started successfully and not stopped with err, we should stop loggers to not waste game with our threads. Should never happen.
                new TimerTask() {
                    @Override
                    public void run() {
                        err.interrupt();
                        out.interrupt();
                    }
                },
                3000
        );
    }
}
