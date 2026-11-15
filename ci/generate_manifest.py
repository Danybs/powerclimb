"""Generate Karoo app metadata from the release APK output metadata."""

import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk_metadata", type=Path, help="Gradle release output-metadata.json")
    parser.add_argument("repository", help="GitHub owner/repository hosting the release")
    parser.add_argument("output", type=Path, help="Destination manifest.json")
    args = parser.parse_args()

    metadata = json.loads(args.apk_metadata.read_text())
    [apk] = metadata["elements"]
    version = apk["versionName"]
    manifest = {
        "label": "PowerClimb",
        "packageName": metadata["applicationId"],
        "latestApkUrl": (
            f"https://github.com/{args.repository}/releases/download/v{version}/powerclimb.apk"
        ),
        "latestVersion": version,
        "latestVersionCode": apk["versionCode"],
    }
    args.output.write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
