"""Report the live CI result. A cached/skipped run is not proof of current Wiki compatibility."""
import json
import os
from pathlib import Path
from xml.etree import ElementTree as ET


def report(build, gradle_ok):
    prefix = 'com.nekolaska.calabiyau.feature.wiki.'
    tests = [case for path in (build / 'test-results/testDebugUnitTest').glob('TEST-*.xml')
             for case in ET.parse(path).getroot().iter('testcase')]
    failures = [(case.get('name'), exc) for case in tests for exc in case if exc.tag in ('failure', 'error')]
    if failures:
        limited_only = all(exc.get('type') == prefix + 'WikiAccessLimited' for _, exc in failures)
        label = 'Wiki 拒绝或限制访问，未完成兼容性检查' if limited_only else '测试失败，请检查页面、网络或解析器'
        details = [f"- `{name}`: {exc.get('message', '').splitlines()[0][:400]}" for name, exc in failures if exc.get('message')]
        return False, not limited_only, '\n'.join([label, *details])
    if not gradle_ok:
        return False, False, 'Gradle 未成功完成；请检查构建日志。没有证据表明 Wiki 页面不兼容。'

    live = [case for case in tests if case.get('classname') == prefix + 'LiveWikiSnapshotTest'
            and case.get('name', '').endswith('WhenEnabled')]
    required = {'fetchesAndParsesListedActivityDetailWhenEnabled', 'fetchesAndParsesExtractionArmorWhenEnabled'}
    if not required.issubset({case.get('name') for case in live}) or any(case.find('skipped') is not None for case in live):
        return False, False, '联网用例缺失或被跳过；本次不能算作通过。'
    lines = [f'本次执行了 {len(live)} 项联网测试。']
    for name in ('activity-detail.json', 'extraction-armor.json'):
        path = build / 'live-wiki' / name
        if not path.is_file():
            return False, False, f'未找到本次实抓响应 `{name}`；请检查测试任务是否误用了缓存。'
        title = json.loads(path.read_bytes())['parse']['title']
        lines.append(f'- {title}（{path.stat().st_size:,} 字节）')
    return True, False, '\n'.join(lines)


if __name__ == '__main__':
    ok, issue, message = report(Path('androidApp/build'), os.environ['SMOKE_OUTCOME'] == 'success')
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as summary:
        print('### Wiki 实时检查', message, sep='\n\n', file=summary)
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        print('issue=' + str(issue).lower(), file=output)
    print(message)
    raise SystemExit(0 if ok else 1)
