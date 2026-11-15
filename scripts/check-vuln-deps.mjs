#!/usr/bin/env node
// 依赖审计断言（todo.md VULN-AWN-001/002 深审 287 条的回归门禁）。
// 阻断 git-clone / html-minifier 成为任何 workspace 包的「直接依赖」：
// 两者当前唯一入口是 @tarojs 工具链的传递依赖（@tarojs/cli → download-git-repo →
// git-clone；@tarojs/webpack5-runner → html-minifier），上游 Taro 最新版（4.3.3）
// 仍钉死旧版、无补丁可升，属已记录的接受风险；任何人把它们加为直接依赖
// （绕过 Taro、扩大暴露面）时本断言即红。
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const BANNED = ["git-clone", "download-git-repo", "html-minifier"];
const DEP_FIELDS = [
  "dependencies",
  "devDependencies",
  "optionalDependencies",
  "peerDependencies",
];

function packageJsonPaths(dir) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    let s;
    try {
      s = statSync(p);
    } catch {
      continue;
    }
    if (s.isDirectory()) {
      if (name === "node_modules" || name.startsWith(".")) continue;
      out.push(...packageJsonPaths(p));
    } else if (name === "package.json") {
      out.push(p);
    }
  }
  return out;
}

const offenders = [];
for (const file of packageJsonPaths(process.cwd())) {
  let pkg;
  try {
    pkg = JSON.parse(readFileSync(file, "utf8"));
  } catch {
    continue;
  }
  for (const field of DEP_FIELDS) {
    const deps = pkg[field];
    if (!deps) continue;
    for (const banned of BANNED) {
      if (deps[banned]) offenders.push(`${file} ${field}.${banned}=${deps[banned]}`);
    }
  }
}

if (offenders.length > 0) {
  console.error("vuln-dep audit FAILED: banned packages present as direct deps");
  for (const o of offenders) console.error(`  ${o}`);
  console.error(
    "these packages are only acceptable as @tarojs transitive deps (see todo.md VULN-AWN-001/002)",
  );
  process.exit(1);
}
console.log("vuln-dep audit ok: no banned direct deps (git-clone/download-git-repo/html-minifier)");
