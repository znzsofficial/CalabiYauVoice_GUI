import {
  VOICE_INDEX_SCHEMA_VERSION,
  VOICE_INDEX_TTL_MS,
  type VoiceIndexCacheRecord,
  type VoiceIndexFailure,
  type VoiceIndexSections,
} from './voiceIndexTypes';

const DB_NAME = 'klbq-voice-index';
const DB_VERSION = 1;
const STORE_NAME = 'index';
const RECORD_KEY = 'main';

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB unavailable'));
      return;
    }
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onerror = () => reject(request.error || new Error('IndexedDB open failed'));
    request.onsuccess = () => resolve(request.result);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME);
      }
    };
  });
}

function idbRequest<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error('IndexedDB request failed'));
  });
}

function isValidRecord(value: unknown): value is VoiceIndexCacheRecord {
  if (!value || typeof value !== 'object') return false;
  const record = value as VoiceIndexCacheRecord;
  return record.schemaVersion === VOICE_INDEX_SCHEMA_VERSION
    && typeof record.builtAt === 'number'
    && Array.isArray(record.characterTitles)
    && Array.isArray(record.index)
    && Array.isArray(record.failed);
}

export function isVoiceIndexFresh(builtAt: number, now = Date.now()): boolean {
  return now - builtAt < VOICE_INDEX_TTL_MS;
}

export async function loadVoiceIndexCache(): Promise<VoiceIndexCacheRecord | null> {
  try {
    const db = await openDb();
    try {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const store = tx.objectStore(STORE_NAME);
      const value = await idbRequest(store.get(RECORD_KEY));
      if (!isValidRecord(value)) return null;
      return value;
    } finally {
      db.close();
    }
  } catch {
    return null;
  }
}

export async function saveVoiceIndexCache(input: {
  characterTitles: string[];
  index: Map<string, VoiceIndexSections>;
  failed: VoiceIndexFailure[];
  builtAt?: number;
}): Promise<void> {
  const record: VoiceIndexCacheRecord = {
    schemaVersion: VOICE_INDEX_SCHEMA_VERSION,
    builtAt: input.builtAt ?? Date.now(),
    characterTitles: [...input.characterTitles],
    index: [...input.index.entries()],
    failed: input.failed.map(item => ({ ...item })),
  };
  try {
    const db = await openDb();
    try {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      await idbRequest(store.put(record, RECORD_KEY));
    } finally {
      db.close();
    }
  } catch {
    // persistence is best-effort
  }
}

export async function clearVoiceIndexCache(): Promise<void> {
  try {
    const db = await openDb();
    try {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      await idbRequest(store.delete(RECORD_KEY));
    } finally {
      db.close();
    }
  } catch {
    // ignore
  }
}

export function cacheToIndexMap(record: VoiceIndexCacheRecord): Map<string, VoiceIndexSections> {
  return new Map(record.index);
}
