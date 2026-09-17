import { HttpError } from "./http.js";
// Claim and conditional business writes MUST share one batch transaction.
export function writeGate(db, key, seconds, token = crypto.randomUUID()) {
  const now = Math.floor(Date.now() / 1000);
  const claim = db.prepare(`INSERT INTO write_throttle(key,last_at,token) VALUES(?,?,?)
    ON CONFLICT(key) DO UPDATE SET last_at=excluded.last_at, token=excluded.token
    WHERE excluded.last_at-write_throttle.last_at >= ?`).bind(key, now, token, seconds);
  return { claim, key, token, now, seconds };
}
export const gateCondition = "EXISTS(SELECT 1 FROM write_throttle WHERE key=? AND token=?)";
export function requireClaim(result, seconds) {
  if (!result.meta?.changes) throw new HttpError(429, "操作过于频繁，请稍后再试", { "Retry-After": String(seconds) });
}
export async function releaseGate(db, gate) {
  await db.prepare("DELETE FROM write_throttle WHERE key=? AND token=?").bind(gate.key, gate.token).run();
}
