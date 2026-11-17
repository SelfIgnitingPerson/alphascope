/** 从唯一SQL生成预算触发器与字典；check模式拒绝新证据字段漏计量及文档漂移。 */
import { readFile, writeFile } from 'node:fs/promises';
const sqlPath='backend/src/main/resources/db/migration/V1__baseline.sql';
let sql=await readFile(sqlPath,'utf8');
const boundary=await readFile('backend/src/main/java/com/alphascope/identity/config/TenantTables.java','utf8');
const privateChunk=boundary.split('Set<String> PRIVATE =')[1].split(';')[0];
const privateTables=new Set([...privateChunk.matchAll(/"([a-z_]+)"/g)].map(x=>x[1]));
const tables=[...sql.matchAll(/CREATE TABLE `([^`]+)` \(([\s\S]*?)\) ENGINE=[^;]*?COMMENT='([^']*)';/g)].map(([,name,body,comment])=>({name,body,comment,columns:[...body.matchAll(/^\s*`([^`]+)` (.*?) COMMENT '([^']*)',?$/gm)].map(([,name,type,comment])=>({name,type,comment}))}));
const active="('PENDING','RUNNING','CANCEL_REQUESTED','TIMEOUT_REQUESTED')";
const qualified = (t, prefix) => t.columns.filter(c=>/^(?:json|longtext|mediumtext|text)\b/i.test(c.type)).map(c=>`COALESCE(OCTET_LENGTH(${prefix}.\`${c.name}\`),0)`).join('+')||'0';
const reservation=(t,prefix)=>['job_execution','research_validation_task'].includes(t.name)?`CASE WHEN ${prefix}.status IN ${active} THEN ${prefix}.reservation_bytes ELSE 0 END`:'0';
const statements=[];
for(const table of tables){
 if(qualified(table,'NEW')==='0'&&!['job_execution','research_validation_task'].includes(table.name))continue;
 for(const [event,suffix] of [['INSERT','ai'],['UPDATE','au'],['DELETE','ad']]){
  const record=event==='DELETE'?'OLD':'NEW';
  const uid=privateTables.has(table.name)?table.name==='job_execution'?`CASE WHEN ${record}.job_name IN ('DAILY','WEEKLY') THEN ${record}.tenant_id ELSE 0 END`:`${record}.tenant_id`:'0';
  const d=event==='INSERT'?qualified(table,'NEW'):event==='DELETE'?`-(${qualified(table,'OLD')})`:`(${qualified(table,'NEW')})-(${qualified(table,'OLD')})`;
  const r=event==='INSERT'?reservation(table,'NEW'):event==='DELETE'?`-(${reservation(table,'OLD')})`:`(${reservation(table,'NEW')})-(${reservation(table,'OLD')})`;
  const immutable=event==='UPDATE'&&privateTables.has(table.name)?" IF NEW.tenant_id<>OLD.tenant_id THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ALPHASCOPE_STORAGE_OWNER_IMMUTABLE'; END IF;\n":'';
  statements.push(`CREATE TRIGGER as_budget_${table.name}_${suffix} AFTER ${event} ON \`${table.name}\` FOR EACH ROW
BEGIN
 DECLARE d BIGINT; DECLARE r BIGINT; DECLARE uid BIGINT;
${immutable} SET d=${d}; SET r=${r}; SET uid=${uid};
 IF d<>0 OR r<>0 THEN
  UPDATE storage_budget SET used_bytes=used_bytes+d,reserved_bytes=reserved_bytes+r WHERE scope_id=0 AND used_bytes+d>=0 AND reserved_bytes+r>=0 AND (d+r<=0 OR used_bytes+reserved_bytes+d+r<=limit_bytes);
  IF ROW_COUNT()=0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ALPHASCOPE_STORAGE_PLATFORM_LIMIT'; END IF;
  IF uid>0 THEN
   INSERT IGNORE INTO storage_budget(scope_id,limit_bytes) SELECT uid,limit_bytes FROM storage_budget WHERE scope_id=-1;
   UPDATE storage_budget SET used_bytes=used_bytes+d,reserved_bytes=reserved_bytes+r WHERE scope_id=uid AND used_bytes+d>=0 AND reserved_bytes+r>=0 AND (d+r<=0 OR used_bytes+reserved_bytes+d+r<=limit_bytes);
   IF ROW_COUNT()=0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ALPHASCOPE_STORAGE_USER_LIMIT'; END IF;
  END IF;
 END IF;
END$$`);
 }
}
const guard='-- BEGIN STORAGE GUARDS\nDELIMITER $$\n'+statements.join('\n\n')+'\nDELIMITER ;\n-- END STORAGE GUARDS\n';
const next=sql.replace(/\n?-- BEGIN STORAGE GUARDS[\s\S]*?-- END STORAGE GUARDS\n?/,'').trimEnd()+'\n\n'+guard;
const count=tables.reduce((n,t)=>n+t.columns.length,0);
const doc='# 当前数据库数据字典\n\n以唯一 [V1开发基线](../../backend/src/main/resources/db/migration/V1__baseline.sql) 为准。金额为人民币元、数量为股、比例为小数，业务时间为上海时区。\n\n当前共'+tables.length+'张业务与控制表、'+count+'个中文字段注释，Flyway历史表另计。私人表的tenant_id由认证身份或后台执行范围注入，不接受客户端指定；共享数据边界见[多用户设计](../architecture/multi-user.md)。\n\n'+tables.map(t=>'## '+t.name+'\n\n'+t.comment+'\n\n| 字段 | 类型与约束 | 说明 |\n| --- | --- | --- |\n'+t.columns.map(c=>'| '+c.name+' | '+c.type.replaceAll('|','\\|')+' | '+c.comment.replaceAll('|','\\|')+' |').join('\n')).join('\n\n')+'\n';
if(process.argv.includes('--write')){await writeFile(sqlPath,next);await writeFile('docs/database/schema.md',doc);}else{
 if(sql!==next)throw Error('预算触发器与证据字段不一致，请执行node scripts/quality/schema-docs.mjs --write');
 if(await readFile('docs/database/schema.md','utf8')!==doc)throw Error('数据库字典已漂移，请重新生成');
}
console.log(`schema: ${tables.length} tables / ${count} columns / ${statements.length} budget triggers`);
