#!/usr/bin/env node
/**
 * C9 ② 部分唯一索引守护脚本（门禁4 审计阻断项 5 的落地控制）
 *
 * 背景：`space_occupancy` 的并发保护依赖**部分唯一索引**
 *   CREATE UNIQUE INDEX uniq_effective_slot ... WHERE status IN ('PENDING','EFFECTIVE')
 * Prisma schema 无法表达 partial index，故该索引只能由手写 SQL 迁移维护（见 schema.prisma 注释）。
 * 风险：任何一次 `prisma migrate dev` 都可能自动产出 `DROP INDEX "uniq_effective_slot"` 的迁移，
 *      若不拦截，C9 并发保护会被**静默删除**，且只有压测才会暴露。
 *
 * 本脚本扫全部迁移 SQL，命中删除该索引的语句即非零退出，
 * 供 `npm run prisma:guard` 在本地与 CI 提交前执行。
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', 'prisma', 'migrations');
const TARGET = 'uniq_effective_slot';

function walk(dir) {
  const out = [];
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(p));
    else if (entry.name.endsWith('.sql')) out.push(p);
  }
  return out;
}

const files = walk(ROOT);
const offenders = [];

for (const file of files) {
  const sql = fs.readFileSync(file, 'utf8');
  const lines = sql.split(/\r?\n/);
  lines.forEach((line, i) => {
    // 忽略注释行
    if (/^\s*--/.test(line)) return;
    if (!line.includes(TARGET)) return;
    if (/\bDROP\s+INDEX\b/i.test(line)) {
      offenders.push(`${path.relative(process.cwd(), file)}:${i + 1}  ${line.trim()}`);
    }
  });
}

if (offenders.length > 0) {
  console.error(
    `[prisma:guard] 拒绝：以下迁移删除了 C9 ② 部分唯一索引 ${TARGET}，会把并发保护静默干掉：`,
  );
  for (const o of offenders) console.error(`  - ${o}`);
  console.error(
    `\n处理办法：手工删除该 DROP 语句再提交；索引口径必须保持 WHERE status IN ('PENDING','EFFECTIVE')。`,
  );
  process.exit(1);
}

// 正向断言：init 迁移里必须存在创建语句
const initSql = files.find((f) => f.includes('20261030000000_init'));
if (initSql && !/CREATE\s+UNIQUE\s+INDEX\s+"?uniq_effective_slot"?/i.test(fs.readFileSync(initSql, 'utf8'))) {
  console.error(`[prisma:guard] 拒绝：init 迁移缺少 ${TARGET} 的创建语句，C9 ② 未生效。`);
  process.exit(1);
}

console.log(`[prisma:guard] OK：${files.length} 个迁移文件，${TARGET} 未被删除且已在 init 中创建。`);
