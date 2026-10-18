import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { balanceAvatarIds, balanceAvatarSources } from '../src/balanceAvatars.ts';
import { balanceDelta, formatBalanceMetric, filterBalanceHeroes } from '../src/balanceView.ts';

const hero = (id, name, winRate) => ({ id, heroName: name, winRate, selectRate: 10, kd: 1, damageAve: 200, score: 200 });

test('search and position filtering preserve valid rows and stable numeric ordering', () => {
  const rows = [hero(3, '星绘', 51), hero(2, '白墨', 51), hero(1, '旧角色', 0)];
  const meta = { 3: { code: '3', name: '星绘', position_code: 'support' } };
  assert.deepEqual(filterBalanceHeroes(rows, meta, '', '', 'winRate', true).map(x => x.id), [2, 3]);
  assert.deepEqual(filterBalanceHeroes(rows, meta, ' 星绘 ', 'support', 'winRate', false).map(x => x.id), [3]);
  assert.equal(filterBalanceHeroes(rows, meta, '不存在', '', 'winRate', true).length, 0);
  assert.equal(rows[0].id, 3, 'does not mutate response');
});

test('comparisons use percentage points, do not invent missing values and round before checking sign', () => {
  assert.equal(balanceDelta(52.5, 50, 'winRate'), '+2.50 pp');
  assert.equal(balanceDelta(48, 50, 'winRate'), '−2.00 pp');
  assert.equal(balanceDelta(50, undefined, 'winRate'), '');
  assert.equal(balanceDelta(1.001, 1, 'kd'), '持平');
  assert.equal(formatBalanceMetric(undefined, 'score'), '—');
  assert.equal(formatBalanceMetric(NaN, 'winRate'), '—');
  assert.equal(formatBalanceMetric(0, 'kd'), '0.00');
  assert.equal(formatBalanceMetric(47.61, 'winRate'), '47.61%');
});

test('all known portraits are bundled as small WebP files; future characters use official URLs', () => {
  assert.equal(new Set(balanceAvatarIds).size, 24);
  for (const id of balanceAvatarIds) {
    const bytes = readFileSync(new URL(`../public/balance-avatars/${id}.webp`, import.meta.url));
    assert.equal(bytes.toString('ascii', 0, 4), 'RIFF');
    assert.equal(bytes.toString('ascii', 8, 12), 'WEBP');
    assert.ok(bytes.length < 20000, `portrait ${id} should stay optimized`);
    assert.deepEqual(balanceAvatarSources(id, 'https://official.example/avatar.png'), [`/balance-avatars/${id}.webp`, 'https://official.example/avatar.png']);
  }
  assert.deepEqual(balanceAvatarSources(999, 'https://official.example/new.png'), ['https://official.example/new.png']);
  assert.deepEqual(balanceAvatarSources(999), []);
  assert.deepEqual(balanceAvatarSources(999, 'javascript:alert(1)'), []);
});
