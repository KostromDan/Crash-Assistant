package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

import java.util.List;
import java.util.ArrayList;

/**
 * Represents a response from analyzing a log
 */
public class LogAnalysisResponse {
    private boolean success;
    private String error;
    private List<Problem> problems;

    /**
     * Creates a new successful LogAnalysisResponse
     * 
     * @param problems The problems found in the log
     */
    public LogAnalysisResponse(List<Problem> problems) {
        this.success = true;
        this.problems = problems;
    }

    /**
     * Creates a new failed LogAnalysisResponse
     * 
     * @param error The error message
     */
    public LogAnalysisResponse(String error) {
        this.success = false;
        this.error = error;
        this.problems = new ArrayList<>();
    }

    /**
     * Checks if the analysis was successful
     * 
     * @return true if the analysis was successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the error message if the analysis failed
     * 
     * @return The error message
     */
    public String getError() {
        return error;
    }

    /**
     * Gets the problems found in the log
     * 
     * @return The problems
     */
    public List<Problem> getProblems() {
        return problems;
    }

    /**
     * Gets the analysis for compatibility with the old API
     * 
     * @return This response
     */
    public LogAnalysisResponse get() {
        return this;
    }
}
