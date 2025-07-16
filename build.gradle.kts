
plugins {
    id("com.gtnewhorizons.gtnhconvention")
    id("me.shedaniel.unified-publishing") version("0.1.+")
}

// Apply dependencies.gradle
apply(from = "dependencies.gradle")

unifiedPublishing {
    project {
        gameVersions = listOf("1.7.10")
        gameLoaders = listOf("forge")

        displayName = tasks.reobfJar.get().archiveFile.get().asFile.name
        version = project.property("modVersion").toString()
        changelog = file("changelog_legacy.md").readText()
        releaseType = project.property("releaseType").toString()

        mainPublication(tasks.reobfJar.get())

        val cfToken = System.getenv("CF_TOKEN")
        if (cfToken != null) {
            curseforge {
                token = cfToken
                id = "1154099"
            }
        }

        val mrToken = System.getenv("MODRINTH_TOKEN")
        if (mrToken != null) {
            modrinth {
                token = mrToken
                id = "ix1qq8Ux"
            }
        }
    }
}
