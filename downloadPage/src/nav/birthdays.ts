/**
 * 角色生日数据与倒计时计算
 * 对照官方资料库与移动端 CharacterBirthdays
 */

export interface CharacterBirthday {
  name: string;
  month: number;
  day: number;
  dateText: string;
}

export const CHARACTER_BIRTHDAYS: CharacterBirthday[] = [
  { name: '令', month: 1, day: 5, dateText: '01-05' },
  { name: '千代', month: 1, day: 10, dateText: '01-10' },
  { name: '香奈美', month: 1, day: 27, dateText: '01-27' },
  { name: '芙拉薇娅', month: 2, day: 14, dateText: '02-14' },
  { name: '加拉蒂亚', month: 2, day: 22, dateText: '02-22' },
  { name: '玛德蕾娜', month: 3, day: 7, dateText: '03-07' },
  { name: '忧雾', month: 3, day: 22, dateText: '03-22' },
  { name: '米雪儿', month: 3, day: 25, dateText: '03-25' },
  { name: '玛拉', month: 4, day: 1, dateText: '04-01' },
  { name: '绯莎', month: 5, day: 10, dateText: '05-10' },
  { name: '伊薇特', month: 5, day: 23, dateText: '05-23' },
  { name: '莉莉丝', month: 6, day: 6, dateText: '06-06' },
  { name: '官博娘', month: 6, day: 6, dateText: '06-06' },
  { name: '汐', month: 6, day: 21, dateText: '06-21' },
  { name: '信', month: 7, day: 11, dateText: '07-11' },
  { name: '奥黛丽', month: 7, day: 29, dateText: '07-29' },
  { name: '明', month: 8, day: 15, dateText: '08-15' },
  { name: '梅瑞狄斯', month: 8, day: 28, dateText: '08-28' },
  { name: '珐格兰丝', month: 9, day: 10, dateText: '09-10' },
  { name: '星绘', month: 9, day: 26, dateText: '09-26' },
  { name: '蕾欧娜', month: 10, day: 10, dateText: '10-10' },
  { name: '诺诺', month: 10, day: 24, dateText: '10-24' },
  { name: '拉薇', month: 10, day: 29, dateText: '10-29' },
  { name: '艾卡', month: 11, day: 11, dateText: '11-11' },
  { name: '心夏', month: 12, day: 12, dateText: '12-12' },
  { name: '宣传车', month: 12, day: 18, dateText: '12-18' },
  { name: '白墨', month: 12, day: 20, dateText: '12-20' }
];

export const BIRTHDAY_MAP: Record<string, CharacterBirthday> = Object.fromEntries(
  CHARACTER_BIRTHDAYS.flatMap(b => [
    [b.name, b],
    [`${b.name}·李`, b],
    [`${b.name}·格罗夫`, b],
    [`${b.name}·利里`, b]
  ])
);

export interface BirthdayCountdown {
  birthday: CharacterBirthday;
  daysRemaining: number;
  isToday: boolean;
  statusText: string;
}

/** 获取指定生日距离当前日期的天数（支持跨年） */
export function getDaysUntilBirthday(birthday: CharacterBirthday, now = new Date()): number {
  const currentYear = now.getFullYear();
  const targetDate = new Date(currentYear, birthday.month - 1, birthday.day);

  // 当日归一化计算（忽略时分秒）
  const nowDateOnly = new Date(currentYear, now.getMonth(), now.getDate());
  let diffTime = targetDate.getTime() - nowDateOnly.getTime();

  // 如果今年已经过了，算到明年该日
  if (diffTime < 0) {
    const nextYearDate = new Date(currentYear + 1, birthday.month - 1, birthday.day);
    diffTime = nextYearDate.getTime() - nowDateOnly.getTime();
  }

  return Math.round(diffTime / (1000 * 60 * 60 * 24));
}

/** 获取最近的寿星与生日倒计时清单 */
export function getUpcomingBirthdays(limit = 4, now = new Date()): BirthdayCountdown[] {
  const list = CHARACTER_BIRTHDAYS.map(b => {
    const days = getDaysUntilBirthday(b, now);
    const isToday = days === 0;
    const statusText = isToday
      ? '今天生日！'
      : days === 1
        ? '明天'
        : `${days}天后`;
    return {
      birthday: b,
      daysRemaining: days,
      isToday,
      statusText
    };
  });

  return list.sort((a, b) => a.daysRemaining - b.daysRemaining).slice(0, limit);
}
