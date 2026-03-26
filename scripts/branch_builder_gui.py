import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import subprocess
import threading
import os
import sys
import re
from pathlib import Path


IGNORED_BRANCHES = ["pages", "app-common-config"]
GRADLEW_CMD = "gradlew.bat" if sys.platform == "win32" else "./gradlew"

def natural_sort_key(s):
    return [int(text) if text.isdigit() else text.lower() for text in re.split(r'([0-9]+)', s)]

class BranchBuildApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Branch Multi-Build Tool")
        
        self.project_root = Path(__file__).resolve().parent.parent
        os.chdir(self.project_root)
        
        self.original_branch = self.get_current_branch()
        self.is_busy = False
        
        self.setup_ui()
        self.load_branches()

    def get_current_branch(self):
        try:
            res = subprocess.run(['git', 'rev-parse', '--abbrev-ref', 'HEAD'], 
                                capture_output=True, text=True, check=True, encoding='utf-8')
            return res.stdout.strip()
        except Exception:
            return "unknown"

    def setup_ui(self):
        main_frame = ttk.Frame(self.root, padding="15")
        main_frame.pack(fill=tk.BOTH, expand=True)

        header_frame = ttk.Frame(main_frame)
        header_frame.pack(fill=tk.X, pady=(0, 10))
        
        ttk.Label(header_frame, text="Current branch: ", font=("Arial", 11)).pack(side=tk.LEFT)
        ttk.Label(header_frame, text=self.original_branch, font=("Arial", 11, "bold")).pack(side=tk.LEFT)

        ttk.Button(header_frame, text="Open build/libs", command=self.open_build_libs).pack(side=tk.RIGHT)

        self.btn_container = ttk.LabelFrame(main_frame, text=" Select target branch for build ", padding=10)
        self.btn_container.pack(fill=tk.BOTH, expand=True, pady=5)
        
        log_frame = ttk.LabelFrame(main_frame, text=" Execution logs ", padding=5)
        log_frame.pack(fill=tk.BOTH, expand=True, pady=(10, 0))
        
        self.log_area = scrolledtext.ScrolledText(log_frame, height=12, state=tk.DISABLED, font=("Courier New", 9))
        self.log_area.pack(fill=tk.BOTH, expand=True)

    def log(self, text):
        self.root.after(0, self._log_to_widget, text)

    def _log_to_widget(self, text):
        self.log_area.config(state=tk.NORMAL)
        self.log_area.insert(tk.END, f"{text}\n")
        self.log_area.see(tk.END)
        self.log_area.config(state=tk.DISABLED)

    def load_branches(self):
        try:
            res = subprocess.run(['git', 'branch', '--all'], 
                                capture_output=True, text=True, check=True, encoding='utf-8')
            lines = res.stdout.splitlines()
        except Exception as e:
            self.log(f"Error fetching branches: {e}")
            return

        unique_branches = set()
        for line in lines:
            line = line.strip()
            if not line or '->' in line: continue
            
            clean_name = line.replace('* ', '').strip()
            if clean_name.startswith('remotes/'):
                parts = clean_name.split('/')
                if len(parts) > 2 and parts[1] == 'origin':
                    clean_name = '/'.join(parts[2:])
                else: continue
            unique_branches.add(clean_name)

        target_branches = sorted(list(unique_branches - set(IGNORED_BRANCHES)), key=natural_sort_key)

        if not target_branches:
            self.log("No suitable branches found to display.")
            return

        num_cols = 5
        if len(target_branches) > 30: num_cols = 6
        if len(target_branches) > 42: num_cols = 7
        
        for i, branch in enumerate(target_branches):
            btn = ttk.Button(self.btn_container, text=branch, 
                             command=lambda b=branch: self.on_branch_btn_click(b))
            btn.grid(row=i // num_cols, column=i % num_cols, padx=4, pady=4, sticky="ew")

        for c in range(num_cols):
            self.btn_container.columnconfigure(c, weight=1)

    def open_build_libs(self):
        libs_path = self.project_root / "build" / "libs"
        if not libs_path.exists():
            libs_path.mkdir(parents=True, exist_ok=True)
        
        if sys.platform == "win32":
            os.startfile(libs_path)
        elif sys.platform == "darwin":
            subprocess.run(["open", str(libs_path)])
        else:
            subprocess.run(["xdg-open", str(libs_path)])

    def on_branch_btn_click(self, target_branch):
        if self.is_busy: return
        
        msg = (f"Perform build on '{self.original_branch}',\n"
               f"then switch to '{target_branch}',\n"
               f"perform build there and return back?")
        
        if not messagebox.askyesno("Confirmation", msg):
            return
        
        self.is_busy = True
        self.toggle_ui_state(tk.DISABLED)
        threading.Thread(target=self.run_process, args=(target_branch,), daemon=True).start()

    def toggle_ui_state(self, state):
        for child in self.btn_container.winfo_children():
            child.configure(state=state)

    def has_uncommitted_changes(self):
        try:
            res = subprocess.run(['git', 'status', '--porcelain'],
                                capture_output=True, text=True, check=True, encoding='utf-8')
            return bool(res.stdout.strip())
        except Exception:
            return False

    def run_process(self, target_branch):
        stashed = False
        try:
            self.log(f"\n>>> STARTING PROCESS FOR: {target_branch}")
            
            self.log(f"[1/4] Building current branch: {self.original_branch}...")
            if not self.execute_cmd([GRADLEW_CMD, 'clean', 'build']):
                self.log("ERROR: Failed to build current branch.")
                return

            if self.has_uncommitted_changes():
                self.log("Found uncommitted changes. Stashing them...")
                if self.execute_cmd(['git', 'stash', 'push', '-m', 'BranchBuildApp: temporary stash']):
                    stashed = True
                else:
                    self.log("ERROR: Failed to stash changes.")
                    return

            self.log(f"[2/4] Switching to branch: {target_branch}...")
            if not self.execute_cmd(['git', 'checkout', target_branch]):
                self.log(f"ERROR: Failed to switch to {target_branch}.")
                return

            self.log(f"[3/4] Building target branch: {target_branch}...")
            build_success = self.execute_cmd([GRADLEW_CMD, 'clean', 'build'])
            if not build_success:
                self.log(f"WARNING: Build on {target_branch} failed.")

            self.log(f"[4/4] Returning to original branch: {self.original_branch}...")
            if not self.execute_cmd(['git', 'checkout', self.original_branch]):
                self.log(f"CRITICAL ERROR: Failed to return to {self.original_branch}!")
                messagebox.showerror("Error", f"Critical error: failed to return to {self.original_branch}!")
                return

            if stashed:
                self.log("Restoring uncommitted changes...")
                if not self.execute_cmd(['git', 'stash', 'pop']):
                    self.log("WARNING: Failed to pop stash. Your changes are still in 'git stash'.")

            self.log("Process completed.")
            if build_success:
                messagebox.showinfo("Success", f"Branches {self.original_branch} and {target_branch} built successfully.")
            else:
                messagebox.showwarning("Partial Success", f"Current branch built, but errors occurred on {target_branch}.")

        except Exception as e:
            self.log(f"Exception in process: {e}")
        finally:
            self.is_busy = False
            self.root.after(0, self.toggle_ui_state, tk.NORMAL)

    def execute_cmd(self, cmd):
        self.log(f"Executing: {' '.join(cmd)}")
        try:
            process = subprocess.Popen(
                cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, encoding='utf-8', errors='replace'
            )
            while True:
                line = process.stdout.readline()
                if not line: break
                self.log(f"  {line.strip()}")
            process.wait()
            return process.returncode == 0
        except Exception as e:
            self.log(f"Command execution error: {e}")
            return False

if __name__ == "__main__":
    root = tk.Tk()
    root.geometry("1100x750")
    app = BranchBuildApp(root)
    root.mainloop()
