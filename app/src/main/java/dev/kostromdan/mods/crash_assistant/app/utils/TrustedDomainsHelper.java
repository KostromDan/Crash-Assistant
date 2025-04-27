package dev.kostromdan.mods.crash_assistant.app.utils;

import java.net.URI;
import java.util.HashSet;

public class TrustedDomainsHelper {
    private static final HashSet<String> trustedDomains = new HashSet<>(){{
        add("discord.gg");
        add("discord.com");
        add("discordapp.com");
        add("discord.media");
        add("discordapp.net");
        add("discordcdn.com");
        add("discord.dev");
        add("discord.new");
        add("discord.gift");
        add("discordstatus.com");
        add("dis.gd");
        add("discord.co");
        add("minecraftforge.net");
        add("neoforged.net");
        add("fabricmc.net");
        add("quiltmc.org");
        add("github.com");
    }};


    public static String getDomainName(URI uri) {
        String domain = uri.getHost();
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    public static String getTopDomainName(URI uri) {
        String domain = getDomainName(uri);
        int indexOfLastDot = domain.lastIndexOf(".");
        int indexOfSecondFromLastDot = domain.lastIndexOf(".", indexOfLastDot-1);
        return domain.substring(indexOfSecondFromLastDot + 1);
    }

    public static boolean isTrustedTopDomain(URI uri) {
        return trustedDomains.contains(getTopDomainName(uri));
    }
}

