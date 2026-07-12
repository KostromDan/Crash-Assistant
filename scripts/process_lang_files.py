import json
import os
import re
from pathlib import Path

def extract_placeholders(text):
    """
    Extracts all placeholders in the format $placeholder$ from the given text.

    Args:
        text (str): The text to extract placeholders from.

    Returns:
        set: A set of placeholder names found in the text.
    """
    if not isinstance(text, str):
        return set()

    # Find all occurrences of $...$ pattern
    placeholders = re.findall(r'\$([^$]+)\$', text)
    return set(placeholders)

def compare_placeholders(en_text, translated_text, key, filename):
    """
    Compares placeholders between English and translated text.

    Args:
        en_text (str): The English text.
        translated_text (str): The translated text.
        key (str): The key being compared.
        filename (str): The name of the file being processed.

    Returns:
        list: A list of warning messages if there are placeholder mismatches.
    """
    warnings = []

    en_placeholders = extract_placeholders(en_text)
    translated_placeholders = extract_placeholders(translated_text)

    # Check for missing placeholders in translation
    missing_in_translation = en_placeholders - translated_placeholders
    if missing_in_translation:
        warnings.append(
            f"  - Key '{key}': Missing placeholders in translation: {', '.join(sorted(missing_in_translation))}"
        )

    # Check for extra placeholders in translation
    extra_in_translation = translated_placeholders - en_placeholders
    if extra_in_translation:
        warnings.append(
            f"  - Key '{key}': Extra placeholders not in English: {', '.join(sorted(extra_in_translation))}"
        )

    return warnings

def process_language_files(directory_path):
    """
    Sorts all JSON files in a directory and finds keys from 'en_us.json'
    that are missing in other language files.

    Args:
        directory_path (str): The path to the directory containing the language files.
    """
    en_us_path = os.path.join(directory_path, 'en_us.json')

    if not os.path.exists(en_us_path):
        print(f"Error: The reference file 'en_us.json' was not found in '{directory_path}'")
        return

    try:
        with open(en_us_path, 'r', encoding='utf-8') as f:
            en_us_data = json.load(f)
        en_us_keys = set(en_us_data.keys())
    except json.JSONDecodeError as e:
        print(f"Error reading or parsing {en_us_path}: {e}")
        return

    for filename in os.listdir(directory_path):
        if filename.endswith('.json'):
            file_path = os.path.join(directory_path, filename)

            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)
            except (json.JSONDecodeError, IOError) as e:
                print(f"Error reading or parsing {file_path}: {e}")
                continue

            # Write sorted JSON with newline at the end
            with open(file_path, 'w', encoding='utf-8') as f:
                json.dump(data, f, sort_keys=True, indent=2, ensure_ascii=False)
                f.write('\n')  # Add empty line at the end

            if filename != 'en_us.json':
                current_keys = set(data.keys())

                missing_keys = en_us_keys - current_keys
                extra_keys = current_keys - en_us_keys

                # Check for missing keys
                if missing_keys:
                    print(f"\nKeys from 'en_us.json' that are missing in '{filename}':")
                    for key in sorted(list(missing_keys)):
                        print(f"  - {key}")

                # Check for extra keys
                if extra_keys:
                    print(f"\nExtra keys in '{filename}' that are not in 'en_us.json':")
                    for key in sorted(list(extra_keys)):
                        print(f"  - {key}")

                # Check for placeholder mismatches
                placeholder_warnings = []
                for key in current_keys & en_us_keys:  # Only check keys that exist in both
                    warnings = compare_placeholders(en_us_data[key], data[key], key, filename)
                    placeholder_warnings.extend(warnings)

                if placeholder_warnings:
                    print(f"\nPlaceholder mismatches in '{filename}':")
                    for warning in placeholder_warnings:
                        print(warning)

if __name__ == '__main__':
    project_root = Path(__file__).resolve().parent.parent
    lang_directory = project_root / 'common_config/src/main/resources/crash_assistant_localization'

    if not os.path.isdir(lang_directory):
        print(f"The directory '{lang_directory}' does not exist.")
        print("Please update the 'lang_directory' variable with the correct path.")
    else:
        process_language_files(lang_directory)
