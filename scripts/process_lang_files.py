import json
import os

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

            with open(file_path, 'w', encoding='utf-8') as f:
                json.dump(data, f, sort_keys=True, indent=2, ensure_ascii=False)

            if filename != 'en_us.json':
                current_keys = set(data.keys())

                missing_keys = en_us_keys - current_keys

                if missing_keys:
                    print(f"\nKeys from 'en_us.json' that are missing in '{filename}':")
                    for key in sorted(list(missing_keys)):
                        print(f"  - {key}")

if __name__ == '__main__':
    lang_directory = 'common_config/src/main/resources/lang'

    if not os.path.isdir(lang_directory):
        print(f"The directory '{lang_directory}' does not exist.")
        print("Please update the 'lang_directory' variable with the correct path.")
    else:
        process_language_files(lang_directory)