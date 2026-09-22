#!/bin/bash
# Promote an already-uploaded build to the Google Play PRODUCTION track, via the Play Console
# UI in Ego browser.
#
# Why the browser and not the API: the service account deliberately has no production release
# permission (least privilege), so `promoteReleaseArtifact --promote-track production` cannot
# work. Driving the Console with the admin account is the only path, and it keeps a human in
# the loop for the one action that reaches every user.
#
# VERIFICATION STATUS (see the production section of build_release.md):
#   verified   account guard, navigation, "建立新版本", the library picker, 版本名稱 / 版本資訊,
#              "下一步" to 預覽並確認, reading its warnings, discarding a draft
#   by hand    推出比例 -> 儲存 -> 發布總覽「送審 1 項變更」-> 「將變更送審」 (walked through once;
#              deliberately left manual, see build_release.md)
set -e

usage() {
  cat <<'USAGE'
Usage: play_release_production.sh <versionName> <versionCode> [--handoff]

  <versionName>  e.g. 1.0.19   (digits and dots only)
  <versionCode>  e.g. 28       (digits only)
  --handoff      after reaching 預覽並確認, hand the browser over so you can set 推出比例,
                 press 儲存 and send it for review by hand. Without it this is a dry run: it
                 reports the preview's warnings, discards the release and closes the browser.

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
// Project convention on every track (the API shows the same text on production and testing).
const RELEASE_NOTES = "<zh-TW>\n修正錯誤\n</zh-TW>\n<en-US>\nMinor bug fix\n</en-US>";

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
let handedOff = false;
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

  // --- 3. Pick the exact bundle from the library -------------------------
  await page.evaluate(() => {
    const b = [...document.querySelectorAll("button")].find(e => /從檔案庫新增/.test((e.innerText||"").trim()));
    if (b) { b.scrollIntoView({ block: "center" }); b.click(); }
  });
  // The editor page has its own grid (pre-filled with the live bundle), so the lookup must be
  // scoped to the library dialog; and that dialog renders its rows late, so wait for them with
  // one read per poll instead of a fixed sleep. The button reads 從檔案庫新增 but the dialog
  // title reads 從程式庫新增 — that is the Console's own wording, not a typo to unify.
  let libraryReady = false;
  for (let i = 0; i < 10 && !libraryReady; i++) {
    await page.waitForTimeout(2000);
    libraryReady = await page.evaluate(() => {
      const d = [...document.querySelectorAll('[role="dialog"]')]
        .find(x => x.getBoundingClientRect().height > 0 && /從程式庫新增/.test(x.innerText || ""));
      if (!d || d.querySelectorAll('[role="row"] [role="gridcell"]').length === 0) return false;
      d.setAttribute("data-library-dialog", "1");
      return true;
    });
  }
  if (!libraryReady)
    fail("FAILED_NO_LIBRARY", "The bundle library dialog did not show any rows within 20s.");

  const picked = await page.evaluate(({ code, name }) => {
    // Rows render as: (checkbox) | 檔案類型 | 版本代碼 | 版本名稱 | API 等級 | 已上傳
    // Match code and name in their own columns: the shell's downgrade guard only checked the
    // name, so a mismatched pair (e.g. "1.0.20 22") must not pick an old bundle.
    const rows = [...document.querySelectorAll('[data-library-dialog] [role="row"]')];
    for (const r of rows) {
      const cells = [...r.querySelectorAll('[role="gridcell"]')].map(c => (c.innerText||"").trim());
      if (cells[2] !== code || cells[3] !== name) continue;
      const cb = r.querySelector('input[type=checkbox],[role=checkbox]');
      if (!cb) continue;
      // Mark the row so the post-click check can be scoped to it: a page-wide
      // [aria-checked="true"] probe also matches select-alls and unrelated toggles.
      r.setAttribute("data-target-row", "1");
      const b = (cb.closest('[class*="checkbox"]') || cb).getBoundingClientRect();
      return { ok: true, cx: Math.round(b.x + b.width/2), cy: Math.round(b.y + b.height/2), cells };
    }
    return { ok: false, seen: rows.slice(0,12).map(r => (r.innerText||"").replace(/\\n/g," ").slice(0,60)) };
  }, { code: VERSION_CODE, name: VERSION_NAME });

  if (!picked.ok) {
    console.log(JSON.stringify(picked.seen, null, 2));
    fail("FAILED_BUNDLE_NOT_FOUND", \`no library row has versionCode \${VERSION_CODE} with versionName \${VERSION_NAME}.\`);
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

  // --- 4. Release name and notes -----------------------------------------
  // Angular only picks up a value set through the native setter plus an input event.
  const filled = await page.evaluate(({ name, notes }) => {
    const vis = [...document.querySelectorAll("input,textarea")].filter(e => e.getBoundingClientRect().height > 0);
    const nameEl = vis.find(e => e.tagName === "INPUT" && /版本名稱/.test(e.getAttribute("aria-label") || ""));
    const notesEl = vis.find(e => e.tagName === "TEXTAREA");
    if (!nameEl || !notesEl) return { ok: false };
    for (const [el, v] of [[nameEl, name], [notesEl, notes]]) {
      const proto = el.tagName === "TEXTAREA" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      Object.getOwnPropertyDescriptor(proto, "value").set.call(el, v);
      ["input", "change", "blur"].forEach(t => el.dispatchEvent(new Event(t, { bubbles: true })));
    }
    return { ok: nameEl.value === name && notesEl.value === notes };
  }, { name: VERSION_NAME, notes: RELEASE_NOTES });
  if (!filled.ok)
    fail("FAILED_NOTES", "Could not fill 版本名稱 / 版本資訊.");
  await page.waitForTimeout(3000);
  console.log(\`RELEASE NAME: \${VERSION_NAME}\`);

  const shot = await page.screenshot({ path: \`\${SHOT_DIR}/prepared.png\` });
  console.log(\`SCREENSHOT: \${shot}\`);

  // --- 5. 下一步 -> 預覽並確認 -------------------------------------------
  await page.evaluate(() => {
    const b = [...document.querySelectorAll("button")].find(e => /^下一步\$/.test((e.innerText||"").trim()));
    if (b) b.click();
  });
  // The Console validates server-side after 下一步, which can outlast any fixed sleep. 推出比例
  // sits at the bottom of the preview, so once it and 儲存 are there the page has rendered.
  let onPreview = false;
  for (let i = 0; i < 15 && !onPreview; i++) {
    await page.waitForTimeout(2000);
    onPreview = await page.evaluate(() =>
      /推出比例/.test(document.body.innerText) &&
      [...document.querySelectorAll("button")].some(e => /^儲存\$/.test((e.innerText||"").trim())));
  }
  if (!onPreview)
    fail("FAILED_NO_PREVIEW", "下一步 did not reach the 預覽並確認 page within 30s.");
  // Expand the collapsed warning list so it can be reported verbatim.
  const expanded = await page.evaluate(() => {
    const b = [...document.querySelectorAll("button")].find(e => /顯示更多/.test((e.innerText||"").trim()));
    if (b) b.click();
    return !!b;
  });
  if (expanded) await page.waitForTimeout(2500);
  const preview = await page.evaluate(() => {
    const t = document.body.innerText;
    const a = t.indexOf("錯誤、警告和訊息"), z = t.indexOf("支援裝置異動摘要");
    // The field has no aria-label; find it from its visible "推出比例 *" label.
    const label = [...document.querySelectorAll("*")]
      .find(e => e.children.length === 0 && /^推出比例/.test((e.innerText || "").trim()));
    let pct = null;
    for (let n = label, depth = 0; n && depth < 6; n = n.parentElement, depth++) {
      pct = n.querySelector("input");
      if (pct) break;
    }
    return {
      issues: a >= 0 && z > a ? t.slice(a, z).trim() : "(none listed)",
      rollout: pct ? pct.value : null,
    };
  });
  console.log("PREVIEW ISSUES:");
  preview.issues.split("\\n").filter(Boolean).forEach(l => console.log("  " + l));
  console.log(\`ROLLOUT FIELD: \${preview.rollout === null ? "(not found)" : JSON.stringify(preview.rollout)}\`);
  const pshot = await page.screenshot({ path: \`\${SHOT_DIR}/preview.png\` });
  console.log(\`SCREENSHOT: \${pshot}\`);

  // --- 6. Stop here — this script never submits --------------------------
  // Pressing 儲存 here does not publish either: it parks the release in 發布總覽, where it
  // still has to be sent for review. Neither step is automated.
  if (HANDOFF) {
    console.log("HANDOFF: the browser is on 預覽並確認. Set 推出比例, press 儲存 -> 前往總覽頁面,");
    console.log("  then 送審 1 項變更 -> 將變更送審. Managed publishing is on: after approval it");
    console.log("  still needs 發布 1 項變更 on 發布總覽 before users get it.");
    onPrepare = false; // leave the release in place for the human
    handedOff = true;
    await task.handOff();
  } else {
    console.log("DRY_RUN: reached 預覽並確認; discarding. Re-run with --handoff to finish by hand.");
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
      // A missing button or dialog is a silent no-op, so check the editor actually closed.
      if (!c || /\\/prepare/.test(await page.url())) throw new Error("editor still open");
      console.log("CLEANUP: discarded the half-prepared draft.");
    } catch (e) {
      // Never let cleanup mask the original failure, but never report success either.
      console.log(\`CLEANUP_FAILED: check \${base}/tracks/production for a stray draft.\`);
      process.exitCode = 1;
    }
  }
  if (!handedOff) await task.finish({ keep: [] });
}
EOF
