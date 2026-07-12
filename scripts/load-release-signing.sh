#!/bin/sh

# Charge la clé de signature SupSécu sans écrire son mot de passe dans le dépôt.
# Ce fichier doit être sourcé par le script de publication.

keychain_service="be.supsecu.app.release-keystore-v2"
keychain_account="${USER:-$(id -un)}"

if ! command -v security >/dev/null 2>&1; then
    echo "Le Trousseau macOS est requis pour charger la signature SupSécu." >&2
    return 1 2>/dev/null || exit 1
fi

release_password=$(security find-generic-password \
    -a "$keychain_account" \
    -s "$keychain_service" \
    -w) || {
    echo "Le mot de passe de signature SupSécu est absent du Trousseau macOS." >&2
    return 1 2>/dev/null || exit 1
}

export SUPSECU_KEYSTORE_PATH="$HOME/.config/supsecu/supsecu-release-v2.jks"
export SUPSECU_KEYSTORE_PASSWORD="$release_password"
export SUPSECU_KEY_ALIAS="supsecu-release-v2"
export SUPSECU_KEY_PASSWORD="$release_password"
unset release_password
