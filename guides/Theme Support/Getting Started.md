# Theme Support: Getting Started

---

### Where to Find Themes

The best place to start is the official collection of community-contributed themes: *
**[FlatLaf IntelliJ Themes Repository](https://github.com/JFormDesigner/flatlaf/tree/main/flatlaf-intellij-themes)**

**How to use this resource:**

1. Browse the list and find a theme that looks appealing to you.
2. **Click on the theme name**—this will take you to the specific repository for that theme.
   Usually there are also demo images
3. Once there, follow the instructions below to locate and download the actual theme file.

#### Alternative: JetBrains marketplace

https://plugins.jetbrains.com/search?tags=Theme.

If you found a good one, click on the `Source Code` link at the bottom of the theme page, which redirects you to the
source code repository of the theme. There you can search for a `.theme.json` file and download it.

---

### How to Locate and Install Theme Files

#### 1. Finding the File

Once you are in a theme's repository or archive, you need to find the specific configuration file.

* **In a Repository:** Navigate to the `src/main/resources` or `resources` folder. You are looking for a file ending
  with the **`.theme.json`** extension.
    * *Example:*
      `flatlaf-intellij-themes/src/main/resources/com/formdev/flatlaf/intellijthemes/themes/DarkPurple.theme.json`
    * *Example:* `arc-theme-idea-light/resources/arc-theme-orange.theme.json`
    * *Example:* `Cobalt2-UI-Theme/resources/Cobalt_2.theme.json`
* **In an Archive (JAR):** If you download a theme as a `.jar` file, you can open it with any archive manager (like
  7-Zip). Look inside the internal folders for the `.theme.json` file.

For `FlatLaf IntelliJ Themes Repository` the are located
here: https://github.com/JFormDesigner/FlatLaf/tree/main/flatlaf-intellij-themes/src/main/resources/com/formdev/flatlaf/intellijthemes/themes

#### 2. Handling Variations

Many themes come in several versions (e.g., `Light`, `Dark`, `High Contrast`, `Soft`). If you find multiple
`.theme.json` files, we recommend trying them all to see which one fits your UI best.

#### 3. Modifying Colors (Customization)

If you find a theme you like but want to tweak it (e.g., make the background darker or change the highlight color), you
can!

* Open the `.theme.json` file with any text editor (Notepad++, VS Code, etc.).
* Modify the HEX codes of corresponding elements.
* Save the file and restart the application to see your changes.

I case of default color of some button (e.g. Upload All) doesn't fit theme,
you can modify it in our config at: `gui_customisation`

#### 4. Installation Steps

1. **Copy the file:** Place your `.theme.json` file into: `config/crash_assistant/`
2. **Edit the config:** Open `crash_assistant.toml` and find the `[gui_customisation]` section.
3. **Set the filename:** Enter the exact name of the file in the `theme_file_name` option.
   ```toml
	theme_file_name = "example.theme.json"
   ```

---

### Troubleshooting

* **Logs:** If the theme doesn't appear, check your logs. Crash Assistant will print a warning if the file is missing or
  formatted incorrectly.

---

### Examples

#### Core Themes

`FlatLightLaf`

<img width="635" height="526" alt="image" src="https://github.com/user-attachments/assets/7cb0d76e-ceb8-4a50-8c21-2f44f6a538a9" />

`FlatDarkLaf`

<img width="635" height="526" alt="image" src="https://github.com/user-attachments/assets/0013bf83-32b0-4a55-8cdb-811dfa4d3c60" />

`FlatIntelliJLaf`

<img width="644" height="566" alt="image" src="https://github.com/user-attachments/assets/3b2ed334-f84d-4090-bc85-982f0984cde7" />

`FlatDarculaLaf`

<img width="644" height="566" alt="image" src="https://github.com/user-attachments/assets/f8428653-79d2-4aa7-8519-4bc8565d058b" />

`FlatMacLightLaf`

<img width="646" height="566" alt="image" src="https://github.com/user-attachments/assets/7ee8261c-b5ee-419f-9141-fdf4e22ef5f9" />

`FlatMacDarkLaf`

<img width="646" height="566" alt="image" src="https://github.com/user-attachments/assets/0a30745c-cfcb-4fcb-849e-e81faadb023d" />

#### Some themes from `FlatLaf IntelliJ Themes Repository`

`Cobalt_2.theme.json` : https://github.com/JFormDesigner/FlatLaf/blob/main/flatlaf-intellij-themes/src/main/resources/com/formdev/flatlaf/intellijthemes/themes/Cobalt_2.theme.json

<img width="644" height="566" alt="image" src="https://github.com/user-attachments/assets/12f941f4-c5c3-4211-80db-0f40b433ecf1" />

`DarkPurple.theme.json` : https://github.com/JFormDesigner/FlatLaf/blob/main/flatlaf-intellij-themes/src/main/resources/com/formdev/flatlaf/intellijthemes/themes/DarkPurple.theme.json

<img width="644" height="566" alt="image" src="https://github.com/user-attachments/assets/34e7b8f7-b3ff-4e6a-aa89-6f34e55c537e" />

`arc-theme.theme.json` : https://github.com/JFormDesigner/FlatLaf/blob/main/flatlaf-intellij-themes/src/main/resources/com/formdev/flatlaf/intellijthemes/themes/arc-theme.theme.json

<img width="638" height="546" alt="image" src="https://github.com/user-attachments/assets/7828b928-46e0-4aba-b558-83b04b221382" />


`arc-theme-orange.theme.json` : https://github.com/JFormDesigner/FlatLaf/blob/main/flatlaf-intellij-themes/src/main/resources/com/formdev/flatlaf/intellijthemes/themes/arc-theme-orange.theme.json

<img width="638" height="546" alt="image" src="https://github.com/user-attachments/assets/97f9b496-7f25-498e-bceb-1b1d522e5126" />


`arc_theme_dark.theme.json` : https://github.com/JFormDesigner/FlatLaf/blob/main/flatlaf-intellij-themes/src/main/resources/com/formdev/flatlaf/intellijthemes/themes/arc_theme_dark.theme.json

<img width="638" height="546" alt="image" src="https://github.com/user-attachments/assets/5601ad08-d218-45ec-8b79-12ed816536b9" />


`arc_theme_dark_orange.theme.json` : https://github.com/JFormDesigner/FlatLaf/blob/main/flatlaf-intellij-themes/src/main/resources/com/formdev/flatlaf/intellijthemes/themes/arc_theme_dark_orange.theme.json

<img width="638" height="546" alt="image" src="https://github.com/user-attachments/assets/f92ab5b1-1135-40e4-b982-4dc8390d5c47" />

