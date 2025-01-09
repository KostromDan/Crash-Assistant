# 
<h1 align="center">Crash Assistant  <br>
	<a href="https://www.curseforge.com/minecraft/mc-mods/crash-assistant/files"><img src="https://cf.way2muchnoise.eu/versions/1154099(c70039).svg" alt="Supported Versions"></a>
	<a href="https://github.com/KostromDan/Crash-Assistant/blob/1.19.2%2B/LICENSE"><img src="https://img.shields.io/github/license/KostromDan/Crash-Assistant?style=flat&color=900c3f" alt="License"></a>
	<a href="https://www.curseforge.com/minecraft/mc-mods/crash-assistant"><img src="http://cf.way2muchnoise.eu/1154099.svg" alt="CF"></a>
    <a href="https://modrinth.com/mod/crash-assistant"><img src="https://img.shields.io/modrinth/dt/ix1qq8Ux?logo=modrinth&label=&suffix=%20&style=flat&color=242629&labelColor=5ca424&logoColor=1c1c1c" alt="Modrinth"></a>
    <br><br>
</h1>

Shows a GUI after Minecraft crashes, immediately showing all affected game/launcher logs, crash reports, or hs_err files. Provides a one-click solution to upload them, copy the link, and perform other actions for easier reporting, debugging, and troubleshooting.

![image](https://github.com/user-attachments/assets/7728afcb-953c-40e9-8186-7314f984a7ba)

## Contributing:
Use gradle `build` task of root project. Compiled jars can be found in: `build\libs`:
* `crash_assistant-fabric-<version>.jar)` fabric mod.
* `crash_assistant-forge-<version>.jar)` forge mod.

For localization go [lang](common_config/src/main/resources/lang)

## Project structure:
`\app` has code of gui app

`\fabric` has code of fabric mod
* `app` is inluded in jar in jar

`\forge` has code of forge mod

`\common` has code for fabric and forge mods shared code.

`\common_config` has code for `app`, `fabric`, `forge_coremod` shared code used for runtime config, lang, launching gui app.

`\forge_coremod` has code of forge coremod from which `forge` mod and `app` launched.

* `app` and `forge` are inluded in jar in jar

### How it works?
Coremod includes 2 services:
* [CrashAssistantTransformationService.java](neoforge_coremod/src/main/java/dev/kostromdan/mods/crash_assistant/core_mod/services/CrashAssistantTransformationService.java)
  * `app` should be launched as soon as possible after game start to be able to help players even with coremod/mixin/hs_err crashes. So we launch it from static block of ITransformationService, the first point, we can launch it from forge mod.
* [CrashAssistantDependencyLocator.java](neoforge_coremod/src/main/java/dev/kostromdan/mods/crash_assistant/core_mod/services/CrashAssistantDependencyLocator.java)
  * We want to have singlefile mod, not `forge_mod.jar` and `forge_coremod.jar`. Since forge doesn't load jar in jar mods from coremods, we should do it by ourselves.
