import { hmac } from "./auth.js";
import { HttpError, json, readJson, requireDb } from "./http.js";
import { writeGate, requireClaim } from "./writes.js";

const features = ["cards", "vote", "download", "subtitle"];
const outcomes = ["ok", "network", "data", "other"];
const metrics = [...features.flatMap(f => outcomes.map(o => `${f}_${o}`)), "cards_recovered"];
const today = () => Math.floor((Date.now() + 8 * 3600000) / 86400000);
const validInt = (v, lo, hi) => Number.isSafeInteger(v) && v >= lo && v <= hi;
const object = v => v != null && typeof v === "object" && !Array.isArray(v);
const only = (v, keys) => object(v) && Object.keys(v).every(k => keys.includes(k));

// Both statements must run in the same D1 batch. No uploads can modify these days (7-day backfill).
// A partial day may span several batches. Select the same primary keys in deterministic order.
export function archiveUsage(db, now = today(), limit = 500) {
  const oldest = `FROM app_usage_daily WHERE day < ? ORDER BY day,install_key LIMIT ?`;
  return [
    db.prepare(`INSERT INTO app_usage_history(day,starts,new_installs)
      SELECT day,SUM(starts),SUM(CASE WHEN day=cohort THEN active ELSE 0 END)
      FROM (SELECT day,starts,cohort,active ${oldest}) GROUP BY day
      ON CONFLICT(day) DO UPDATE SET starts=starts+excluded.starts,new_installs=new_installs+excluded.new_installs`)
      .bind(now - 44, limit),
    db.prepare(`DELETE FROM app_usage_daily WHERE (day,install_key) IN (SELECT day,install_key ${oldest})`)
      .bind(now - 44, limit),
  ];
}

export async function recordUsage(request, env) {
  const db = requireDb(env);
  const body = await readJson(request);
  const now = today();
  if (!only(body, ["schema", "install", "cohort", "days"]) || body.schema !== 1 || typeof body.install !== "string" ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(body.install) ||
      !validInt(body.cohort, 1, now) || !Array.isArray(body.days) || !body.days.length || body.days.length > 8) {
    throw new HttpError(400, "统计格式无效");
  }
  const seen = new Set();
  for (const r of body.days) {
    if (!only(r, ["day", "version", "seq", "active", "starts", "counts"]) || !validInt(r.day, Math.max(body.cohort, now - 7), now) ||
        seen.has(r.day) || typeof r.version !== "string" || !/^\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(r.version) ||
        !validInt(r.seq, 1, 1000000) || !validInt(r.active, 0, 1) || !validInt(r.starts, 0, 10000) || (r.starts && !r.active) ||
        !only(r.counts, metrics) || Object.values(r.counts).some(v => !validInt(v, 0, 100000))) throw new HttpError(400, "统计字段无效");
    seen.add(r.day);
  }
  if (!env.GUEST_SECRET || env.GUEST_SECRET.length < 32) throw new HttpError(503, "统计暂不可用");
  const ip = request.headers.get("CF-Connecting-IP");
  if (!ip) throw new HttpError(400, "缺少请求来源");
  const [key, bucket] = await Promise.all([hmac(env.GUEST_SECRET, `usage-install:${body.install}`), hmac(env.GUEST_SECRET, `usage-rate:${now}:${ip}`)]);
  // No raw IP or installation UUID in D1. Daily rotating quota keys are separate from metrics.
  const gate = writeGate(db, `usage:${bucket}`, 2);
  requireClaim(await gate.claim.run(), 2);
  const sql = `INSERT INTO app_usage_daily(day,install_key,cohort,version,seq,active,starts,counts) VALUES(?,?,?,?,?,?,?,?)
    ON CONFLICT(day,install_key) DO UPDATE SET version=excluded.version,seq=excluded.seq,
    active=MAX(active,excluded.active),starts=MAX(starts,excluded.starts),counts=excluded.counts
    WHERE excluded.seq>seq AND excluded.cohort=cohort`;
  await db.batch([...body.days.map(r => db.prepare(sql).bind(r.day, key.slice(0,32), body.cohort, r.version, r.seq, r.active, r.starts, JSON.stringify(r.counts))),
    ...archiveUsage(db,now,16),
    db.prepare("DELETE FROM write_throttle WHERE key IN (SELECT key FROM write_throttle WHERE last_at < unixepoch()-86400 LIMIT 8)")]);
  return json({ accepted: true });
}

export async function usageSummary(db) {
  const end = today(), start = end - 29;
  const sums = metrics.map(k => `SUM(COALESCE(json_extract(counts,'$.${k}'),0)) AS ${k}`).join(",");
  const [daily, cohorts, versions, older] = await db.batch([
    db.prepare(`SELECT day,SUM(active) AS dau,SUM(starts) AS starts,${sums} FROM app_usage_daily WHERE day BETWEEN ? AND ? GROUP BY day`).bind(start,end),
    db.prepare(`SELECT c.day,COUNT(*) AS installs,SUM(COALESCE(d1.active,0)) AS d1,SUM(COALESCE(d7.active,0)) AS d7 FROM app_usage_daily c
      LEFT JOIN app_usage_daily d1 ON d1.day=c.day+1 AND d1.install_key=c.install_key
      LEFT JOIN app_usage_daily d7 ON d7.day=c.day+7 AND d7.install_key=c.install_key
      WHERE c.day BETWEEN ? AND ? AND c.day=c.cohort AND c.active=1 GROUP BY c.day`).bind(start,end),
    db.prepare(`SELECT version,COUNT(*) AS installs FROM app_usage_daily WHERE day=? AND active=1 GROUP BY version ORDER BY installs DESC,version`).bind(end),
    // Reuse the recent days below. Include unarchived older rows even if maintenance is behind.
    db.prepare(`SELECT MIN(day) AS since,COALESCE(SUM(starts),0) AS starts,COALESCE(SUM(new_installs),0) AS newInstalls FROM (
      SELECT day,starts,new_installs FROM app_usage_history
      UNION ALL SELECT day,starts,CASE WHEN day=cohort THEN active ELSE 0 END FROM app_usage_daily WHERE day < ?
    )`).bind(start),
  ]);
  const dailyByDay=new Map(daily.results.map(r=>[r.day,r])), byCohort=new Map(cohorts.results.map(r=>[r.day,r]));
  const totals=Object.fromEntries(metrics.map(k=>[k,0]));
  const days=Array.from({length:30},(_,i)=>{
    const day=start+i, row=dailyByDay.get(day), cohort=byCohort.get(day);
    for(const k of metrics) totals[k]+=row?.[k]??0;
    return {date:new Date(day*86400000).toISOString().slice(0,10),dau:row?.dau??0,starts:row?.starts??0,newInstalls:cohort?.installs??0,
      d1:day+1<end&&cohort ? cohort.d1:null,d7:day+7<end&&cohort ? cohort.d7:null};
  });
  const {since, starts, newInstalls} = older.results[0];
  const first = since ?? (daily.results.length ? Math.min(...daily.results.map(r => r.day)) : null);
  const lifetime = {since:first === null ? null : new Date(first*86400000).toISOString().slice(0,10),
    starts:starts+days.reduce((n,d)=>n+d.starts,0), newInstalls:newInstalls+days.reduce((n,d)=>n+d.newInstalls,0)};
  return json({days, versions:versions.results, totals, lifetime});
}
