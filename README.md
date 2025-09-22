# 
<h1 align="center">Crash Assistant  <br>
	<a href="https://www.curseforge.com/minecraft/mc-mods/crash-assistant/files"><img src="https://cf.way2muchnoise.eu/versions/1154099(c70039).svg" alt="Supported Versions"></a>
	<a href="https://www.curseforge.com/minecraft/mc-mods/crash-assistant"><img src="http://cf.way2muchnoise.eu/1154099.svg" alt="CF"></a>
    <a href="https://modrinth.com/mod/crash-assistant"><img src="https://img.shields.io/modrinth/dt/ix1qq8Ux?logo=modrinth&label=&suffix=%20&style=flat&color=242629&labelColor=5ca424&logoColor=1c1c1c" alt="Modrinth"></a>
    <br><br>
</h1>

Shows a GUI after Minecraft crashes, immediately showing all affected game/launcher logs, crash reports, or hs_err files. Provides a one-click solution to upload them, copy the link, and perform other actions for easier reporting, debugging, and troubleshooting.

![image](https://github.com/user-attachments/assets/390c5475-5cdc-4750-aeee-1639e8112bff)

## Contributing:
Use gradle `build` task of root project. Compiled jars can be found in: `build\libs`:
* `crash_assistant-fabric-<version>.jar)` fabric mod.
* `crash_assistant-forge-<version>.jar)` forge mod.

To debug the GUI in the development environment, run `main()` of [CrashAssistantApp](app/src/main/java/dev/kostromdan/mods/crash_assistant/app/CrashAssistantApp.java)

Don't try to use loom `runClient()` functions, as they are broken from the moment of mod creation, due to complicated structure of mod.

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
* [CrashAssistantTransformationService](forge_coremod%2Fsrc%2Fmain%2Fjava%2Fdev%2Fkostromdan%2Fmods%2Fcrash_assistant%2Fcore_mod%2Fservices%2FCrashAssistantTransformationService.java)
  * `app` should be launched as soon as possible after game start to be able to help players even with coremod/mixin/hs_err crashes. So we launch it from static block of ITransformationService, the first point, we can launch it from forge mod.
* [CrashAssistantDependencyLocator](forge_coremod%2Fsrc%2Fmain%2Fjava%2Fdev%2Fkostromdan%2Fmods%2Fcrash_assistant%2Fcore_mod%2Fservices%2FCrashAssistantDependencyLocator.java)
  * We want to have singlefile mod, not `forge_mod.jar` and `forge_coremod.jar`. Since forge doesn't load jar in jar mods from coremods, we should do it by ourselves.


## Partners
YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/),
[YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/),
and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).

[![YourKit](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com)
