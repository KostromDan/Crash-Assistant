package dev.kostromdan.mods.crash_assistant.mod_list;

public class UpdatedPairDiff {
    private final String commonPrefix;
    private final String commonSuffix;
    private final String uniquePart1;
    private final String uniquePart2;

    public UpdatedPairDiff(String commonPrefix, String commonSuffix, String uniquePart1, String uniquePart2) {
        this.commonPrefix = commonPrefix;
        this.commonSuffix = commonSuffix;
        this.uniquePart1 = uniquePart1;
        this.uniquePart2 = uniquePart2;
    }

    public String getCommonPrefix() {
        return commonPrefix;
    }

    public String getCommonSuffix() {
        return commonSuffix;
    }

    public String getUniquePart1() {
        return uniquePart1;
    }

    public String getUniquePart2() {
        return uniquePart2;
    }

    /**
     * Computes the updated pair diff between two mods.
     * It finds:
     * 1. The common prefix of both jarName strings.
     * 2. The common suffix of both jarName strings.
     * 3. The differing middle part of the first jarName.
     * 4. The differing middle part of the second jarName.
     *
     * Additionally, if the last character of the common prefix is a digit and the
     * first character of the unique middle part is a digit, the boundary is shifted one
     * character to the left. Similarly, for the suffix, if the first character of the
     * common suffix is a digit and the last character of the unique part (from mod2) is a digit,
     * the boundary is shifted one character to the left.
     *
     * For example, for jarNames:
     * "fabric-api-0.118.55+1.21.4" and "fabric-api-0.119.45+1.21.4",
     * the unique parts will be "118.55" and "119.45" after adjustments.
     *
     * @param mod1 the first mod
     * @param mod2 the second mod
     * @return an UpdatedPairDiff containing the differences
     */
    public static UpdatedPairDiff fromMods(Mod mod1, Mod mod2) {
        String jarName1 = mod1.getJarName();
        String jarName2 = mod2.getJarName();

        // 1. Find the common prefix
        int prefixLength = 0;
        int minLength = Math.min(jarName1.length(), jarName2.length());
        while (prefixLength < minLength && jarName1.charAt(prefixLength) == jarName2.charAt(prefixLength)) {
            prefixLength++;
        }

        // 2. Find the common suffix
        int suffixLength = 0;
        while (suffixLength < (minLength - prefixLength) &&
                jarName1.charAt(jarName1.length() - 1 - suffixLength) ==
                        jarName2.charAt(jarName2.length() - 1 - suffixLength)) {
            suffixLength++;
        }

        // Adjust the prefix boundary:
        // If the last character of the common prefix is a digit and the first character
        // of the unique part (in jarName1) is a digit, shift the boundary one character to the left.
        while (prefixLength > 0 && prefixLength < jarName1.length() &&
                Character.isDigit(jarName1.charAt(prefixLength - 1)) &&
                Character.isDigit(jarName1.charAt(prefixLength))) {
            prefixLength--;
        }

        // Adjust the suffix boundary:
        // If the first character of the common suffix is a digit and the last character
        // of the unique part (in jarName2) is a digit, shift the boundary one character to the left.
        while (suffixLength > 0 &&
                Character.isDigit(jarName1.charAt(jarName1.length() - suffixLength)) &&
                Character.isDigit(jarName2.charAt(jarName2.length() - suffixLength - 1))) {
            suffixLength--;
        }

        String commonPrefix = jarName1.substring(0, prefixLength);
        String commonSuffix = jarName1.substring(jarName1.length() - suffixLength);
        String uniquePart1 = jarName1.substring(prefixLength, jarName1.length() - suffixLength);
        String uniquePart2 = jarName2.substring(prefixLength, jarName2.length() - suffixLength);

        return new UpdatedPairDiff(commonPrefix, commonSuffix, uniquePart1, uniquePart2);
    }

    // For testing purposes
    public static void main(String[] args) {
        Mod mod1 = new Mod("fabric-api-0.118.55+1.21.4", "mod1", "1.0");
        Mod mod2 = new Mod("fabric-api-0.119.45+1.21.4", "mod2", "1.0");

        UpdatedPairDiff diff = UpdatedPairDiff.fromMods(mod1, mod2);
        System.out.println("Common prefix: " + diff.getCommonPrefix());
        System.out.println("Common suffix: " + diff.getCommonSuffix());
        System.out.println("Unique part of mod1: " + diff.getUniquePart1());
        System.out.println("Unique part of mod2: " + diff.getUniquePart2());
    }
}
