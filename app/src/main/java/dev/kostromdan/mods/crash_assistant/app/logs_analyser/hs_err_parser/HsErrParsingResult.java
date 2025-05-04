package dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser;

import java.util.Optional;

public class HsErrParsingResult {
    private String problematicFrame = null;
    private String problematicFrameFullString = null;
    private Integer physicalMemory = null;
    private Integer pageFileSize = null;

    /**
     * @return The problematic frame wrapped in Optional
     */
    public Optional<String> getProblematicFrame() {
        return Optional.ofNullable(problematicFrame);
    }

    /**
     * Sets the problematic frame
     *
     * @param problematicFrame The problematic frame
     */
    public void setProblematicFrame(String problematicFrame) {
        this.problematicFrame = problematicFrame;
    }

    /**
     * @return The problematic frame full string wrapped in Optional
     */
    public Optional<String> getProblematicFrameFullString() {
        return Optional.ofNullable(problematicFrameFullString);
    }

    /**
     * Sets the problematic frame full string
     *
     * @param problematicFrameFullString The problematic frame full string
     */
    public void setProblematicFrameFullString(String problematicFrameFullString) {
        this.problematicFrameFullString = problematicFrameFullString;
    }

    /**
     * Appends a fragment to the {@code problematicFrameFullString}.
     *
     * @param text the text to append; ignored if {@code null} or empty
     */
    public void appendProblematicFrameFullString(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (this.problematicFrameFullString == null) {
            this.problematicFrameFullString = text;
        } else {
            this.problematicFrameFullString += text;
        }
    }


    /**
     * @return The physical memory in MB wrapped in Optional
     */
    public Optional<Integer> getPhysicalMemory() {
        return Optional.ofNullable(physicalMemory);
    }

    /**
     * Sets the physical memory
     *
     * @param physicalMemory The physical memory in MB
     */
    public void setPhysicalMemory(Integer physicalMemory) {
        this.physicalMemory = physicalMemory;
    }

    /**
     * @return The page file size in MB wrapped in Optional
     */
    public Optional<Integer> getPageFileSize() {
        return Optional.ofNullable(pageFileSize);
    }

    /**
     * Sets the page file size
     *
     * @param pageFileSize The page file size in MB
     */
    public void setPageFileSize(Integer pageFileSize) {
        this.pageFileSize = pageFileSize;
    }

    /**
     * Returns {@code true} when the size of the page file is the same
     * as the amount of physical memory, which is an indication that the
     * page file is disabled.
     *
     * @return {@code true} if {@code physicalMemory} and {@code pageFileSize}
     * are both non-null and equal, otherwise {@code false}
     */
    public boolean isPageFileDisabled() {
        return physicalMemory != null
                && physicalMemory.equals(pageFileSize);
    }

}
