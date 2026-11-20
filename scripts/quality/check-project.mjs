/**
 * 核对受维护文件的目录边界、数据库迁移与本地文档链接。
 * 仅扫描源码，不读取本机 .env、运行资料、依赖或编译目录。
 */
import { readdir, readFile, stat } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../../", import.meta.url));
const ignored = new Set([
  ".git",
  ".idea",
  ".runtime",
  "artifacts",
  "target",
  "node_modules",
  "dist",
  ".m2",
  ".npm",
]);
const files = [];
const errors = [];

async function scan(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    if (ignored.has(entry.name) || entry.name.startsWith(".env")) continue;
    const file = path.join(directory, entry.name);
    if (entry.isSymbolicLink()) continue;
    if (entry.isDirectory()) await scan(file);
    else if (entry.isFile()) files.push(file);
  }
}

await import("./schema-docs.mjs");
await scan(root);
const sql = files.filter((file) => file.endsWith(".sql"));
const baseline = path.join(
  root,
  "backend/src/main/resources/db/migration/V1__baseline.sql",
);
const migrationNames = sql.map((file) => path.basename(file));
const migrationVersions = migrationNames.map((name) => name.split("__")[0]);
if (
  !sql.includes(baseline) ||
  sql.some(
    (file) =>
      file !== baseline &&
      (path.dirname(file) !== path.dirname(baseline) ||
        !/^V(?:[2-9]|[1-9]\d+)__[a-z][a-z0-9_]*\.sql$/.test(path.basename(file))),
  ) ||
  new Set(migrationVersions).size !== migrationVersions.length
) {
  errors.push(
    "数据库迁移集中在 backend/src/main/resources/db/migration，须保留唯一 V1 基线并使用不同版本编号",
  );
}

for (const file of files) {
  const relative = path.relative(root, file);
  if (/(?:^|\/)(?:[^/]+\.(?:bak|orig|rej)|[^/]+[~])$/.test(relative)) {
    errors.push(`源码中存在临时或冲突残留：${relative}`);
  }
  if (!file.endsWith(".md")) continue;
  const markdown = (await readFile(file, "utf8")).replace(
    /^```[^\n]*\n[\s\S]*?^```/gm,
    "",
  );
  for (const match of markdown.matchAll(/\[[^\]\n]*\]\(([^)\n]+)\)/g)) {
    const target = match[1].trim().replace(/^<([^>]+)>$/, "$1");
    if (/^(?:[a-z][a-z0-9+.-]*:|#)/i.test(target)) continue;
    const destination = target.split(/[?#]/, 1)[0];
    if (!destination) continue;
    try {
      const resolved = path.resolve(
        path.dirname(file),
        decodeURIComponent(destination),
      );
      const within = path.relative(root, resolved);
      if (within.startsWith("..") || path.isAbsolute(within))
        throw new Error("项目外链接");
      await stat(resolved);
    } catch {
      errors.push(`文档链接失效：${relative} → ${target}`);
    }
  }
}

if (process.env.GITLAB_CI === "true") {
  const git = (...args) =>
    execFileSync("git", args, { cwd: root, encoding: "utf8" }).trim();
  const released = git("tag", "--merged", "HEAD", "--sort=-version:refname")
    .split("\n")
    .find((tag) => /^v\d+\.\d+\.\d+$/.test(tag) && tag !== process.env.CI_COMMIT_TAG);
  if (released) {
    const baseline = "backend/src/main/resources/db/migration/V1__baseline.sql";
    const original = execFileSync("git", ["show", `${released}:${baseline}`], { cwd: root });
    if (!original.equals(await readFile(path.join(root, baseline)))) {
      errors.push(`已发布的 ${released} 数据库 V1 与本次源码不同，请通过增量迁移修改结构。`);
    }
  }
}

if (errors.length) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log(
    `结构检查通过：${files.length} 个受维护文件，数据库迁移与文档本地链接可用。`,
  );
}
