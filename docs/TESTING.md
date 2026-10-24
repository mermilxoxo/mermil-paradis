# Testing and current limits

Target: LabyMod 4, Minecraft Java 1.21.11, Java 21. Version: 0.3.1.

## Reproduce isolated tests

After a successful `build.ps1` run:

```powershell
powershell -ExecutionPolicy Bypass -File .\test.ps1
```

The script uses the locally copied dependencies in `build/compile-deps`, compiles
tests separately and never adds the test API double to the production JAR.

Tests cover:

- `SkinDataTest`: Classic/Slim, texture URL validation and HTTPS normalization.
- `ServiceStatusTest`: HTTP diagnostics, PNG signatures and response limits.
- `HistoryDataTest`: documented RebelCore schema fixtures, UUID binding,
  nullable fields, date semantics and response/record bounds.
- `HistoryComparisonTest`: source union, repeated events, provenance and distinction
  between failed, pending, disabled and absent source records.
- `SkinBackupsTest`: pending/saved journal, successful and rejected changes,
  restart serialization, recovery and account separation.
- `ParadisLinksTest`: actual JAR service descriptor, Activity registration,
  required stylesheet links and absence of the faulty `loadMeta` override.
- `LiveSkinOverrideTest`: own player only, replacement instances, arm variant,
  preserving cape/elytra and clearing the override. Uses an isolated test double.

These tests passed again for the 0.3.1 UI update. All 31 production classes passed a JVM
verification/load check. A separate headless check confirmed that the installed
native `DefaultLinkMetaLoader` accepts the three Activity style links, using a
mocked API context. This is not a rendered UI test.

## Still requires a running game

- Opening and rendering the Mermil panel on different GUI scales/themes.
- Real Laby.net/RebelCore API responses and availability.
- Clipboard and browser actions.
- Configuration persistence through the complete LabyMod lifecycle.
- Real account-skin backup/restore, token expiry and account switching.
- Minecraft Mixin application and F5/inventory/hand rendering.

No real account skin was changed for the isolated tests. Network fixtures follow
the public API contract; they are not captured player-history responses.

## Known build-environment limitation

In the original restricted development environment, `javac` emitted all classes
without source errors but returned a nonzero exit while closing JAR file handles
(`AccessDeniedException`). A clean compiler-process exit was not established there.
Generated classes were packaged separately and verified. Both public scripts fail
on nonzero tool exits; they do not silently accept compiler failures.

This repository does not include a prebuilt JAR, the original machine's logs,
absolute paths, client libraries or runtime configuration.
