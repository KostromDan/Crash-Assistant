package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.utils.LogProcessor;

import java.io.File;
import java.nio.file.Path;

public class Log {
    private final String name;
    private final Path path;
    private final LogType type;
    private final LogProcessor processor;
    private String linkToUploadedFirstLines = null;
    private String linkToUploadedLastLines = null;
    private boolean isAnalysed = false;


    public Log(LogType type, String name, Path path) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.processor = new LogProcessor(path);
    }

    public Log(LogType type, Path path) {
        this(type, path.getFileName().toString(), path);
    }

    public String getName() {
        return name;
    }

    public Path getPath() {
        return path;
    }

    public File getFile() {
        return path.toFile();
    }

    public String getFileName() {
        return path.getFileName().toString();
    }

    public LogType getType() {
        return type;
    }

    public LogProcessor getProcessor() {
        return processor;
    }

    public boolean isLogUploaded() {
        return linkToUploadedFirstLines != null;
    }

    public String getLinkToUploadedFirstLines() {
        return linkToUploadedFirstLines;
    }

    public void setLinkToUploadedFirstLines(String linkToUploadedFirstLines) {
        this.linkToUploadedFirstLines = linkToUploadedFirstLines;
    }

    public String getLinkToUploadedLastLines() {
        return linkToUploadedLastLines;
    }

    public void setLinkToUploadedLastLines(String linkToUploadedLastLines) {
        this.linkToUploadedLastLines = linkToUploadedLastLines;
    }

    public boolean isAnalysed() {
        return isAnalysed;
    }

    public void setAnalysed(boolean analysed) {
        isAnalysed = analysed;
    }
}
