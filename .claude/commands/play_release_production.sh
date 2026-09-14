#!/bin/bash
# Promote an already-uploaded build to the Google Play PRODUCTION track, via the Play Console
# UI in Ego browser.
#
# Why the browser and not the API: the service account deliberately has no production release
# permission (least privilege), so `promoteReleaseArtifact --promote-track production` cannot
# work. Driving the Console with the admin account is the only path, and it keeps a human in
# the loop for the one action that reaches every user.
#
# VERIFICATION STATUS (be honest about this — see the TODO block in build_release.md):
#   verified   account guard, navigation, "建立新版本", the library picker, saving a draft,
#              discarding a draft
#   UNVERIFIED the release-notes step, "下一步", the preview page, and the final submit.
#              The first real run must be supervised.
set -e

usage() {
  cat <<'USAGE'
Usage: play_release_production.sh <versionName> <versionCode> [--handoff]

  <versionName>  e.g. 1.0.19   (digits and dots only)
  <versionCode>  e.g. 28       (digits only)
  --handoff      after preparing the release, hand the browser over so you can finish the
                 submit by hand. Without it the release is saved as a draft and the browser
                 is closed.

This script never submits to production on its own. Requires Ego browser signed in as the
Play Console admin account.
USAGE
}

VERSION_NAME=""
VERSION_CODE=""
HANDOFF="false"

while [ $# -gt 0 ]; do
  case "$1" in
    --handoff) HANDOFF="true" ;;
    --help|-h) usage; exit 0 ;;
    -*) echo "Error: unknown option '$1'"; echo; usage; exit 1 ;;
    *)
      if [ -z "$VERSION_NAME" ]; then VERSION_NAME="$1"
      elif [ -z "$VERSION_CODE" ]; then VERSION_CODE="$1"
      else echo "Error: too many arguments"; echo; usage; exit 1; fi
      ;;
  esac
  shift
done

[ -n "$VERSION_NAME" ] && [ -n "$VERSION_CODE" ] || { usage; exit 1; }

# Both values are interpolated into JavaScript string literals below. A double quote would end
# the literal and the remainder would execute as code — and these values come from
# app/build.gradle.kts, so repo content would otherwise become an eval sink.
case "$VERSION_NAME" in *[!0-9.]*) echo "Error: versionName must contain only digits and dots"; exit 1 ;; esac
case "$VERSION_CODE" in *[!0-9]*)  echo "Error: versionCode must contain only digits"; exit 1 ;; esac

EXPECTED_ACCOUNT="app@rootilabs.com"
SHOT_DIR="${TMPDIR:-/tmp}/play-production-release"
mkdir -p "$SHOT_DIR"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Refuse to go backwards. The current production version comes from the Publishing API, not
# from the Console page: that page renders "最新版本：1.0.176 個國家/地區" with no separator,
# so scraping cannot tell 1.0.17 from 1.0.176 — and this comparison is the only automated
# guard standing between a typo and a downgrade for every user.
CURRENT_PROD="$(python3 "$SCRIPT_DIR/play_tracks.py" production)" || exit 1
case "$CURRENT_PROD" in
  ''|*[!0-9.]*)
    echo "ABORT_UNPARSEABLE_PRODUCTION: got '$CURRENT_PROD' for the production track."
    echo "Refusing to prepare a release without knowing what it would replace."
    exit 1 ;;
esac
# Values go through argv, never string interpolation, so neither can inject into the snippet.
if ! python3 -c 'import sys
part = lambda v: [int(x) for x in v.split(".")]
sys.exit(0 if part(sys.argv[1]) > part(sys.argv[2]) else 1)' "$VERSION_NAME" "$CURRENT_PROD"; then
  echo "ABORT_NOT_NEWER: $VERSION_NAME is not newer than production's $CURRENT_PROD."
  echo "Promoting it would downgrade every user."
  exit 1
fi
echo "PRODUCTION NOW: $CURRENT_PROD"
echo "TARGET        : $VERSION_NAME (code $VERSION_CODE)"

ego-browser nodejs <<EOF
const DEV_ID = "7088258313312421414";
const APP_ID = "4974555030656048054";
const EXPECTED_ACCOUNT = "${EXPECTED_ACCOUNT}";
const VERSION_NAME = "${VERSION_NAME}";
const VERSION_CODE = "${VERSION_CODE}";
const HANDOFF = ${HANDOFF};
const SHOT_DIR = "${SHOT_DIR}";

const base = \`https://play.google.com/console/developers/\${DEV_ID}/app/\${APP_ID}\`;
const task = await taskSpace("play production release");
const page = task.page("p1");

const fail = (marker, ...lines) => {
  console.log(marker);
  lines.forEach(l => console.log("  " + l));
  throw new Error(marker);
};

// Without the finally an abort leaves the task space open AND can leave a half-prepared
// production release behind for someone to submit later.
let onPrepare = false;
try {
  // --- 1. Account guard --------------------------------------------------
  await page.goto("https://play.google.com/console/developers");
  await page.waitForLoadState("load");
  await page.waitForTimeout(9000);
  const probe = await page.evaluate(() => ({
    url: location.href,
    email: (document.body.innerText.match(/[\\w.+-]+@[\\w.-]+\\.\\w+/) || [])[0] || null,
  }));
  if (/accounts\\.google\\.com|\\/console\\/about/.test(probe.url))
    fail("NOT_SIGNED_IN", \`Sign in to Ego browser as \${EXPECTED_ACCOUNT}, then re-run.\`);
  if (probe.email !== EXPECTED_ACCOUNT)
    fail("ACCOUNT_MISMATCH", \`expected: \${EXPECTED_ACCOUNT}\`, \`actual: \${probe.email || "(unreadable)"}\`);
  console.log(\`ACCOUNT: \${probe.email} (OK)\`);

  // --- 2. Create a new production release --------------------------------
  // The downgrade guard already ran in the shell, against the API rather than this page.
  await page.goto(\`\${base}/tracks/production\`);
  await page.waitForLoadState("load");
  await page.waitForTimeout(9000);
  await page.evaluate(() => {
    const b = [...document.querySelectorAll("button,a")].find(e => /建立新版本/.test((e.innerText||"").trim()));
    if (b) b.click();
  });
  await page.waitForTimeout(12000);
  if (!/\\/prepare/.test(await page.url()))
    fail("FAILED_NO_EDITOR", "The release editor did not open.");
  onPrepare = true;

  // --- 4. Pick the exact bundle from the library -------------------------
  await page.evaluate(() => {
    const b = [...document.querySelectorAll("button")].find(e => /從檔案庫新增/.test((e.innerText||"").trim()));
    if (b) { b.scrollIntoView({ block: "center" }); b.click(); }
  });
  await page.waitForTimeout(7000);

  const picked = await page.evaluate((wanted) => {
    // Rows render as: 檔案類型 | 版本代碼 | 版本名稱 | API 等級 | 已上傳
    const rows = [...document.querySelectorAll('[role="row"],tr')];
    for (const r of rows) {
      const cells = [...r.querySelectorAll('[role="gridcell"],td')].map(c => (c.innerText||"").trim());
      if (!cells.includes(wanted)) continue;
      const cb = r.querySelector('input[type=checkbox],[role=checkbox]');
      if (!cb) continue;
      // Mark the row so the post-click check can be scoped to it: a page-wide
      // [aria-checked="true"] probe also matches select-alls and unrelated toggles.
      r.setAttribute("data-target-row", "1");
      const b = (cb.closest('[class*="checkbox"]') || cb).getBoundingClientRect();
      return { ok: true, cx: Math.round(b.x + b.width/2), cy: Math.round(b.y + b.height/2), cells };
    }
    return { ok: false, seen: rows.slice(0,12).map(r => (r.innerText||"").replace(/\\n/g," ").slice(0,60)) };
  }, VERSION_CODE);

  if (!picked.ok) {
    console.log(JSON.stringify(picked.seen, null, 2));
    fail("FAILED_BUNDLE_NOT_FOUND", \`versionCode \${VERSION_CODE} is not in the bundle library.\`);
  }

  // Material checkboxes ignore el.click(); send a real mouse event.
  await page.mouse.click(picked.cx, picked.cy, { label: \`select versionCode \${VERSION_CODE}\` });
  await page.waitForTimeout(2500);
  const rowChecked = await page.evaluate(() =>
    !!document.querySelector('[data-target-row] [aria-checked="true"], [data-target-row][aria-checked="true"]'));
  if (!rowChecked)
    fail("FAILED_SELECT", \`Could not tick the row for versionCode \${VERSION_CODE}.\`);
  console.log(\`BUNDLE \${VERSION_CODE} SELECTED: true\`);
  console.log(\`  row: \${picked.cells.join(" | ")}\`);

  await page.evaluate(() => {
    const b = [...document.querySelectorAll("button")].find(e => /^加入版本\$/.test((e.innerText||"").trim()));
    if (b) b.click();
  });
  await page.waitForTimeout(8000);
  // A missing button is a silent no-op, which would otherwise save a production draft with
  // no bundle in it and still report success.
  const added = await page.evaluate((code) =>
    document.body.innerText.includes(\`\${code} (\`), VERSION_CODE);
  if (!added)
    fail("FAILED_ADD", \`versionCode \${VERSION_CODE} did not appear in the release after 加入版本.\`);

  const shot = await page.screenshot({ path: \`\${SHOT_DIR}/prepared.png\` });
  console.log(\`SCREENSHOT: \${shot}\`);

  // --- 5. Stop here — this script never submits --------------------------
  // TODO(unverified): 版本名稱 / 版本資訊, then 下一步 -> 預覽並確認 -> submit.
  if (HANDOFF) {
    console.log("HANDOFF: the browser is yours for the final submit.");
    console.log("  下一步 -> 預覽並確認 -> 發布 are not automated yet; report what you see so");
    console.log("  this script can be finished.");
    onPrepare = false; // leave the draft in place for the human
    await task.handOff();
  } else {
    await page.evaluate(() => {
      const b = [...document.querySelectorAll("button")].find(e => /儲存為草稿/.test((e.innerText||"").trim()));
      if (b) b.click();
    });
    await page.waitForTimeout(8000);
    const stillEditing = /\\/prepare/.test(await page.url());
    if (stillEditing) {
      console.log("SAVE_UNCERTAIN: still on the editor page after 儲存為草稿.");
      console.log(\`  Check \${base}/tracks/production before assuming anything was saved.\`);
    } else {
      onPrepare = false;
      console.log("SAVED_AS_DRAFT");
      console.log(\`  Review and publish it yourself: \${base}/tracks/production\`);
    }
    console.log("Done.");
  }
} finally {
  if (onPrepare) {
    // Discard so an aborted run cannot leave a production draft someone later submits.
    try {
      await page.evaluate(() => {
        const b = [...document.querySelectorAll("button")].find(e => /捨棄草稿版本/.test((e.innerText||"").trim()));
        if (b) { b.scrollIntoView({ block: "center" }); b.click(); }
      });
      await page.waitForTimeout(4000);
      const c = await page.evaluate(() => {
        const d = [...document.querySelectorAll('[role="dialog"]')]
          .filter(x => x.getBoundingClientRect().height > 0)
          .find(x => /要捨棄草稿版本嗎/.test(x.innerText || ""));
        if (!d) return null;
        const b = [...d.querySelectorAll("button")].find(x => /^捨棄草稿版本\$/.test((x.innerText||"").trim()));
        if (!b) return null;
        const r = b.getBoundingClientRect();
        return { cx: Math.round(r.x + r.width/2), cy: Math.round(r.y + r.height/2) };
      });
      if (c) { await page.mouse.click(c.cx, c.cy, { label: "discard draft" }); await page.waitForTimeout(7000); }
      console.log("CLEANUP: discarded the half-prepared draft.");
    } catch (e) {
      // Never let cleanup mask the original failure.
      console.log(\`CLEANUP_FAILED: check \${base}/tracks/production for a stray draft.\`);
    }
  }
  if (!HANDOFF) await task.finish({ keep: [] });
}
EOF
