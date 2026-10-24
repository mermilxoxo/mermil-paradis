# Third-party dependencies and services

This repository contains the project's own Java code, artwork, metadata and tests.
The MIT license does not relicense third-party dependencies, service data or skins.

- **LabyMod 4 API/client**: used from an existing local installation. No client JAR
  or decompiled implementation is redistributed. Developer documentation:
  https://dev.labymod.net/pages/addon/
- **Gson**: used from the local LabyMod installation; not bundled. Upstream:
  https://github.com/google/gson
- **Mixin**: annotations/runtime integration from the local LabyMod installation;
  not bundled. Upstream: https://github.com/SpongePowered/Mixin
- **Minecraft**: required at runtime; no game binary or downloaded skin is committed.
- **Laby.net** and **RebelCore** provide remote data; results are not included in the
  repository. RebelCore API documentation: https://www.rebelcore.org/api
- **NameMC** and **Crafty** are external profile links, not bundled databases.

`tests/test-api` is a minimal test-only interface double written for these tests.
It is not a copy of the LabyMod runtime and must never be packaged in the addon JAR.
Tests use synthetic fixtures and a public example Minecraft profile, not user logs
or exported account data.
