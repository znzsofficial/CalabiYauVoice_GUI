import type { ProfileValue } from './searchTypes';

export const SEARCH_TASKS = [
  { profile: 'voiceSubtitle', title: '角色语音与字幕', description: '多语言台词试听、音频与字幕批量导出', icon: 'lucide:audio-lines' },
  { profile: 'images', title: '图片与文件素材', description: '高清立绘、壁纸、图标预览与批量打包', icon: 'lucide:images' },
  { profile: 'default', title: 'Wiki 词条检索', description: '角色设定、武器数值、地图与系统玩法资料', icon: 'lucide:book-open' }
] as const;

export function searchTask(profile: ProfileValue): 'voice' | 'assets' | 'wiki' {
  if (profile === 'voiceSubtitle') return 'voice';
  if (profile === 'images' || profile === 'categoryDownload' || profile === 'voiceCategory') return 'assets';
  return 'wiki';
}

export function initialSearchProfile(params: URLSearchParams): ProfileValue {
  const profile = params.get('mode');
  if (['voiceSubtitle', 'images', 'default', 'all', 'advanced', 'voiceCategory', 'categoryDownload'].includes(profile || '')) return profile as ProfileValue;
  // Existing navigation links with ?q= remain Wiki searches, not voice filters.
  return params.get('q')?.trim() ? 'default' : 'voiceSubtitle';
}

export function searchGuide(profile: ProfileValue) {
  if (profile === 'voiceSubtitle') return {
    title: '角色语音与字幕',
    description: '收录游戏内角色中/日/英多语言语音台词，支持单句在线试听与批量打包导出（含 LRC / SRT / VTT 字幕）。',
    placeholder: '按角色名或台词对白筛选…',
    hint: '支持实时输入筛选。开启全量台词索引后可检索特定对白与出处。',
    examples: ['星绘', '信', '芙拉薇娅']
  };
  if (profile === 'categoryDownload' || profile === 'voiceCategory') return {
    title: profile === 'voiceCategory' ? '语音分类目录' : '分类资源打包',
    description: '按 Wiki 分类层级探索素材集合，支持展开子分类结构并批量打包全部文件。',
    placeholder: '搜索分类名称，例如：角色、武器、时装…',
    hint: '按 Enter 或点击搜索查询。如需试听台词或导出字幕，建议使用「角色语音与字幕」模块。',
    examples: profile === 'voiceCategory' ? ['语音', '星绘'] : ['角色', '武器', '时装']
  };
  if (profile === 'images') return {
    title: '图片与文件素材',
    description: '检索 Wiki 收录的高清立绘、壁纸、图标等媒体资源，支持在线预览与勾选批量打包。',
    placeholder: '搜索立绘、壁纸、图标、道具…',
    hint: '按 Enter 或点击搜索查询。支持单文件直链下载与多选 ZIP 导出。',
    examples: ['星绘', '壁纸', '头像']
  };
  return {
    title: 'Wiki 词条检索',
    description: '查询卡拉彼丘 Wiki 词条正文，涵盖角色背景、武器数据、战斗机制与活动记录。',
    placeholder: '搜索角色、武器、地图、玩法…',
    hint: '按 Enter 或点击搜索查询。默认检索主命名空间，高级选项可指定命名空间与排序规则。',
    examples: ['星绘', '北极星', '空间实验室']
  };
}
