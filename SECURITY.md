# Security and private data

Never post account files, access/refresh tokens, authorization headers, session
data, skin-backup configuration, private email addresses or full game logs in issues.

For an ordinary bug, provide the addon/Minecraft/LabyMod versions, reproduction
steps and only the relevant **redacted** exception lines. Remove local usernames,
absolute personal paths, player identifiers and private chat/server details.

For a vulnerability, use GitHub's private vulnerability reporting feature if it
is available. Do not publish an exploitable secret in a public issue or pull request.
If a secret has already been exposed, revoke or rotate it at the issuing service;
deleting the visible text alone does not revoke a credential.

The source can be inspected freely. Authentication remains inside LabyMod.
The addon does not read or store access tokens in its own code, but local skin
backups contain account UUIDs and public texture URLs and should remain private.

Before publishing changes, run `python tools/check_public_files.py`. This is a
small local guard, not a guarantee that every possible kind of secret is detectable.
