# CI signing key

`powerclimb-ci.jks` is the public signing key used by the GitHub Actions
release workflow so every `powerclimb.apk` published from `main` can be
installed as an update over the previous release.

- Alias: `powerclimb`
- Store/key password: `powerclimbci`
- Validity: 30 years

This key is intentionally public and is **not** suitable for Google Play or any
store that requires a private upload key. For a private key, add these GitHub
Actions secrets and the workflow will use them instead:

- `RELEASE_KEYSTORE_BASE64` — base64 of the `.jks` file
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

The workflow only uses the committed key when the secrets are absent.

## Karoo app manifest

Each release publishes `manifest.json` alongside the signed `powerclimb.apk`.
The APK points Karoo to the latest manifest through application-level
`io.hammerhead.karooext.MANIFEST_URL` metadata. Companion users can share that
JSON URL instead of the raw APK URL.

Generate it from Gradle's **release** APK metadata (not a manually maintained
version). The APK URL is pinned to the same version's release:

```bash
python3 ci/generate_manifest.py \
  app/build/outputs/apk/release/output-metadata.json \
  Danybs/powerclimb manifest.json
```

The generator expects one APK output, matching the current unsplit release
build. Run its integration checks without an Android SDK:

```bash
python3 -m unittest discover -s ci -p 'test_*.py'
```

Publishing is not performed by these checks. Validate the real Companion
installation on Karoo after publishing: `Installing` must finish, PowerClimb
must appear in the installed extensions list with uninstall available, and both
data fields must remain available. The SDK metadata contract alone cannot
prove the behavior of Karoo's installation screen.
