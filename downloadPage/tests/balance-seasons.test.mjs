import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { mergeBalanceSeasons, supportsBalanceScore } from '../src/balanceSeasons.ts';

test('fills historical seasons, keeps upstream metadata, sorts newest first and is idempotent', () => {
  const upstream = [
    { code: '11', name: '鸣弦之誓' }, { code: '18', name: '错季花园' },
    { code: '3', name: '官方修订名' }, { code: '20', name: '未来赛季' }
  ];
  const merged = mergeBalanceSeasons(upstream);
  assert.deepEqual(merged.map(item => item.code), ['20', '18', '11', ...Array.from({ length: 10 }, (_, i) => String(10 - i))]);
  assert.equal(merged.find(item => item.code === '3').name, '官方修订名');
  assert.equal(merged.find(item => item.code === '10').name, '敕魂命途');
  assert.equal(upstream.length, 4);
  assert.deepEqual(mergeBalanceSeasons(merged), merged);
});

test('score is only available from S8 onward', () => {
  assert.equal(supportsBalanceScore('7'), false);
  assert.equal(supportsBalanceScore(''), false);
  assert.equal(supportsBalanceScore('8'), true);
  assert.equal(supportsBalanceScore('18'), true);
});

test('web historical catalog matches the shared Android/desktop catalog', () => {
  const kotlin = readFileSync(new URL('../../shared/src/commonMain/kotlin/data/BalanceSeasons.kt', import.meta.url), 'utf8');
  const names = [...kotlin.match(/historicalNames = listOf\(([\s\S]*?)\)/)[1].matchAll(/"([^"]+)"/g)].map(match => match[1]);
  assert.deepEqual(mergeBalanceSeasons([]).reverse().map(item => item.name), names);
});
