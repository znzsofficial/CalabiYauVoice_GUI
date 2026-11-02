"""Capture current weapon catalogs/pages for the optional offline Kotlin audit.

No parallel requests. A 6.5-second quiet period follows every request, including errors.
Stops on HTTP errors, WAF HTML or API errors; rerun with --reuse to keep saved captures.
Full pages are local artifacts, not fixtures to commit wholesale.
"""
import argparse
import json
import pathlib
import time
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    parser.add_argument('--proxy', help='Optional local HTTP(S) proxy URL; otherwise uses system environment')
    parser.add_argument('--reuse', action='store_true', help='Reuse saved catalogs and pages instead of refetching')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    opener = urllib.request.build_opener(
        urllib.request.ProxyHandler({'http': args.proxy, 'https': args.proxy})
    ) if args.proxy else urllib.request.build_opener()

    def save(path, value):
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')

    def request(params):
        req = urllib.request.Request(
            'https://wiki.biligame.com/klbq/api.php?' + urllib.parse.urlencode(params),
            headers={
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0 Safari/537.36',
                'Referer': 'https://wiki.biligame.com/klbq/'
            }
        )
        try:
            with opener.open(req, timeout=45) as response:
                raw = response.read()
            if not raw.lstrip().startswith(b'{'):
                raise RuntimeError('Non-JSON / possible WAF; stop without retrying')
            value = json.loads(raw)
            if value.get('error'):
                raise RuntimeError('Wiki API error: ' + str(value['error']))
            return value
        finally:
            time.sleep(6.5)

    groups = {}
    for kind, category in [('tactical', '战术道具'), ('primary', '主武器'), ('secondary', '副武器'), ('melee', '近战武器')]:
        path = args.output / (kind + '-catalog.json')
        value = json.loads(path.read_text(encoding='utf-8')) if args.reuse and path.exists() else request({
            'action': 'ask', 'query': '[[分类:' + category + ']]|limit=100', 'format': 'json'
        })
        save(path, value)
        names = list(value.get('query', {}).get('results', {}))
        if not names or len(names) >= 100:
            raise RuntimeError(category + ': empty/truncated catalog; inspect before fetching pages')
        groups[kind] = names
    if sum(map(len, groups.values())) > 65:
        raise RuntimeError('More than 65 pages; inspect the scope before making more requests')
    report = {'groups': groups, 'pages': [], 'blocked': None}
    manifest = args.output / 'manifest.json'
    save(manifest, report)
    for kind, names in groups.items():
        for name in names:
            try:
                if any(char in name for char in '/\\:*?"<>|') or name in ('.', '..'):
                    raise RuntimeError('Unexpected page/file name: ' + name)
                path = args.output / (name + '.json')
                value = json.loads(path.read_text(encoding='utf-8')) if args.reuse and path.exists() else request({
                    'action': 'parse', 'page': name, 'prop': 'wikitext|text', 'format': 'json', 'redirects': '1'
                })
                save(path, value)
                parsed = value.get('parse')
                if not parsed:
                    raise RuntimeError('No parse payload for ' + name)
                report['pages'].append({'name': name, 'kind': kind, 'title': parsed.get('title')})
                print(kind + ': ' + name, flush=True)
            except Exception as error:
                report['blocked'] = {'page': name, 'error': str(error)}
                save(manifest, report)
                raise
            save(manifest, report)
    print('Saved ' + str(len(report['pages'])) + ' pages in ' + str(args.output), flush=True)


if __name__ == '__main__':
    main()
