import type { FileAsset, ResultImage, WikiSearchItem } from './searchApi';

export type ProfileValue = 'default' | 'images' | 'all' | 'advanced' | 'voiceCategory' | 'categoryDownload' | 'voiceSubtitle';
export type Status = 'idle' | 'loading' | 'empty' | 'error' | 'ready';
export type SortValue = 'relevance' | 'last_edit_desc' | 'last_edit_asc' | 'create_timestamp_desc' | 'incoming_links_desc';
export type NamespaceOption = { id: number; name: string };

export type SearchResult = WikiSearchItem & {
  title: string;
  ns: number;
  image?: ResultImage;
  file?: FileAsset;
  categories: string[];
  url: string;
  nsName: string;
  dateStr: string;
  pageSizeKB: string;
  fileSize: string;
  wordCountStr: string;
  delay: string;
};
