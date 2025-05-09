package dev.kostromdan.mods.crash_assistant.app.utils;

import javax.swing.*;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class ModRemover {

    public static void main(String[] args) {
        if (args.length < 2) {
            showErrorAndExit("Insufficient arguments. Usage: ModRemover <modPath> <parentPID>");
            return;
        }

        String modPath = args[0];
        long parentPID = 0;

        try {
            parentPID = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            showErrorAndExit("Invalid PID format: " + args[1]);
            return;
        }

        // Wait for parent process to exit
        boolean parentExited = waitForProcessToExit(parentPID);
        if (!parentExited) {
            showErrorAndExit("Parent process did not exit within the timeout period");
            return;
        }

        // Attempt to delete the mod file
        File modFile = new File(modPath);
        boolean success = false;
        String errorMessage = "";

        // Try to delete the file with retries
        for (int i = 0; i < 5; i++) {
            if (modFile.exists()) {
                try {
                    success = modFile.delete();
                    if (success) {
                        break;
                    } else {
                        // Small delay before retry
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (Exception e) {
                    errorMessage = e.getMessage();
                }
            } else {
                // File doesn't exist, consider as "missing"
                showMissingFileMessage(modPath);
                return;
            }
        }

        if (success) {
            showSuccessMessage(modPath);
        } else {
            showFailureMessage(modPath, errorMessage);
        }
    }

    private static boolean waitForProcessToExit(long pid) {
        ProcessHandle processHandle = ProcessHandle.of(pid).orElse(null);
        if (processHandle == null) {
            // Process already exited or not found
            return true;
        }

        // Check if the process is still alive
        for (int i = 0; i < 30; i++) { // Wait up to 15 seconds
            if (!processHandle.isAlive()) {
                return true;
            }

            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false; // Timeout, process is still running
    }

    private static void showSuccessMessage(String modPath) {
        SwingUtilities.invokeLater(() -> {
            setupLookAndFeel();
            String message = "Crash Assistant has been successfully removed from:\n" + modPath +
                    "\n\nPlease restart your game.";
            JDialog dialog = new JOptionPane(
                    message,
                    JOptionPane.INFORMATION_MESSAGE,
                    JOptionPane.DEFAULT_OPTION
            ).createDialog("Crash Assistant Removed");
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
            System.exit(0);
        });
    }

    private static void showFailureMessage(String modPath, String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            setupLookAndFeel();
            String message = "Failed to remove Crash Assistant from:\n" + modPath +
                    "\n\nPlease delete it manually from your mods folder." +
                    (errorMessage.isEmpty() ? "" : "\n\nError: " + errorMessage);
            JDialog dialog = new JOptionPane(
                    message,
                    JOptionPane.WARNING_MESSAGE,
                    JOptionPane.DEFAULT_OPTION
            ).createDialog("Removal Failed");
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
            System.exit(1);
        });
    }

    private static void showMissingFileMessage(String modPath) {
        SwingUtilities.invokeLater(() -> {
            setupLookAndFeel();
            String message = "Could not find Crash Assistant mod file at:\n" + modPath +
                    "\n\nIt may have been moved, renamed, or already removed.";
            JDialog dialog = new JOptionPane(
                    message,
                    JOptionPane.WARNING_MESSAGE,
                    JOptionPane.DEFAULT_OPTION
            ).createDialog("File Not Found");
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
            System.exit(1);
        });
    }

    private static void showErrorAndExit(String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            setupLookAndFeel();
            JDialog dialog = new JOptionPane(
                    "Error in Crash Assistant remover:\n" + errorMessage +
                            "\n\nPlease delete it manually from your mods folder.",
                    JOptionPane.ERROR_MESSAGE,
                    JOptionPane.DEFAULT_OPTION
            ).createDialog("Error");
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
            System.exit(1);
        });
    }

    private static void setupLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fallback to default look and feel
        }
    }
}
