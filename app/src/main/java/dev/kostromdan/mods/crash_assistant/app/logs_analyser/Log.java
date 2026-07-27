package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import org.apache.commons.jexl3.annotations.NoJexl;

import java.io.File;
import java.nio.file.Path;

public class Log {
    private final String name;
    private final Path path;
    private final LogType type;
    private final LogReader reader;
    private volatile String linkToUploadedFirstLines = null;
    private volatile String linkToUploadedLastLines = null;
    private boolean isAnalysed = false;

    @NoJexl
    public Log(LogType type, String name, Path path) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.reader = new LogReader(this);
    }

    @NoJexl
    public Log(LogType type, Path path) {
        this(type, path.getFileName().toString(), path);
    }

    public String getName() {
        return name;
    }

    @NoJexl
    public Path getPath() {
        return path;
    }

    @NoJexl
    public File getFile() {
        return path.toFile();
    }

    public String getFileName() {
        return path.getFileName().toString();
    }

    public LogType getType() {
        return type;
    }

    public LogReader getReader() {
        return reader;
    }

    public boolean isLogUploaded() {
        return linkToUploadedFirstLines != null;
    }

    public String getLinkToUploadedFirstLines() {
        return linkToUploadedFirstLines;
    }

    @NoJexl
    public void setLinkToUploadedFirstLines(String linkToUploadedFirstLines) {
        this.linkToUploadedFirstLines = linkToUploadedFirstLines;
    }

    public String getLinkToUploadedLastLines() {
        return linkToUploadedLastLines;
    }

    @NoJexl
    public void setLinkToUploadedLastLines(String linkToUploadedLastLines) {
        this.linkToUploadedLastLines = linkToUploadedLastLines;
    }

    public boolean isAnalysed() {
        return isAnalysed;
    }

    @NoJexl
    public void setAnalysed(boolean analysed) {
        isAnalysed = analysed;
    }

    public String getParentName() {
        String[] splitLog = getName().split(":");
        return splitLog.length == 2 ? splitLog[0] + ": " : "";
    }
}
