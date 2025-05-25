package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

import java.util.List;

/**
 * Represents a problem found in a log file
 */
public class Problem {
    private final int line;
    private final String message;
    private final List<String> solutions;

    /**
     * Creates a new Problem
     * 
     * @param line The line number where the problem was found
     * @param problem The description of the problem
     * @param solutions The suggested solutions for the problem
     */
    public Problem(int line, String problem, List<String> solutions) {
        this.line = line;
        this.message = problem;
        this.solutions = solutions;
    }

    /**
     * Gets the line number where the problem was found
     * 
     * @return The line number
     */
    public int getLine() {
        return line;
    }

    /**
     * Gets the solutions for compatibility with the old API
     * 
     * @return An array of Solution objects
     */
    public List<String> getSolutions() {
        return solutions;
    }

    /**
     * Gets the message for compatibility with the old API
     * 
     * @return The problem description
     */
    public String getMessage() {
        return message;
    }
}