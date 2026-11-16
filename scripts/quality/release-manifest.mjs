/** 发布资料只从白名单产物取指纹，不读取.env或业务资料，不包含凭据。 */
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { createHash } from "node:crypto";
import { resolve } from "node:path";
const root = resolve(import.meta.dirname, "../..");
const digest = async (path) =>
  createHash("sha256")
    .update(await readFile(resolve(root, path)))
    .digest("hex");
const version = "0.1.0";
const manifest = {
  version,
  isolationProtocol: "TENANT_V1",
  createdAt: new Date().toISOString(),
  revision: process.env.CI_COMMIT_SHA || "LOCAL_UNPUBLISHED",
  files: {
    jar: await digest(`backend/target/alphascope-backend-${version}.jar`),
    baseline: await digest(
      "backend/src/main/resources/db/migration/V1__baseline.sql",
    ),
    frontendLock: await digest("frontend/package-lock.json"),
    dockerfile: await digest("Dockerfile"),
  },
  deployment: "NOT_DEPLOYED",
  sourcePermissions: "REQUIRE_ACTUAL_OPERATOR_RECORDS",
  restorePoint: "NOT_CREATED",
};
await mkdir(resolve(root, "artifacts"), { recursive: true });
await writeFile(
  resolve(root, "artifacts/release-manifest.json"),
  JSON.stringify(manifest, null, 2) + "\n",
);
try {
  const bom = JSON.parse(
    await readFile(resolve(root, "target/bom.json"), "utf8"),
  );
  const backendInventory = (bom.components || []).map((item) => ({
    ecosystem: "maven",
    name: item.group ? `${item.group}:${item.name}` : item.name,
    version: item.version,
    licenses: (item.licenses || []).map(
      (value) =>
        value.license?.id ||
        value.license?.name ||
        value.expression ||
        "UNKNOWN",
    ),
  }));
  // 锁文件中包含实际解析的传递依赖；缺失许可证必须显式待审阅。
  const lock = JSON.parse(
    await readFile(resolve(root, "frontend/package-lock.json"), "utf8"),
  );
  const frontendInventory = Object.entries(lock.packages || {})
    .filter(([path]) => path !== "")
    .map(([path, item]) => ({
      ecosystem: "npm",
      name: path.split("node_modules/").at(-1),
      version: item.version,
      licenses: [item.license || "UNKNOWN"],
    }));
  await writeFile(
    resolve(root, "artifacts/dependency-licenses.json"),
    JSON.stringify(
      {
        status: "INVENTORY_REQUIRES_REVIEW",
        components: [...backendInventory, ...frontendInventory],
      },
      null,
      2,
    ) + "\n",
  );
} catch (error) {
  if (error.code !== "ENOENT") throw error;
}
console.log("artifacts/release-manifest.json; no deployment performed");
