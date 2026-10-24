# Contributing

1. Keep changes focused and explain the resulting behavior.
2. Use Java 21 and a local LabyMod installation; do not commit client libraries.
3. Build with `build.ps1`, then run `test.ps1`.
4. Run `python tools/check_public_files.py` after staging the intended files.
5. For UI or Minecraft-hook changes, also test in a running game and distinguish
   that result from isolated tests. Do not test by changing someone else's account.

Only publish source, owned artwork, test fixtures and documentation. Never add
local game folders, full logs, account/configuration exports, credentials, compiled
artifacts or third-party libraries. `.gitignore` is a first layer; inspect the
actual staged diff too.

Use a GitHub no-reply commit email if you want your private email kept out of
public commit metadata. This repository cannot control a contributor's Git settings.

By contributing code, you agree that your contribution may be distributed under
the project's MIT license. Do not contribute code or assets you cannot license.
