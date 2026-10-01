#!/usr/bin/env node
/**
 * SEC4-642 — fails the build if anything secret made it into the production
 * bundle: the Fauxnance API key, the Fauxnance host, the JWT signing
 * secret, or anything else carrying one of those literal values under a
 * different name.
 *
 * Runs against `dist/`, whatever that directory actually contains — if the
 * build is ever configured to emit source maps, this scan picks those up
 * too, since a source map is the source again. Minification is not
 * obfuscation, so this greps the built text, not the pretty-printed
 * source.
 *
 * Secret *values* are read from the repo root's `.env.example` (never
 * hard-coded here) so a key rotation doesn't silently go unscanned.
 */
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..', '..');
const distDir = path.resolve(__dirname, '..', 'dist');
const envExamplePath = path.join(repoRoot, '.env.example');

function readEnvExample() {
  const text = fs.readFileSync(envExamplePath, 'utf8');
  const values = {};
  for (const line of text.split('\n')) {
    const match = /^([A-Z0-9_]+)=(.+)$/.exec(line.trim());
    if (match) {
      values[match[1]] = match[2].trim();
    }
  }
  return values;
}

function collectFiles(dir) {
  const results = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...collectFiles(full));
    } else {
      results.push(full);
    }
  }
  return results;
}

function main() {
  if (!fs.existsSync(distDir)) {
    console.error(`check:bundle — no dist/ found at ${distDir}. Run "npm run build" first.`);
    process.exit(1);
  }

  const env = readEnvExample();
  const jwtSecret = env.JWT_SECRET;
  const fauxnanceHost = env.FAUXNANCE_BASE_URL ? new URL(env.FAUXNANCE_BASE_URL).host : undefined;
  const fauxnanceApiKeyPlaceholder = env.FAUXNANCE_API_KEY;

  if (!jwtSecret || !fauxnanceHost) {
    console.error('check:bundle — could not read JWT_SECRET / FAUXNANCE_BASE_URL from the root .env.example.');
    process.exit(1);
  }

  // Name patterns (catch the variable name, wherever it leaked) and literal
  // value patterns (catch the value even if someone renamed the variable).
  const patterns = [
    { label: 'the word FAUXNANCE', regex: /FAUXNANCE/i },
    { label: 'the Fauxnance host', regex: new RegExp(escapeRegExp(fauxnanceHost), 'i') },
    { label: 'the word API_KEY', regex: /API_KEY/i },
    { label: 'the word SECRET', regex: /SECRET/i },
    { label: 'the literal JWT_SECRET value', regex: new RegExp(escapeRegExp(jwtSecret)) },
  ];
  if (fauxnanceApiKeyPlaceholder && !/^replace-/i.test(fauxnanceApiKeyPlaceholder)) {
    patterns.push({
      label: 'the literal FAUXNANCE_API_KEY value',
      regex: new RegExp(escapeRegExp(fauxnanceApiKeyPlaceholder)),
    });
  }

  const files = collectFiles(distDir);
  const findings = [];

  for (const file of files) {
    const content = fs.readFileSync(file, 'utf8');
    for (const pattern of patterns) {
      if (pattern.regex.test(content)) {
        findings.push({ file: path.relative(distDir, file), label: pattern.label });
      }
    }
  }

  if (findings.length > 0) {
    console.error(`check:bundle — FOUND ${findings.length} match(es) in the production bundle:\n`);
    for (const finding of findings) {
      console.error(`  ${finding.file}: matches ${finding.label}`);
    }
    process.exit(1);
  }

  console.log(`check:bundle — scanned ${files.length} file(s) under dist/, found nothing secret. OK.`);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

main();
