/**
 * 导航页扩展合集与弹窗数据定义
 * 提供猫娘百宝箱、官方媒体渠道、筛选表等聚合弹窗的丰富元数据
 */

export interface ModalToolItem {
  title: string;
  desc: string;
  url: string;
  icon: string;
  tag: string;
  color: string;
  isExternal?: boolean;
}

export interface CollectionModalData {
  id: string;
  title: string;
  subtitle: string;
  icon: string;
  color: string;
  items: ModalToolItem[];
  footerTip?: string;
  wikiUrl?: string;
}

/** 猫娘的百宝箱：8 款社区同人与实用 Web 工具 */
export const CATGIRL_TOOLBOX_MODAL: CollectionModalData = {
  id: 'catgirl-toolbox',
  title: '猫娘的百宝箱',
  subtitle: '引航者社区共同维护的 8 款实用创作、生成与娱乐小工具',
  icon: 'lucide:box',
  color: '#06b6d4',
  wikiUrl: 'https://wiki.biligame.com/klbq/WIKI_APP',
  footerTip: '工具均由玩家社区与创作者开发维护，点击即可在新窗口直接使用',
  items: [
    {
      title: '生化卡牌生成器',
      desc: '晶源感染模式专属卡组配置与自定义卡牌在线制作',
      url: 'https://card.klbq.fsltech.cn',
      icon: 'lucide:credit-card',
      tag: '在线工具',
      color: '#a855f7',
      isExternal: true
    },
    {
      title: '贴纸生成器',
      desc: '游戏内萌趣角色表情、气泡对话框与贴纸在线拼图',
      url: 'https://klbqbq.site',
      icon: 'lucide:smile',
      tag: '趣味制作',
      color: '#ec4899',
      isExternal: true
    },
    {
      title: '意识重构模拟器',
      desc: '全卡池超弦体抽卡概率模拟与重构结晶消耗估算',
      url: 'https://wiki.biligame.com/klbq/%E6%84%8F%E8%AF%86%E9%87%8D%E6%9E%84%E6%A8%A1%E6%8B%9F%E5%99%A8',
      icon: 'lucide:dices',
      tag: '抽卡模拟',
      color: '#f59e0b'
    },
    {
      title: '玩家卡拼接',
      desc: '超弦体个性化展示名片、底板与徽章战绩拼接器',
      url: 'https://wiki.biligame.com/klbq/%E7%8E%A9%E5%AE%B6%E5%8D%A1%E6%8B%BC%E6%8E%A5',
      icon: 'lucide:id-card',
      tag: '名片拼装',
      color: '#3b82f6'
    },
    {
      title: 'GuGuTalk',
      desc: '卡拉彼丘社区引航者专属即时交流与互动工具',
      url: 'http://gugutack.com/index.html',
      icon: 'lucide:message-circle',
      tag: '社区交流',
      color: '#10b981',
      isExternal: true
    },
    {
      title: '同人塔防游戏',
      desc: '以卡丘角色和技能为机制的社区原创同人网页防守游戏',
      url: 'https://wiki.biligame.com/klbq/%E5%90%8C%E4%BA%BA%E5%A1%94%E9%98%B2%E6%B8%B8%E6%88%8F',
      icon: 'lucide:castle',
      tag: '同人小游戏',
      color: '#ef4444'
    },
    {
      title: '连连看小游戏',
      desc: '超弦体萌系头像与武器技能图标休闲消除小游戏',
      url: 'https://wiki.biligame.com/klbq/%E8%BF%9E%E8%BF%9E%E7%9C%8B',
      icon: 'lucide:grid-2x2',
      tag: '休闲消除',
      color: '#06b6d4'
    },
    {
      title: 'WIKI 手机客户端',
      desc: '卡拉彼丘 BWiki 官方移动端应用程序安装与使用指引',
      url: 'https://wiki.biligame.com/klbq/WIKI_APP',
      icon: 'lucide:smartphone',
      tag: '官方指南',
      color: '#64748b'
    }
  ]
};

/** 常用链接：7 大官方媒体与平台渠道 */
export const OFFICIAL_CHANNELS_MODAL: CollectionModalData = {
  id: 'official-channels',
  title: '官方渠道矩阵',
  subtitle: '《卡拉彼丘》PC端、移动端与社交媒体官方认证入口',
  icon: 'lucide:link-2',
  color: '#2563eb',
  footerTip: '全部为游戏开发运营方官方认证链接，可放心访问',
  items: [
    {
      title: 'PC端官网（国服）',
      desc: '游戏官方网站、版本资讯公告与 PC 客户端下载',
      url: 'https://klbq.idreamsky.com',
      icon: 'lucide:monitor',
      tag: '国服官网',
      color: '#2563eb',
      isExternal: true
    },
    {
      title: 'PC端国际服官网 (Strinova)',
      desc: '全球国际服官网、Steam 社区与多语种赛事信息',
      url: 'https://www.strinova.com',
      icon: 'lucide:globe',
      tag: '国际服',
      color: '#3b82f6',
      isExternal: true
    },
    {
      title: '移动端手游官网',
      desc: '《卡拉彼丘》手游官方预约、资格获取与移动端最新情报',
      url: 'https://klbqm.idreamsky.com',
      icon: 'lucide:smartphone',
      tag: '手游官网',
      color: '#10b981',
      isExternal: true
    },
    {
      title: 'PC端官方 B站账号',
      desc: '卡拉彼丘官方哔哩哔哩动态、版本 PV 与重磅更新发布',
      url: 'https://space.bilibili.com/660091334',
      icon: 'lucide:tv-2',
      tag: 'B站官方',
      color: '#fb7299',
      isExternal: true
    },
    {
      title: '移动端官方 B站账号',
      desc: '卡拉彼丘手游官方 B站运营、测试爆料与机型实机演示',
      url: 'https://space.bilibili.com/3493286507448436',
      icon: 'lucide:tv-2',
      tag: 'B站官方',
      color: '#fb7299',
      isExternal: true
    },
    {
      title: '宣传车官方 B站账号',
      desc: '卡拉彼丘官方宣传车小队动态、粉丝二创与活动播报',
      url: 'https://space.bilibili.com/1304841421',
      icon: 'lucide:truck',
      tag: '官方小队',
      color: '#f59e0b',
      isExternal: true
    },
    {
      title: '制作组面对面官方账号',
      desc: 'Day1 制作组面对面直通车、策划答疑与战斗机制前瞻',
      url: 'https://space.bilibili.com/3537118838131284',
      icon: 'lucide:wrench',
      tag: '策划团队',
      color: '#8b5cf6',
      isExternal: true
    }
  ]
};

/** 常用筛选表：5 大全站分类筛选工具 */
export const FILTER_TOOLS_MODAL: CollectionModalData = {
  id: 'filter-tools',
  title: '全站筛选图鉴',
  subtitle: '时装外观、武器皮肤、功能道具与卡组筛选检索工具',
  icon: 'lucide:filter',
  color: '#f59e0b',
  footerTip: '直达 Wiki 各维度的动态筛选矩阵页面，支持多条件筛选',
  items: [
    {
      title: '角色时装筛选',
      desc: '全部超弦体传说/非凡/卓越品质时装、特效与获取途径一览',
      url: 'https://wiki.biligame.com/klbq/%E8%A7%92%E8%89%B2%E6%97%B6%E8%A3%85%E7%AD%9B%E9%80%89',
      icon: 'lucide:sparkles',
      tag: '时装图鉴',
      color: '#ec4899'
    },
    {
      title: '武器外观筛选',
      desc: '各枪械金色/紫色枪皮展示、换色模型与动效图鉴',
      url: 'https://wiki.biligame.com/klbq/%E6%AD%A6%E5%99%A8%E5%A4%96%E8%A7%82%E7%AD%9B%E9%80%89',
      icon: 'lucide:palette',
      tag: '枪械皮肤',
      color: '#f59e0b'
    },
    {
      title: '功能道具筛选表',
      desc: '晶核、芯片、升级耗材、誓约礼物等全游戏道具资料库',
      url: 'https://wiki.biligame.com/klbq/%E5%8A%9F%E8%83%BD%E9%81%93%E5%85%B7%E7%AD%9B%E9%80%89%E8%A1%A8',
      icon: 'lucide:package-search',
      tag: '道具清单',
      color: '#3b82f6'
    },
    {
      title: 'PC端晶源感染卡牌',
      desc: 'PC端生化感染模式全部感染体与人类增益卡牌效果表',
      url: 'https://wiki.biligame.com/klbq/%E6%88%98%E6%96%97%E6%A8%A1%E5%BC%8F/%E6%99%B6%E6%BA%90%E6%84%9F%E6%9F%93/PC%E7%AB%AF%E5%8D%A1%E7%89%8C%E7%AD%9B%E9%80%89',
      icon: 'lucide:layers',
      tag: '生化卡牌',
      color: '#a855f7'
    },
    {
      title: '移动端晶源感染卡牌',
      desc: '手游端晶源感染模式专属卡组配置与技能加点数值表',
      url: 'https://wiki.biligame.com/klbq/%E6%88%98%E6%96%97%E6%A8%A1%E5%BC%8F/%E6%99%B6%E6%BA%90%E6%84%9F%E6%9F%93/%E7%A7%BB%E5%8A%A8%E7%AB%AF%E5%8D%A1%E7%89%8C%E7%AD%9B%E9%80%89',
      icon: 'lucide:smartphone',
      tag: '移动专属',
      color: '#10b981'
    }
  ]
};

/** 游戏延伸：7 大官方衍生内容与周边汇总 */
export const GAME_EXTENSIONS_MODAL: CollectionModalData = {
  id: 'game-extensions',
  title: '游戏延伸与周边',
  subtitle: '官方音乐原声、壁纸画廊、表情包、四格漫画与周边礼包',
  icon: 'lucide:sparkles',
  color: '#0891b2',
  footerTip: '卡拉彼丘官方衍生文化内容与创作者周边资料汇编',
  items: [
    {
      title: 'BGM 官方原声音频',
      desc: '游戏内全部战斗音乐、局内背景音与主题曲在线试听',
      url: 'https://wiki.biligame.com/klbq/BGM',
      icon: 'lucide:music',
      tag: '原声音频',
      color: '#ec4899'
    },
    {
      title: '壁纸画廊',
      desc: '170+ 高清官方宣发插画、版本主视觉与电脑壁纸',
      url: 'https://wiki.biligame.com/klbq/%E5%A3%81%E7%BA%B8',
      icon: 'lucide:image',
      tag: '高清壁纸',
      color: '#3b82f6'
    },
    {
      title: '官方表情包',
      desc: '全超弦体 Q 版萌系表情、聊天表情包切图与动图下载',
      url: 'https://wiki.biligame.com/klbq/%E8%A1%A8%E6%83%85%E5%8C%85',
      icon: 'lucide:smile',
      tag: 'Q版表情',
      color: '#f59e0b'
    },
    {
      title: '官方四格漫画',
      desc: '卡丘基地搞笑日常、四格条漫与超弦体生活小剧场',
      url: 'https://wiki.biligame.com/klbq/%E5%AE%98%E6%96%B9%E5%9B%9B%E6%A0%BC%E6%BC%AB%E7%94%BB',
      icon: 'lucide:book-image',
      tag: '趣味条漫',
      color: '#10b981'
    },
    {
      title: '商业与IP跨界联动',
      desc: '历代官方品牌跨界合作、联动活动与专属涂装回顾',
      url: 'https://wiki.biligame.com/klbq/%E8%81%94%E5%8A%A8',
      icon: 'lucide:handshake',
      tag: '品牌联动',
      color: '#8b5cf6'
    },
    {
      title: '相关周边资料一览',
      desc: '官方正版手办、立牌、吧唧与实体周边档案集',
      url: 'https://wiki.biligame.com/klbq/%E7%9B%B8%E5%85%B3%E5%91%A8%E8%BE%B9',
      icon: 'lucide:shopping-bag',
      tag: '实体周边',
      color: '#f43f5e'
    },
    {
      title: '福利兑换码汇总',
      desc: '全网最新可用兑换码、礼包码与活动 CDKEY 领取说明',
      url: 'https://wiki.biligame.com/klbq/%E5%85%91%E6%8D%A2%E7%A0%81',
      icon: 'lucide:gift',
      tag: '礼包兑换',
      color: '#eab308'
    }
  ]
};
