#!/usr/bin/env bash
# Uploads an Android App Bundle to a Google Play track with the Play Developer API (edits), using only
# curl, openssl and jq: no third-party action or SDK.
#
# Environment:
#   PLAY_SERVICE_ACCOUNT_JSON  key of a Google Cloud service account invited in Play Console
#   PACKAGE_NAME               e.g. io.github.fershibli.miligames
#   AAB                        path of the .aab to upload
#   TRACK                      internal | alpha | beta | production | <custom closed track>
#   STATUS                     draft | completed (Google only accepts draft before the first review)
#   VERSION                    shown as the release name, e.g. 1.2.0
#
# The very first bundle of a new app must be uploaded by hand in Play Console; this script handles
# every version after that.
set -euo pipefail

: "${PLAY_SERVICE_ACCOUNT_JSON:?}" "${PACKAGE_NAME:?}" "${AAB:?}" "${TRACK:?}" "${STATUS:?}" "${VERSION:?}"
[ -f "$AAB" ] || { echo "::error::$AAB not found"; exit 1; }

# Overridable only so the script can be tested against a local fake server.
root=${PLAY_API_ROOT:-https://androidpublisher.googleapis.com}
token_url=${PLAY_TOKEN_URL:-https://oauth2.googleapis.com/token}
api="$root/androidpublisher/v3/applications/$PACKAGE_NAME"
upload="$root/upload/androidpublisher/v3/applications/$PACKAGE_NAME"
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# 1. OAuth access token from a JWT signed with the service account key.
key_file=$(mktemp)
trap 'rm -f "$key_file"' EXIT
jq -r .private_key <<<"$PLAY_SERVICE_ACCOUNT_JSON" > "$key_file"
email=$(jq -r .client_email <<<"$PLAY_SERVICE_ACCOUNT_JSON")
now=$(date +%s)
header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
claims=$(jq -cn --arg iss "$email" --argjson iat "$now" \
  '{iss: $iss, scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token", iat: $iat, exp: ($iat + 3600)}' | b64url)
signature=$(printf '%s.%s' "$header" "$claims" | openssl dgst -sha256 -sign "$key_file" | b64url)
# Prints Google's error message (instead of a bare HTTP status) when a request fails.
request() {
  local out
  if ! out=$(curl -sS --fail-with-body "$@"); then
    echo "::error::Google Play API request failed: $out" >&2
    return 1
  fi
  printf '%s' "$out"
}
token=$(request "$token_url" \
  --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
  --data-urlencode "assertion=$header.$claims.$signature" | jq -r .access_token)
echo "::add-mask::$token"
call() { request -H "Authorization: Bearer $token" "$@"; }

# 2. Open an edit, upload the bundle, put it on the track and commit.
edit=$(call -X POST "$api/edits" | jq -r .id)
version_code=$(call -X POST -H "Content-Type: application/octet-stream" --data-binary "@$AAB" \
  "$upload/edits/$edit/bundles?uploadType=media" | jq -r .versionCode)
echo "Uploaded version code $version_code"
release=$(jq -cn --arg track "$TRACK" --arg name "$VERSION" --arg code "$version_code" --arg status "$STATUS" \
  '{track: $track, releases: [{name: $name, versionCodes: [$code], status: $status}]}')
call -X PUT -H "Content-Type: application/json" --data "$release" "$api/edits/$edit/tracks/$TRACK" > /dev/null
call -X POST "$api/edits/$edit:commit" > /dev/null
echo "Google Play: $PACKAGE_NAME $VERSION ($version_code) is on the '$TRACK' track as $STATUS."
