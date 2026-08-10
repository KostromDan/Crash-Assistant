package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import java.io.Closeable;
import java.io.IOException;

interface LogLineReader extends Closeable {
    String readLine() throws IOException;
}
