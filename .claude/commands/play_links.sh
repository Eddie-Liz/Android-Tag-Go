#!/bin/bash
# Post-upload check: confirm in Play Console which version each testing track is serving,
# and print the tester opt-in links.
#
# The links themselves are constants (the closed-testing one is literally derived from the
# package name), so the value here is the version confirmation, not the link lookup.
set -e

usage() {
  cat <<'USAGE'
Usage: play_links.sh [<versionName>]

  <versionName>  optional; when given, each track is checked for exactly this version
                 and VERSION_CHECK reports OK / MISMATCH. Digits and dots only.

Requires Ego browser to be signed in as the Play Console admin account.
USAGE
}

EXPECTED_VERSION=""
while [ $# -gt 0 ]; do
  case "$1" in
    --help|-h) usage; exit 0 ;;
    -*) echo "Error: unknown option '$1'"; echo; usage; exit 1 ;;
    *)
      [ -n "$EXPECTED_VERSION" ] && { echo "Error: too many arguments"; echo; usage; exit 1; }
      EXPECTED_VERSION="$1"
      ;;
  esac
  shift
done

# The value below is interpolated into a JavaScript string literal. A double quote would end
# that literal and the rest would execute as code, and versionName reaches this script straight
# out of app/build.gradle.kts — so repo content would become an eval sink. Restricting the
# shape closes that off entirely.
case "$EXPECTED_VERSION" in
  "") ;;
  *[!0-9.]*) echo "Error: versionName must contain only digits and dots (got '$EXPECTED_VERSION')"; exit 1 ;;
esac

EXPECTED_ACCOUNT="app@rootilabs.com"

ego-browser nodejs <<EOF
const DEV_ID = "7088258313312421414";
const APP_ID = "4974555030656048054";
const ROOTI_TRACK_UI_ID = "4698103554696570030";
const EXPECTED_ACCOUNT = "${EXPECTED_ACCOUNT}";
const EXPECTED_VERSION = "${EXPECTED_VERSION}";

const base = \`https://play.google.com/console/developers/\${DEV_ID}/app/\${APP_ID}\`;

const task = await taskSpace("play tester links");
const page = task.page("p1");

// Every step below can throw on a slow Console; without the finally the task space leaks and
// the next run of this script is blocked by a browser tab nobody owns.
try {
  // --- Account guard -----------------------------------------------------
  // A wrong account silently shows a different developer's data, so stop rather than report it.
  // The account picker is the only page that reliably renders the signed-in address; the app
  // track pages show just the app icon in the header.
  await page.goto("https://play.google.com/console/developers");
  await page.waitForLoadState("load");
  await page.waitForTimeout(9000);

  const probe = await page.evaluate(() => ({
    url: location.href,
    email: (document.body.innerText.match(/[\\w.+-]+@[\\w.-]+\\.\\w+/) || [])[0] || null,
  }));

  // A signed-out session redirects to the sign-in page or the /console/about marketing page,
  // which looks identical to "wrong account" unless the URL is checked.
  if (/accounts\\.google\\.com|\\/console\\/about/.test(probe.url)) {
    console.log("NOT_SIGNED_IN");
    console.log(\`  landed on: \${probe.url.slice(0, 90)}\`);
    console.log(\`  Sign in to Ego browser as \${EXPECTED_ACCOUNT}, then re-run this script.\`);
    throw new Error("not signed in to Play Console");
  }
  if (probe.email !== EXPECTED_ACCOUNT) {
    console.log("ACCOUNT_MISMATCH");
    console.log(\`  expected: \${EXPECTED_ACCOUNT}\`);
    console.log(\`  actual  : \${probe.email || "(could not read account)"}\`);
    console.log("Switch accounts in Ego browser, then re-run this script.");
    throw new Error("wrong Play Console account");
  }
  console.log(\`ACCOUNT: \${probe.email} (OK)\`);

  // --- Helper: open a track's testers tab and read version + opt-in link --
  async function readTrack(url, label) {
    await page.goto(url);
    await page.waitForLoadState("load");
    await page.waitForTimeout(9000);

    const info = await page.evaluate((wanted) => {
      const t = document.body.innerText.replace(/\\n{2,}/g, "\\n");
      const block = t.slice(Math.max(0, t.indexOf("測試群組摘要")));
      // Anchor on the two wordings the Console actually uses. Taking the first "d.d" in the
      // block instead lets a download size or a dotted date win over the version number.
      const m = block.match(/最新版本[：:]\\s*(\\d+(?:\\.\\d+)+)/)
             || block.match(/(\\d+(?:\\.\\d+)+)\\s*版正處於/);
      let state = "unknown";
      if (/審核|審查/.test(block.slice(0, 400))) state = "in review";
      else if (/提供給內部測試人員|可供所選測試人員使用|有效/.test(block.slice(0, 200))) state = "live";
      // Authoritative when a version was supplied: look for that exact literal, bounded so
      // 1.0.19 does not match inside 1.0.190.
      let serves = null;
      if (wanted) {
        const esc = wanted.replace(/\\./g, "\\\\.");
        serves = new RegExp(\`(?<![\\\\d.])\${esc}(?![\\\\d.])\`).test(block);
      }
      return { version: m ? m[1] : null, state, serves };
    }, EXPECTED_VERSION);

    // The testers tab holds the opt-in link; the releases tab does not.
    await page.evaluate(() => {
      const t = [...document.querySelectorAll('[role="tab"],button,a,span,div')]
        .find(e => e.children.length === 0 && /^測試人數\$/.test((e.textContent || "").trim()));
      if (t) (t.closest('[role="tab"]') || t).click();
    });
    await page.waitForTimeout(7000);

    const link = await page.evaluate(() => {
      const hits = document.documentElement.outerHTML
        .match(/https?:\\/\\/play\\.google\\.com\\/apps\\/(?:internaltest|testing)\\/[^"'<\\s\\\\]+/g) || [];
      return hits[0] || null;
    });

    console.log(\`\${label} VERSION: \${info.version || "(unknown)"} [\${info.state}]\`);
    console.log(\`\${label} LINK   : \${link || "(not found)"}\`);
    return info.serves;
  }

  const internalServes = await readTrack(\`\${base}/tracks/internal-testing\`, "INTERNAL");
  const rootiServes = await readTrack(\`\${base}/tracks/\${ROOTI_TRACK_UI_ID}\`, "CLOSED-ROOTI");

  if (EXPECTED_VERSION) {
    console.log(internalServes && rootiServes
      ? \`VERSION_CHECK: OK (both tracks serving \${EXPECTED_VERSION})\`
      : \`VERSION_CHECK: MISMATCH (expected \${EXPECTED_VERSION}; internal=\${internalServes}, rooti=\${rootiServes})\`);
  }
  console.log("Done.");
} finally {
  await task.finish({ keep: [] });
}
EOF
