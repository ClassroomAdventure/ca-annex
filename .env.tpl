# 1Password: Vault "Dev" / item "env-ca-sidescreen"
# .env を作る: op inject -i .env.tpl -o .env
# リリース鍵（android/keystore/sidescreen-release.jks）は 1Password の Document "ca-sidescreen-release-keystore":
#   op document get ca-sidescreen-release-keystore --vault Dev --out-file android/keystore/sidescreen-release.jks
SIDESCREEN_KEYSTORE_PASSWORD=op://Dev/env-ca-sidescreen/SIDESCREEN_KEYSTORE_PASSWORD
