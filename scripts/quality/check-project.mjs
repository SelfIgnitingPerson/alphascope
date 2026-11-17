/**
 * 核对受维护文件的目录边界、唯一 SQL 与本地文档链接。
 * 仅扫描源码，不读取本机 .env、运行资料、依赖或编译目录。
 */
import { readdir, readFile, stat } from "node:fs/promises";
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
if (sql.length !== 1 || sql[0] !== baseline) {
  errors.push(
    "数据库只维护 backend/src/main/resources/db/migration/V1__baseline.sql 一份 SQL",
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

if (errors.length) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log(
    `结构检查通过：${files.length} 个受维护文件，唯一 SQL，文档本地链接可用。`,
  );
}
