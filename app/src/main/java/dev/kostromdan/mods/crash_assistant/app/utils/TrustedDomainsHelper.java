package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;

public class TrustedDomainsHelper {
    private static final HashSet<String> trustedDomains = new HashSet<String>(){{
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
        add("qq.com");
        add("minecraftforge.net");
        add("neoforged.net");
        add("fabricmc.net");
        add("quiltmc.org");
        add("github.com");
        add("gnomebot.dev");
        add("mclo.gs");
        add("kostromdan.dev");
        add("kdan.dev");
        add("download.fo");
        add("t.me");
        add("cryprojects.ru");

        // Add domains from LinksProvider
        addAll(getDomainsFromLinksProvider());
    }};

    private static HashSet<String> getDomainsFromLinksProvider() {
        HashSet<String> domains = new HashSet<>();
        for (LinksProvider provider : LinksProvider.values()) {
            try {
                String link = provider.getLink();
                if (link != null && !link.equals("PRIVACY_POLICY")) {
                    URI uri = new URI(link);
                    String domain = getDomainName(uri);
                    if (domain != null) {
                        domains.add(domain);
                    }
                }
            } catch (URISyntaxException e) {
                // Skip invalid URLs
            }
        }
        return domains;
    }


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
