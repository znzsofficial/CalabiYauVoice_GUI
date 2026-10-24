import test from 'node:test';
import assert from 'node:assert/strict';
import { SEARCH_TASKS, initialSearchProfile, searchTask, searchGuide } from '../src/search/searchExperience.ts';

test('voice download is the first task and the default landing surface', () => {
  assert.equal(SEARCH_TASKS[0].profile, 'voiceSubtitle');
  assert.equal(initialSearchProfile(new URLSearchParams()), 'voiceSubtitle');
  assert.equal(initialSearchProfile(new URLSearchParams('q=')), 'voiceSubtitle');
});

test('legacy query links still open wiki; explicit task links work', () => {
  assert.equal(initialSearchProfile(new URLSearchParams('q=星绘')), 'default');
  assert.equal(initialSearchProfile(new URLSearchParams('q=星绘&mode=voiceSubtitle')), 'voiceSubtitle');
  assert.equal(initialSearchProfile(new URLSearchParams('mode=images')), 'images');
  assert.equal(initialSearchProfile(new URLSearchParams('mode=bogus')), 'voiceSubtitle');
});

test('every legacy tool stays reachable under a clear task', () => {
  assert.equal(searchTask('voiceSubtitle'), 'voice');
  for (const profile of ['images', 'categoryDownload', 'voiceCategory']) assert.equal(searchTask(profile), 'assets');
  for (const profile of ['default', 'all', 'advanced']) assert.equal(searchTask(profile), 'wiki');
});

test('input instructions distinguish instant filtering from submitted searches', () => {
  assert.match(searchGuide('voiceSubtitle').hint, /实时输入筛选/);
  assert.match(searchGuide('voiceSubtitle').description, /语音台词/);
  for (const profile of ['default', 'images', 'categoryDownload', 'voiceCategory']) {
    assert.match(searchGuide(profile).hint, /Enter/);
    assert.ok(searchGuide(profile).examples.length > 0);
  }
  assert.match(searchGuide('voiceCategory').hint, /角色语音与字幕/);
});
