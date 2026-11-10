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
