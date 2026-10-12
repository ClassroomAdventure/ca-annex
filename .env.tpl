# 1Password: Vault "Dev" / item "env-ca-annex"
# .env を作る: op inject -i .env.tpl -o .env
# リリース鍵（android/keystore/annex-release.jks）は 1Password の Document "ca-annex-release-keystore":
#   op document get ca-annex-release-keystore --vault Dev --out-file android/keystore/annex-release.jks
ANNEX_KEYSTORE_PASSWORD=op://Dev/env-ca-annex/ANNEX_KEYSTORE_PASSWORD
