import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext, font
import subprocess
import threading
import json
import os
import sys
from pathlib import Path
import shutil
import glob
import shlex
import re

# ==============================================================================================================
# THIS SCRIPT WAS VIBE-CODED WITH THE AIM OF SIMPLIFYING MY LIFE, GIVEN THE HUGE NUMBER OF BRANCHES.
# HELPING ME DISTRIBUTE CHANGES BETWEEN BRANCHES AS WELL AS DOING PUBLISHING.
#
# YES, I'M NOT A BIG FAN OF VIBE-CODING, BUT CONSIDERING THAT I'M THE ONLY ONE USING IT,
# AND IT HAS SAVED A TON OF TIME, WHY NOT (I THINK THE COUNT IS IN THE HUNDREDS OF HOURS).
# ==============================================================================================================

os.chdir(Path(__file__).resolve().parent.parent)

# --- Constants ---
CONFIG_FILE = "git_porter_config.json"
DESKTOP_PATH = Path.home() / "Desktop"
JAR_OUTPUT_DIR = DESKTOP_PATH / "jar_releases"
GRADLEW_CMD = "gradlew.bat" if sys.platform == "win32" else "./gradlew"
IGNORED_BRANCHES = ["pages", "vulkan-addon-3.3.1"]
VERSION_COMMIT_REGEX = re.compile(r'^\d+\.\d+\.\d+(\.\d+)?$')
GRADLE_PROPERTIES_FILE = "gradle.properties"
CHANGELOG_FILENAME = "changelog.md"

def natural_sort_key(s):
    """
    Creates a key for "natural" sorting of strings containing numbers.
    Example: '1.7.10' will be sorted before '1.10.2'.
    """
    return [int(text) if text.isdigit() else text.lower() for text in re.split(r'([0-9]+)', s)]

class GitPortingApp:
    """
    Main application class for porting commits and building.
    """
    def __init__(self, root):
        self.root = root
        self.root.title("Git Porting Tool")
        self.root.geometry("1000x800")

        # Styling
        self.setup_styles()

        # GUI State Variables
        self.branch_vars = {}
        self.build_option = tk.StringVar(value="nothing")
        self.publish_option = tk.StringVar(value="nothing")
        self.push_before_checkout = tk.BooleanVar(value=False)
        self.original_branch = ""

        # Load configuration
        self.config = self.load_config()

        self.create_menu()
        self.create_widgets()

        # Initial data population
        self.populate_commits()
        self.populate_branches()

        # Apply saved configuration
        self.apply_config_to_gui()

    def create_menu(self):
        """Creates the menu bar with File menu."""
        menubar = tk.Menu(self.root)
        self.root.config(menu=menubar)

        file_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="File", menu=file_menu)
        file_menu.add_command(label="Force Apply Changes", command=self.force_apply_wrapper)

    def setup_styles(self):
        """Configures styles for ttk widgets."""
        style = ttk.Style()
        style.theme_use('clam')
        style.configure("TFrame", background="#f0f0f0")
        style.configure("TLabel", background="#f0f0f0", font=("Arial", 10))
        style.configure("TButton", padding=6, font=("Arial", 10, "bold"))
        style.configure("Sync.TButton", padding=6, font=("Arial", 10), foreground="#0056b3")
        style.configure("TRadiobutton", background="#f0f0f0", font=("Arial", 10))
        style.configure("TCheckbutton", background="#f0f0f0", font=("Arial", 10))
        style.configure("TLabelframe", background="#f0f0f0", borderwidth=1, relief="groove")
        style.configure("TLabelframe.Label", background="#f0f0f0", font=("Arial", 11, "bold"))
        style.configure("Refresh.TButton", padding=2, font=("Arial", 8))

    def create_widgets(self):
        """Creates and places all widgets in the main window."""
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)

        # --- Top Panel (Lists) ---
        top_frame = ttk.Frame(main_frame)
        top_frame.pack(fill=tk.BOTH, expand=True, pady=5)

        # Column 0 (Commits) gets weight=1 (takes all available space)
        top_frame.grid_columnconfigure(0, weight=1)
        # Column 1 (Branches) gets weight=0 (takes only necessary minimum)
        top_frame.grid_columnconfigure(1, weight=0)

        top_frame.grid_rowconfigure(0, weight=1)

        # Left Panel: Commits
        commits_frame = ttk.LabelFrame(top_frame, text="1. Select commits (optional)")
        commits_frame.grid(row=0, column=0, sticky="nsew", padx=(0, 5))
        commits_frame.grid_rowconfigure(1, weight=1)
        commits_frame.grid_columnconfigure(0, weight=1)

        refresh_commits_btn = ttk.Button(commits_frame, text="Refresh", style="Refresh.TButton", command=self.populate_commits)
        refresh_commits_btn.grid(row=0, column=0, sticky="ne", padx=5, pady=2)

        self.commit_list = tk.Listbox(commits_frame, selectmode=tk.EXTENDED, font=("Courier New", 10))
        self.commit_list.grid(row=1, column=0, sticky="nsew")

        commit_scrollbar_y = ttk.Scrollbar(commits_frame, orient=tk.VERTICAL, command=self.commit_list.yview)
        commit_scrollbar_y.grid(row=1, column=1, sticky="ns")
        self.commit_list.config(yscrollcommand=commit_scrollbar_y.set)

        commit_scrollbar_x = ttk.Scrollbar(commits_frame, orient=tk.HORIZONTAL, command=self.commit_list.xview)
        commit_scrollbar_x.grid(row=2, column=0, sticky="ew")
        self.commit_list.config(xscrollcommand=commit_scrollbar_x.set)

        # Right Panel: Branches
        branches_frame = ttk.LabelFrame(top_frame, text="2. Select target branches")
        # sticky="ns" is important so height matches the left column, but width is minimal
        branches_frame.grid(row=0, column=1, sticky="nsew", padx=(5, 0))
        branches_frame.grid_columnconfigure(0, weight=1)
        branches_frame.grid_rowconfigure(1, weight=1)

        branch_header_frame = ttk.Frame(branches_frame)
        branch_header_frame.grid(row=0, column=0, columnspan=2, sticky="ew")

        self.select_all_var = tk.BooleanVar()
        select_all_check = ttk.Checkbutton(branch_header_frame, text="Select all", variable=self.select_all_var, command=self.toggle_all_branches)
        select_all_check.pack(side="left", padx=5, pady=5)

        refresh_branches_btn = ttk.Button(branch_header_frame, text="Refresh", style="Refresh.TButton", command=self.populate_branches)
        refresh_branches_btn.pack(side="right", padx=5, pady=2)

        self.branch_canvas = tk.Canvas(branches_frame, borderwidth=0, background="#ffffff")
        self.branch_list_frame = ttk.Frame(self.branch_canvas, style="TFrame")
        branch_scrollbar = ttk.Scrollbar(branches_frame, orient="vertical", command=self.branch_canvas.yview)
        self.branch_canvas.configure(yscrollcommand=branch_scrollbar.set)

        branch_scrollbar.grid(row=1, column=1, sticky="ns")
        self.branch_canvas.grid(row=1, column=0, sticky="nsew")
        self.branch_canvas_window = self.branch_canvas.create_window((0, 0), window=self.branch_list_frame, anchor="nw")

        self.branch_list_frame.bind("<Configure>", lambda e: self.branch_canvas.configure(scrollregion=self.branch_canvas.bbox("all")))
        self.branch_canvas.bind('<Configure>', self.on_canvas_configure)

        # Bind mouse scrolling to Canvas and internal Frame
        for widget in (self.branch_canvas, self.branch_list_frame):
            widget.bind("<MouseWheel>", self._on_mousewheel) # Windows/macOS
            widget.bind("<Button-4>", self._on_mousewheel)   # Linux (up)
            widget.bind("<Button-5>", self._on_mousewheel)   # Linux (down)


        # --- Middle Panel (Options) ---
        options_frame = ttk.Frame(main_frame)
        options_frame.pack(fill=tk.X, pady=5)
        options_frame.grid_columnconfigure(0, weight=1)
        options_frame.grid_columnconfigure(1, weight=1)

        # Build Options
        build_options_frame = ttk.LabelFrame(options_frame, text="3. Build options")
        build_options_frame.grid(row=0, column=0, sticky="ew", padx=(0, 5))
        ttk.Radiobutton(build_options_frame, text="Nothing", variable=self.build_option, value="nothing").pack(anchor="w", padx=10, pady=2)
        ttk.Radiobutton(build_options_frame, text="Build", variable=self.build_option, value="build").pack(anchor="w", padx=10, pady=2)
        ttk.Radiobutton(build_options_frame, text="Clean Build (copies .jar to desktop)", variable=self.build_option, value="clean_build").pack(anchor="w", padx=10, pady=2)

        # Publish Options
        publish_options_frame = ttk.LabelFrame(options_frame, text="4. Publish options")
        publish_options_frame.grid(row=0, column=1, sticky="ew", padx=(5, 0))
        ttk.Radiobutton(publish_options_frame, text="Nothing", variable=self.publish_option, value="nothing").pack(anchor="w", padx=10, pady=2)
        ttk.Radiobutton(publish_options_frame, text="publishUnified", variable=self.publish_option, value="publishUnified").pack(anchor="w", padx=10, pady=2)
        ttk.Radiobutton(publish_options_frame, text="publishUnifiedToLocal (copies .jar to desktop)", variable=self.publish_option, value="publishUnifiedToLocal").pack(anchor="w", padx=10, pady=2)

        # --- Start Button and Extra Options ---
        actions_frame = ttk.Frame(main_frame)
        actions_frame.pack(fill=tk.X, pady=10)

        self.start_button = ttk.Button(actions_frame, text="🚀 Start Processing", command=self.start_processing_wrapper)
        self.start_button.pack(side=tk.LEFT, fill=tk.X, expand=True)

        self.sync_changelog_button = ttk.Button(
            actions_frame,
            text="🔄 Sync Changelog to 'pages'",
            style="Sync.TButton",
            command=self.sync_changelog_wrapper
        )
        self.sync_changelog_button.pack(side=tk.LEFT, padx=5)

        self.vt_verify_button = ttk.Button(
            actions_frame,
            text="Run VT Verify",
            style="Sync.TButton",
            command=self.run_vt_verify_wrapper
        )
        self.vt_verify_button.pack(side=tk.LEFT, padx=5)

        push_checkbox = ttk.Checkbutton(actions_frame, text="Push before checkout", variable=self.push_before_checkout)
        push_checkbox.pack(side=tk.LEFT, padx=15)


        # --- Bottom Panel (Logs) ---
        log_frame = ttk.LabelFrame(main_frame, text="Execution Logs")
        log_frame.pack(fill=tk.BOTH, expand=True)
        log_frame.grid_rowconfigure(0, weight=1)
        log_frame.grid_columnconfigure(0, weight=1)

        self.log_area = scrolledtext.ScrolledText(log_frame, wrap=tk.WORD, state=tk.DISABLED, font=("Courier New", 9))
        self.log_area.grid(row=0, column=0, sticky="nsew")

    def _on_mousewheel(self, event):
        """Handles mouse wheel scrolling for the branch list Canvas."""
        if sys.platform.startswith('linux'):
            if event.num == 4:
                self.branch_canvas.yview_scroll(-1, "units")
            elif event.num == 5:
                self.branch_canvas.yview_scroll(1, "units")
        else:
            self.branch_canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")

    def on_canvas_configure(self, event):
        """Adjusts the width of the internal frame when the Canvas size changes."""
        canvas_width = event.width
        self.branch_canvas.itemconfig(self.branch_canvas_window, width=canvas_width)

    def log(self, message, level="INFO"):
        """Thread-safe method for adding messages to the log."""
        self.root.after(0, self._update_log_widget, f"[{level}] {message}\n")

    def _update_log_widget(self, message):
        """Updates the log widget (executed in the main thread)."""
        self.log_area.config(state=tk.NORMAL)
        self.log_area.insert(tk.END, message)
        self.log_area.see(tk.END)
        self.log_area.config(state=tk.DISABLED)

    def load_config(self):
        """Loads configuration from the JSON file."""
        try:
            with open(CONFIG_FILE, 'r') as f:
                return json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            return {"selected_branches": [], "build_option": "nothing", "publish_option": "nothing", "push_before_checkout": False}

    def save_config(self):
        """Saves current configuration to the JSON file."""
        selected_branches = [branch for branch, var in self.branch_vars.items() if var.get()]
        config_data = {
            "selected_branches": selected_branches,
            "build_option": self.build_option.get(),
            "publish_option": self.publish_option.get(),
            "push_before_checkout": self.push_before_checkout.get()
        }
        with open(CONFIG_FILE, 'w') as f:
            json.dump(config_data, f, indent=4)
        self.log("Configuration saved.")

    def apply_config_to_gui(self):
        """Applies loaded configuration to GUI elements."""
        self.build_option.set(self.config.get("build_option", "nothing"))
        self.publish_option.set(self.config.get("publish_option", "nothing"))
        self.push_before_checkout.set(self.config.get("push_before_checkout", False))

        selected_branches = self.config.get("selected_branches", [])
        for branch, var in self.branch_vars.items():
            if branch in selected_branches:
                var.set(True)

    def run_git_command(self, command, suppress_error_popup=False):
        """Executes a Git command and returns the result."""
        try:
            result = subprocess.run(command, capture_output=True, text=True, check=True, encoding='utf-8')
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            if not suppress_error_popup:
                messagebox.showerror("Git Error", f"Command failed with error:\n{' '.join(command)}\n\n{e.stderr}")
            return None
        except FileNotFoundError:
            if not suppress_error_popup:
                messagebox.showerror("Git Error", "Command 'git' not found. Ensure Git is installed and in PATH.")
            self.root.destroy()
            return None


    def populate_commits(self):
        """Populates the commit list for the current branch."""
        current_branch = self.get_current_branch()
        if not current_branch:
             self.log("Failed to determine current branch. Operation aborted.", "ERROR")
             return

        self.root.title(f"Git Porting Tool (Current Branch: {current_branch})")

        log_output = self.run_git_command(['git', 'log', '--pretty=format:%h - %s', '-200'])
        if log_output:
            self.commit_list.delete(0, tk.END)
            for line in log_output.split('\n'):
                self.commit_list.insert(tk.END, line)
        self.log(f"Commit list updated for branch '{current_branch}'.")

    def populate_branches(self):
        """Populates the branch list with checkboxes."""
        for widget in self.branch_list_frame.winfo_children():
            widget.destroy()
        self.branch_vars.clear()

        branches_output = self.run_git_command(['git', 'branch', '--all'])
        if branches_output:
            unique_branches = set()
            for line in branches_output.split('\n'):
                line = line.strip()
                if '->' in line:
                    continue

                clean_name = line.replace('* ', '').strip()
                if clean_name.startswith('remotes/'):
                    parts = clean_name.split('/')
                    if len(parts) > 2:
                        remote_name = parts[1]
                        if remote_name != 'origin':
                            continue
                        clean_name = '/'.join(parts[2:])
                    else:
                        continue
                unique_branches.add(clean_name)

            branches = sorted(list(unique_branches - set(IGNORED_BRANCHES)), key=natural_sort_key)

            # --- CALCULATE BRANCH COLUMN WIDTH ---
            # Create a font to measure text length
            chk_font = font.Font(family="Arial", size=10)
            max_text_width = 150 # Starting value (minimum)

            for branch_name in branches:
                var = tk.BooleanVar()
                cb = ttk.Checkbutton(self.branch_list_frame, text=branch_name, variable=var)
                cb.pack(anchor="w", padx=5, pady=2, fill='x')

                # Measure text width
                text_width = chk_font.measure(branch_name)
                if text_width > max_text_width:
                    max_text_width = text_width

                # Bind scrolling
                cb.bind("<MouseWheel>", self._on_mousewheel)
                cb.bind("<Button-4>", self._on_mousewheel)
                cb.bind("<Button-5>", self._on_mousewheel)
                self.branch_vars[branch_name] = var

            # Add padding for checkbox, scrollbar, and margins (approx 60-70 pixels)
            final_width = max_text_width + 70
            # Limit maximum width to prevent taking up the whole screen if name is too long
            if final_width > 600: final_width = 600

            # Apply width to Canvas
            self.branch_canvas.configure(width=final_width)

        self.log("Branch list updated.")

    def get_current_branch(self):
        """Returns the name of the currently active branch."""
        branch_output = self.run_git_command(['git', 'rev-parse', '--abbrev-ref', 'HEAD'])
        return branch_output

    def toggle_all_branches(self):
        """Selects or deselects all branches."""
        select_state = self.select_all_var.get()
        for var in self.branch_vars.values():
            var.set(select_state)

    def toggle_controls(self, enabled):
        """Enables or disables GUI controls during execution."""
        state = tk.NORMAL if enabled else tk.DISABLED
        self.start_button.config(state=state)
        self.sync_changelog_button.config(state=state)
        # ... (other controls)
        for child in self.root.winfo_children():
            # Recursively change state of all widgets except logs
            self._toggle_recursive(child, state)

    def _toggle_recursive(self, widget, state):
        """Recursively changes widget state."""
        if widget == self.log_area.master.master: # do not block logs
            return
        try:
            widget.config(state=state)
        except tk.TclError:
            pass # Some widgets do not have a state option
        for child in widget.winfo_children():
            self._toggle_recursive(child, state)

    def sync_changelog_wrapper(self):
        """Starts the changelog synchronization process in a separate thread."""
        global CHANGELOG_FILENAME

        self.original_branch = self.get_current_branch()
        if self.original_branch == "pages":
            messagebox.showwarning("Error", "You are already on the 'pages' branch.")
            return

        # Check for file existence
        changelog_path = Path(CHANGELOG_FILENAME)
        if not changelog_path.exists():
            # Try to find in different case
            alt_path = Path("CHANGELOG.md")
            if alt_path.exists():
                CHANGELOG_FILENAME = "CHANGELOG.md"
            else:
                messagebox.showerror("Error", f"File {CHANGELOG_FILENAME} not found in current branch.")
                return

        self.toggle_controls(False)
        self.log_area.config(state=tk.NORMAL)
        self.log_area.delete(1.0, tk.END)
        self.log_area.config(state=tk.DISABLED)

        self.log(f"Starting synchronization of {CHANGELOG_FILENAME} from {self.original_branch} to pages...")

        thread = threading.Thread(target=self.sync_changelog_logic, daemon=True)
        thread.start()

    def run_vt_verify_wrapper(self):
        script_path = Path("scripts") / "vt_verify.py"
        if not script_path.exists():
            messagebox.showerror("Missing script", f"Script not found:\n{script_path}")
            return

        if sys.platform == "win32":
            try:
                subprocess.Popen([
                    "cmd.exe", "/c", "start", "cmd.exe", "/k",
                    sys.executable, str(script_path)
                ])
                self.log("Launched vt_verify.py in a new console window.")
            except Exception as e:
                messagebox.showerror("Launch Error", f"Failed to launch vt_verify.py:\n{e}")
            return

        messagebox.showinfo("Unsupported platform", "This launcher is currently available only on Windows.")

    def sync_changelog_logic(self):
        """Logic for synchronizing the file between branches."""
        try:
            # 1. Read content
            with open(CHANGELOG_FILENAME, 'r', encoding='utf-8') as f:
                content = f.read()
            self.log(f"Content of {CHANGELOG_FILENAME} successfully read.")

            # 2. Switch to pages
            if not self.run_process_in_thread(['git', 'checkout', 'pages']):
                self.log("Failed to switch to branch 'pages'.", "ERROR")
                raise Exception("Git checkout failed")

            # 3. Overwrite file
            with open(CHANGELOG_FILENAME, 'w', encoding='utf-8') as f:
                f.write(content)
            self.log(f"File {CHANGELOG_FILENAME} overwritten in branch 'pages'.")

            # 4. Commit changes
            self.run_process_in_thread(['git', 'add', CHANGELOG_FILENAME])

            commit_msg = "Update changelog.md"

            self.run_process_in_thread(['git', 'commit', '-m', commit_msg])

            # 5. Push (optional, if checked)
            if self.push_before_checkout.get():
                self.log("Pushing for branch pages...")
                self.run_process_in_thread(['git', 'push', 'origin', 'pages'])

            self.log("Changelog synchronization successfully completed!", "SUCCESS")

        except Exception as e:
            self.log(f"Error during synchronization: {e}", "ERROR")
        finally:
            # Return
            self.log(f"Returning to branch {self.original_branch}...")
            if self.original_branch:
                 self.run_process_in_thread(['git', 'checkout', self.original_branch])
            self.root.after(0, self.toggle_controls, True)

    def start_processing_wrapper(self):
        """Prepares and starts the main process in a separate thread."""
        self.original_branch = self.get_current_branch()

        self.selected_branches = [branch for branch, var in self.branch_vars.items() if var.get()]
        if not self.selected_branches:
            messagebox.showwarning("No Selection", "Please select at least one target branch.")
            return

        selected_commit_indices = self.commit_list.curselection()
        if selected_commit_indices:
            selected_commits_text = [self.commit_list.get(i) for i in selected_commit_indices]
            self.selected_commit_hashes = [item.split(' - ')[0] for item in selected_commits_text]
            self.selected_commit_hashes.reverse()
        else:
            self.selected_commit_hashes = []

        # Cleanup old unified-local directories
        if self.publish_option.get() == "publishUnifiedToLocal":
            self.log("Cleaning old 'unified-local' directories...")
            try:
                unified_local_dirs = list(Path.cwd().glob('**/build/unified-local'))
                if not unified_local_dirs:
                    self.log("No old 'unified-local' directories found.")
                else:
                    for dir_path in unified_local_dirs:
                        self.log(f"Deleting: {dir_path.relative_to(Path.cwd())}")
                        shutil.rmtree(dir_path)
                    self.log("Cleanup completed.", "SUCCESS")
            except Exception as e:
                self.log(f"Error cleaning 'unified-local' directories: {e}", "ERROR")
                messagebox.showerror("Cleanup Error", f"Failed to delete old 'unified-local' directories.\n\n{e}\n\nProcess will be stopped.")
                return # Stop process if cleanup fails

        self.save_config()
        self.toggle_controls(False)
        self.log_area.config(state=tk.NORMAL)
        self.log_area.delete(1.0, tk.END)
        self.log_area.config(state=tk.DISABLED)

        if self.selected_commit_hashes:
            self.log(f"Starting process for {len(self.selected_branches)} branches with {len(self.selected_commit_hashes)} commits.")
        else:
            self.log(f"Starting process for {len(self.selected_branches)} branches (no cherry-pick).")

        process_thread = threading.Thread(target=self.processing_logic, daemon=True)
        process_thread.start()

    def run_process_in_thread(self, command, cwd=None):
        """Executes a command in subprocess, logging output in real-time."""
        self.log(f"Executing command: {' '.join(shlex.quote(c) for c in command)}")
        process = subprocess.Popen(
            command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding='utf-8', errors='replace', cwd=cwd
        )
        while True:
            line = process.stdout.readline()
            if not line: break
            self.log(line.strip(), "CMD")
        process.wait()
        return process.returncode == 0

    def get_commit_subject(self, commit_hash):
        """Retrieves the subject (first line) of a commit by its hash."""
        return self.run_git_command(['git', 'show', '-s', '--format=%s', commit_hash], suppress_error_popup=True)

    def is_version_commit(self, commit_hash):
        """Checks if the commit is a version commit."""
        subject = self.get_commit_subject(commit_hash)
        if subject and VERSION_COMMIT_REGEX.match(subject):
            return True
        return False

    def handle_version_commit(self, commit_hash):
            """Processes a version commit: updates gradle.properties and commits."""
            commit_subject = self.get_commit_subject(commit_hash)
            new_version = commit_subject
            self.log(f"Processing version commit: {commit_hash[:7]} -> {new_version}")

            try:
                with open(GRADLE_PROPERTIES_FILE, 'r', encoding='utf-8') as f:
                    lines = f.readlines()

                updated = False
                new_lines = []
                for line in lines:
                    stripped_line = line.strip()
                    if stripped_line.startswith("version") or stripped_line.startswith("mod_version"):
                        parts = re.split(r'(\s*=\s*)', line, maxsplit=1)
                        if len(parts) == 3:
                            key_part, separator, _ = parts
                            new_line = f"{key_part.rstrip()}{separator}{new_version}\n"
                            new_lines.append(new_line)
                            self.log(f"Found line to update: '{line.strip()}' -> '{new_line.strip()}'")
                            updated = True
                        else:
                            new_lines.append(line)
                    else:
                        new_lines.append(line)

                if not updated:
                    self.log(f"Line 'version' or 'mod_version' not found in {GRADLE_PROPERTIES_FILE}", "WARNING")
                    return False

                with open(GRADLE_PROPERTIES_FILE, 'w', encoding='utf-8') as f:
                    f.writelines(new_lines)

                # Commit changes
                if not self.run_process_in_thread(['git', 'add', GRADLE_PROPERTIES_FILE]):
                    self.log(f"Failed to add {GRADLE_PROPERTIES_FILE} to index.", "ERROR")
                    return False
                if not self.run_process_in_thread(['git', 'commit', '-m', commit_subject]):
                    self.log("Failed to commit version changes.", "ERROR")
                    # Attempt to revert index addition on failure
                    self.run_process_in_thread(['git', 'reset', 'HEAD', GRADLE_PROPERTIES_FILE])
                    return False

                self.log(f"Version successfully updated to '{new_version}' and committed.", "SUCCESS")
                return True

            except FileNotFoundError:
                self.log(f"File {GRADLE_PROPERTIES_FILE} not found.", "ERROR")
                return False
            except Exception as e:
                self.log(f"Error processing file {GRADLE_PROPERTIES_FILE}: {e}", "ERROR")
                return False

    def copy_jar_files(self, source_pattern, destination_dir):
        """Copies JAR files matching the pattern."""
        try:
            destination_dir.mkdir(parents=True, exist_ok=True)
            self.log(f"Searching for .jar files by pattern: {source_pattern}")

            found_files = list(Path.cwd().glob(source_pattern))
            if not found_files:
                self.log(f"No JAR files found.", "WARNING")
                return

            for jar_file in found_files:
                self.log(f"Copying file: {jar_file.relative_to(Path.cwd())} -> {destination_dir}")
                shutil.copy(jar_file, destination_dir)

            self.log(f"Successfully copied {len(found_files)} files.", "SUCCESS")
        except Exception as e:
            self.log(f"Error copying JAR files: {e}", "ERROR")

    def processing_logic(self):
            """Main logic executed in a separate thread."""
            all_success = True
            try:
                sorted_branches = sorted(self.selected_branches, key=natural_sort_key)

                for branch in sorted_branches:
                    is_original_branch = (branch == self.original_branch)

                    # --- Step 1: Switch to branch (if necessary) ---
                    if self.get_current_branch() != branch:
                        self.log(f"--- Switching to branch: {branch} ---")
                        if not self.run_process_in_thread(['git', 'checkout', branch]):
                            self.log(f"Failed to switch to branch {branch}. Process stopped.", "ERROR")
                            all_success = False; break
                    else:
                        self.log(f"--- Working on current branch: {branch} ---")

                    # --- Step 2: Apply commits (skipped for original branch) ---
                    if not is_original_branch:
                        if self.selected_commit_hashes:
                            version_commits = [h for h in self.selected_commit_hashes if self.is_version_commit(h)]
                            standard_commits = [h for h in self.selected_commit_hashes if not self.is_version_commit(h)]

                            if standard_commits:
                                self.log(f"Applying {len(standard_commits)} standard commits (cherry-pick)...")
                                if not self.run_process_in_thread(['git', 'cherry-pick'] + standard_commits):
                                    self.log(f"Cherry-pick error on branch {branch}. Aborting.", "ERROR")
                                    self.run_process_in_thread(['git', 'cherry-pick', '--abort'])
                                    all_success = False; break
                                self.log("Standard commits successfully applied.", "SUCCESS")

                            if version_commits and all_success:
                                self.log(f"Processing {len(version_commits)} version commits...")
                                for commit_hash in version_commits:
                                    if not self.handle_version_commit(commit_hash):
                                        all_success = False; break
                                if all_success:
                                    self.log("Version commits processed successfully.", "SUCCESS")

                            if not all_success: break # Abort if there were commit errors
                        else:
                            self.log("No commits selected, skipping application.")
                    else:
                        self.log("Skipping commit application for original branch.")

                    # --- Step 3: Build and Publish (executed for all) ---
                    build_opt = self.build_option.get()
                    publish_opt = self.publish_option.get()

                    tasks_to_run = []
                    if build_opt != "nothing" or publish_opt != "nothing": tasks_to_run.append('clean')
                    if build_opt == "build" or build_opt == "clean_build": tasks_to_run.append('build')
                    if publish_opt != "nothing": tasks_to_run.append(publish_opt)

                    if tasks_to_run:
                        gradle_command = [GRADLEW_CMD] + tasks_to_run
                        if not self.run_process_in_thread(gradle_command):
                            self.log(f"Gradle tasks failed on branch {branch}. Process stopped.", "ERROR")
                            all_success = False; break
                        else:
                            if build_opt == "clean_build": self.copy_jar_files('build/libs/*.jar', JAR_OUTPUT_DIR)
                            if publish_opt == "publishUnifiedToLocal": self.copy_jar_files('**/build/unified-local/project*/*.jar', JAR_OUTPUT_DIR)

                    # --- Step 4: Push ---
                    if self.push_before_checkout.get():
                         self.log(f"Pushing for branch {branch}...")
                         if not self.run_process_in_thread(['git', 'push', 'origin', branch]):
                             self.log(f"Failed to push for branch {branch}.", "WARNING")

                    self.root.after(0, self.branch_vars[branch].set, False)
                    self.log(f"--- Branch {branch} successfully processed ---", "SUCCESS")

            except Exception as e:
                self.log(f"An error occurred: {e}", "FATAL")
                all_success = False
            finally:
                self.log("Returning to original branch...")
                if self.get_current_branch() != self.original_branch:
                    if not self.run_process_in_thread(['git', 'checkout', self.original_branch]):
                         self.log(f"CRITICAL ERROR: Failed to return to original branch {self.original_branch}!", "ERROR")

                if all_success: self.log("All selected tasks completed successfully.", "SUCCESS")
                else: self.log("Process aborted due to errors.", "ERROR")

                self.root.after(0, self.toggle_controls, True)


    def force_apply_wrapper(self):
        """Starts the force apply process after confirmation."""
        self.original_branch = self.get_current_branch()

        self.selected_branches = [branch for branch, var in self.branch_vars.items() if var.get()]
        if not self.selected_branches:
            messagebox.showwarning("No Selection", "Please select at least one target branch.")
            return

        selected_commit_indices = self.commit_list.curselection()
        if not selected_commit_indices:
            messagebox.showwarning("No Commits", "Please select at least one commit for force apply.")
            return

        selected_commits_text = [self.commit_list.get(i) for i in selected_commit_indices]
        self.selected_commit_hashes = [item.split(' - ')[0] for item in selected_commits_text]
        self.selected_commit_hashes.reverse()

        if not messagebox.askyesno("Force Apply Confirmation",
            "Are you sure you want to force apply changes?\n\n"
            "This will overwrite/delete all files changed in the selected commits "
            "across all selected branches, regardless of their current content.",
            icon='warning'):
            return

        self.save_config()
        self.toggle_controls(False)
        self.log_area.config(state=tk.NORMAL)
        self.log_area.delete(1.0, tk.END)
        self.log_area.config(state=tk.DISABLED)

        self.log(f"Starting FORCE APPLY for {len(self.selected_branches)} branches with {len(self.selected_commit_hashes)} commits.")

        thread = threading.Thread(target=self.force_apply_logic, daemon=True)
        thread.start()

    def get_changed_files_from_commits(self, commit_hashes):
        """Returns a dict with file paths as keys and status ('M', 'A', 'D') as values
        for all files changed across the given commits."""
        changed_files = {}
        for commit_hash in commit_hashes:
            output = self.run_git_command(
                ['git', 'diff-tree', '--no-commit-id', '-r', '--name-status', commit_hash],
                suppress_error_popup=True
            )
            if output:
                for line in output.split('\n'):
                    line = line.strip()
                    if not line:
                        continue
                    parts = line.split('\t', 1)
                    if len(parts) == 2:
                        status, filepath = parts
                        # M=modified, A=added, D=deleted; last status wins
                        changed_files[filepath] = status[0]
        return changed_files

    def force_apply_logic(self):
        """Force apply logic: overwrite/delete files from selected commits across branches."""
        all_success = True
        try:
            # 1. Collect all changed files from selected commits (on original branch)
            changed_files = self.get_changed_files_from_commits(self.selected_commit_hashes)
            if not changed_files:
                self.log("No changed files found in selected commits.", "WARNING")
                return

            self.log(f"Found {len(changed_files)} changed file(s) in selected commits:")
            for filepath, status in changed_files.items():
                self.log(f"  [{status}] {filepath}")

            # 2. Read current content of modified/added files from original branch
            file_contents = {}
            for filepath, status in changed_files.items():
                if status in ('M', 'A'):
                    try:
                        content = self.run_git_command(
                            ['git', 'show', f'{self.original_branch}:{filepath}'],
                            suppress_error_popup=True
                        )
                        if content is not None:
                            file_contents[filepath] = content
                        else:
                            self.log(f"Warning: Could not read {filepath} from {self.original_branch}", "WARNING")
                    except Exception as e:
                        self.log(f"Error reading {filepath}: {e}", "ERROR")

            # 3. Iterate over target branches
            sorted_branches = sorted(self.selected_branches, key=natural_sort_key)
            for branch in sorted_branches:
                if branch == self.original_branch:
                    self.log(f"Skipping original branch: {branch}")
                    self.root.after(0, self.branch_vars[branch].set, False)
                    continue

                self.log(f"--- Force applying to branch: {branch} ---")

                # Checkout
                if self.get_current_branch() != branch:
                    if not self.run_process_in_thread(['git', 'checkout', branch]):
                        self.log(f"Failed to switch to branch {branch}. Process stopped.", "ERROR")
                        all_success = False
                        break

                files_changed = False
                for filepath, status in changed_files.items():
                    target_path = Path(filepath)
                    if status == 'D':
                        # Delete file if it exists
                        if target_path.exists():
                            target_path.unlink()
                            self.log(f"  Deleted: {filepath}")
                            files_changed = True
                        else:
                            self.log(f"  Already absent: {filepath}")
                    elif status in ('M', 'A'):
                        if filepath in file_contents:
                            # Ensure parent directory exists
                            target_path.parent.mkdir(parents=True, exist_ok=True)
                            # Use binary mode to read from git show and write exactly
                            raw_content = subprocess.run(
                                ['git', 'show', f'{self.original_branch}:{filepath}'],
                                capture_output=True
                            )
                            if raw_content.returncode == 0:
                                with open(target_path, 'wb') as f:
                                    f.write(raw_content.stdout)
                                self.log(f"  Overwritten: {filepath}")
                                files_changed = True
                            else:
                                self.log(f"  Failed to read {filepath} from {self.original_branch}", "WARNING")

                if files_changed:
                    # Stage and commit
                    self.run_process_in_thread(['git', 'add', '-A'])
                    commit_subjects = []
                    for h in self.selected_commit_hashes:
                        subj = self.get_commit_subject(h)
                        if subj:
                            commit_subjects.append(subj)
                    commit_msg = f"Force apply: {'; '.join(commit_subjects)}"
                    if not self.run_process_in_thread(['git', 'commit', '-m', commit_msg]):
                        self.log(f"Nothing to commit on branch {branch} (files may already be in sync).", "WARNING")
                else:
                    self.log(f"No file changes needed on branch {branch}.")

                # Push if option is set
                if self.push_before_checkout.get():
                    self.log(f"Pushing for branch {branch}...")
                    if not self.run_process_in_thread(['git', 'push', 'origin', branch]):
                        self.log(f"Failed to push for branch {branch}.", "WARNING")

                self.root.after(0, self.branch_vars[branch].set, False)
                self.log(f"--- Branch {branch} force apply completed ---", "SUCCESS")

        except Exception as e:
            self.log(f"An error occurred during force apply: {e}", "FATAL")
            all_success = False
        finally:
            self.log("Returning to original branch...")
            if self.get_current_branch() != self.original_branch:
                if not self.run_process_in_thread(['git', 'checkout', self.original_branch]):
                    self.log(f"CRITICAL ERROR: Failed to return to original branch {self.original_branch}!", "ERROR")

            if all_success:
                self.log("Force apply completed successfully.", "SUCCESS")
            else:
                self.log("Force apply aborted due to errors.", "ERROR")

            self.root.after(0, self.toggle_controls, True)


if __name__ == "__main__":
    try:
        root = tk.Tk()
        app = GitPortingApp(root)
        root.mainloop()
    except Exception as e:
        with open("git_porter_fatal.log", "a") as f:
            import traceback
            f.write(f"--- FATAL ERROR ---\n{traceback.format_exc()}\n")
        messagebox.showerror("Critical Error", f"A critical error occurred. Details in git_porter_fatal.log\n\n{e}")
