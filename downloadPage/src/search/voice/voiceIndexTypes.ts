export type VoiceIndexLine = {
  category: string;
  cnText: string;
  jpText: string;
  enText: string;
};

export type VoiceIndexSection = {
  title: string;
  lines: VoiceIndexLine[];
};

export type VoiceIndexSections = VoiceIndexSection[];

export type VoiceIndexFailure = {
  title: string;
  error: string;
  at: number;
  attempts: number;
};

export type VoiceIndexCacheRecord = {
  schemaVersion: 1;
  builtAt: number;
  characterTitles: string[];
  index: Array<[string, VoiceIndexSections]>;
  failed: VoiceIndexFailure[];
};

export const VOICE_INDEX_SCHEMA_VERSION = 1 as const;
/** Serve cached index for 24h before a silent refresh is preferred. */
export const VOICE_INDEX_TTL_MS = 24 * 60 * 60 * 1000;
