#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 vX.Y.Z" >&2
    exit 2
fi

tag="$1"
case "$tag" in
    v[0-9]*.[0-9]*.[0-9]*) ;;
    *) echo "Le tag doit respecter vX.Y.Z." >&2; exit 2 ;;
esac

: "${JAVA_HOME:?JAVA_HOME doit pointer vers JDK 17}"
: "${ANDROID_HOME:?ANDROID_HOME doit pointer vers le SDK Android}"
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"

if [ -z "${SUPSECU_KEYSTORE_PATH:-}" ] ||
    [ -z "${SUPSECU_KEYSTORE_PASSWORD:-}" ] ||
    [ -z "${SUPSECU_KEY_ALIAS:-}" ] ||
    [ -z "${SUPSECU_KEY_PASSWORD:-}" ]; then
    # Sur le Mac de publication, les secrets sont conservés dans le Trousseau.
    # shellcheck source=./load-release-signing.sh
    . "$root/scripts/load-release-signing.sh"
fi

: "${SUPSECU_KEYSTORE_PATH:?SUPSECU_KEYSTORE_PATH est requis}"
: "${SUPSECU_KEYSTORE_PASSWORD:?SUPSECU_KEYSTORE_PASSWORD est requis}"
: "${SUPSECU_KEY_ALIAS:?SUPSECU_KEY_ALIAS est requis}"
: "${SUPSECU_KEY_PASSWORD:?SUPSECU_KEY_PASSWORD est requis}"

version_name=$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' app/build.gradle.kts)
version_code=$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\)/\1/p' app/build.gradle.kts)
expected_tag="v$version_name"
if [ "$tag" != "$expected_tag" ]; then
    echo "Le tag $tag ne correspond pas à versionName $version_name." >&2
    exit 2
fi

./gradlew clean test lint assembleRelease

apk_source="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$apk_source" ]; then
    echo "APK signé introuvable : $apk_source" >&2
    exit 1
fi

mkdir -p dist
cp "$apk_source" dist/SupSecu.apk
sha256=$(shasum -a 256 dist/SupSecu.apk | awk '{print $1}')

jq -n \
    --argjson versionCode "$version_code" \
    --arg versionName "$version_name" \
    --arg sha256 "$sha256" \
    '{
        versionCode: $versionCode,
        versionName: $versionName,
        packageName: "be.supsecu.app",
        minimumAndroidSdk: 26,
        apkAssetName: "SupSecu.apk",
        sha256: $sha256
    }' > dist/supsecu-update.json

gh release create "$tag" \
    dist/SupSecu.apk \
    dist/supsecu-update.json \
    --title "SupSécu $version_name" \
    --generate-notes

echo "Version $tag publiée avec son manifeste SHA-256."
