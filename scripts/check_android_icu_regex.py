"""Compile Kotlin regex literals with the host ICU, without Gradle, network or pip.

    python scripts/check_android_icu_regex.py [--verbose] [file.kt ...]

Defaults to the three production Kotlin roots in ROOTS (including untracked files).
Recognizes direct Regex(...) and receiver.toRegex(...) calls by spelling, without
Kotlin type resolution. Checks regular and raw triple-quoted string literals and
explicit RegexOption values (also setOf). Lists recognized sites with unevaluated
expressions/options as SKIP. Aliases, wrappers, code inside string templates,
other regex APIs and generated sources are outside the inventory.

Requires Python 3.9+ and Windows icu.dll or Linux libicui18n/libicuuc. Checks
syntax with the installed ICU version, not Android runtime or matching behavior.
Exit: 0 = no compiled literal failed (skips allowed), 1 = ICU rejected a literal,
2 = tool/input error. --verbose also lists each successful site.
"""

import argparse
import collections
import ctypes as c
import ctypes.util
from pathlib import Path
import re
import sys
from typing import NamedTuple


REPO = Path(__file__).resolve().parents[1]
ROOTS = ('androidApp/src/main/kotlin', 'shared/src/commonMain/kotlin', 'shared/src/androidMain/kotlin')


class ParseError(c.Structure):
    _fields_ = [('line', c.c_int32), ('offset', c.c_int32),
                ('preContext', c.c_uint16 * 16), ('postContext', c.c_uint16 * 16)]


class ICU:
    def __init__(self):
        if sys.platform == 'win32':
            i18n = common = c.CDLL('icu.dll')
            suffix = ''
        elif sys.platform.startswith('linux'):
            i18n_name = ctypes.util.find_library('icui18n')
            common_name = ctypes.util.find_library('icuuc')
            if not (i18n_name and common_name):
                raise OSError('libicui18n and libicuuc are required')
            i18n, common = c.CDLL(i18n_name), c.CDLL(common_name)
            # Linux distro libraries normally export uregex_open_72, etc.
            major = re.search(r'\.so\.(\d+)', i18n_name)
            suffix = '_' + major[1] if major and not hasattr(i18n, 'uregex_open') else ''
        else:
            raise OSError('supported ICU hosts: Windows and Linux')

        def bind(lib, name, result, *args):
            fn = getattr(lib, name + suffix)
            fn.restype, fn.argtypes = result, args
            return fn

        self.open = bind(i18n, 'uregex_open', c.c_void_p, c.POINTER(c.c_uint16), c.c_int32,
                         c.c_uint32, c.POINTER(ParseError), c.POINTER(c.c_int32))
        self.close = bind(i18n, 'uregex_close', None, c.c_void_p)
        self.error_name = bind(common, 'u_errorName', c.c_char_p, c.c_int32)
        version = (c.c_uint8 * 4)()
        bind(common, 'u_getVersion', None, c.POINTER(c.c_uint8))(version)
        self.version = '.'.join(map(str, version))

    def check(self, pattern, flags=0):
        encoding = 'utf-16-le' if sys.byteorder == 'little' else 'utf-16-be'
        data = pattern.encode(encoding, 'surrogatepass')
        length = len(data) // 2
        buf = (c.c_uint16 * (length + 1)).from_buffer_copy(data + b'\0\0')
        error, status = ParseError(), c.c_int32()
        # Android enables UREGEX_ERROR_ON_UNKNOWN_ESCAPES. Native ICU needs -1
        # for an empty NUL-terminated pattern; use explicit lengths for embedded NUL.
        handle = self.open(buf, length or -1, flags | 512, c.byref(error), c.byref(status))
        if handle:
            self.close(handle)
        if status.value > 0:  # negative values are ICU success-with-warning
            return f'{self.error_name(status).decode()} (ICU line={error.line}, offset={error.offset})'
        if not handle:
            raise RuntimeError('uregex_open returned no handle or error')
        return None


# Small lexer, not a Kotlin parser. Opaque comments/strings prevent documentation,
# JavaScript in raw strings, or quotes inside ${...} from becoming phantom calls.
TOKEN = re.compile(r'''\s+|//[^\n]*|/\*|\$*"""|\$*"|'(?:\\.|[^'\\\n])*'|`[^`\n]+`|\w+|.''', re.S)
COMMENT = re.compile(r'/\*|\*/')
NORMAL = re.compile(r'\\.|"|\$+(?=\{|[^\W\d]|`)', re.S)
RAW = re.compile(r'"{3,}|\$+(?=\{|[^\W\d]|`)')


class Token(NamedTuple):
    text: str
    start: int
    dynamic: bool = False


def token_at(src, i):
    match = TOKEN.match(src, i)
    word, end = match[0], match.end()
    dynamic = False
    if word == '/*':
        depth = 1
        for m in COMMENT.finditer(src, end):
            depth += 1 if m[0] == '/*' else -1
            if not depth:
                end = m.end()
                break
        else:
            raise ValueError('unterminated block comment')
    elif word.lstrip('$') in ('"', '"""'):
        delimiter = word.lstrip('$')
        dollars = len(word) - len(delimiter)
        dynamic = bool(dollars)  # do not evaluate multi-dollar strings
        event = RAW if delimiter == '"""' else NORMAL
        while True:
            m = event.search(src, end)
            if not m:
                raise ValueError('unterminated string literal')
            end = m.end()
            if m[0].startswith('"'):
                break
            if m[0].startswith('$') and len(m[0]) >= max(1, dollars):
                dynamic = True
                if src[end] == '{':
                    depth, end = 1, end + 1
                    while depth and end < len(src):
                        t = token_at(src, end)
                        depth += (t.text == '{') - (t.text == '}')
                        end += len(t.text)
    return Token(src[i:end], i, dynamic)


def tokens(src):
    pos = 0
    while pos < len(src):
        t = token_at(src, pos)
        pos += len(t.text)
        if not (t.text.isspace() or t.text.startswith(('/*', '//'))):
            yield t


ESCAPES = {'n': '\n', 'r': '\r', 't': '\t', 'b': '\b', '\\': '\\', "'": "'", '"': '"', '$': '$'}
ESCAPE = re.compile(r'\\(?:u([0-9a-fA-F]{4})|(.))', re.S)


def decode_literal(t):
    if t.dynamic:
        raise ValueError('interpolated or multi-dollar literal')
    if t.text.startswith('"""'):
        return t.text[3:-3]
    if not t.text.startswith('"'):
        raise ValueError('non-literal pattern')

    def unescape(m):
        if m[1]:
            return chr(int(m[1], 16))
        if m[2] not in ESCAPES:
            raise ValueError(f'unsupported Kotlin escape {m[0]!r}')
        return ESCAPES[m[2]]

    return ESCAPE.sub(unescape, t.text[1:-1])


def arguments(ts, start):
    """Split a call after its opening paren, retaining each top-level argument."""
    args, current, depth = [], [], 0
    for i in range(start, len(ts)):
        t = ts[i]
        if not depth and t.text == ')':
            return args + ([current] if current else [])
        if not depth and t.text == ',':
            args.append(current)
            current = []
        else:
            current.append(t)
            depth += (t.text in ('(', '[', '{')) - (t.text in (')', ']', '}'))
    raise ValueError('unterminated regex call')


FLAGS = {'UNIX_LINES': 1, 'IGNORE_CASE': 2, 'COMMENTS': 4, 'MULTILINE': 8,
         'LITERAL': 16, 'DOT_MATCHES_ALL': 32}


def bind_arguments(args, names):
    """Normalize positional and named arguments to the pattern/options slots."""
    if len(args) > len(names):
        raise ValueError('unsupported arguments')
    bound = {}
    for index, arg in enumerate(args):
        if len(arg) >= 2 and arg[1].text == '=':
            name = arg[0].text
            name = 'options' if name == 'option' else name
            arg = arg[2:]
        else:
            name = names[index]
        if name not in names or name in bound or not arg:
            raise ValueError('unsupported argument: ' + name)
        bound[name] = arg
    return bound


def options(arg):
    if arg is None:
        return 0
    expr = ''.join(t.text for t in arg)
    parts = expr[6:-1].rstrip(',').split(',') if expr.startswith('setOf(') and expr.endswith(')') else [expr]
    flags = 0
    for part in parts:
        name = part.removeprefix('RegexOption.')
        if name == part or name not in FLAGS:
            raise ValueError('unsupported regex options: ' + expr)
        flags |= FLAGS[name]
    return flags


def sites(src):
    ts = list(tokens(src)) if 'Regex' in src else []
    for i, t in enumerate(ts[:-1]):
        if t.text not in ('Regex', 'toRegex') or ts[i+1].text != '(':
            continue
        line = src.count('\n', 0, t.start) + 1
        args = arguments(ts, i+2)
        try:
            if t.text == 'Regex':
                bound = bind_arguments(args, ('pattern', 'options'))
                pattern = bound.get('pattern', [])
                if len(pattern) != 1:
                    raise ValueError('pattern expression (not one literal)')
                literal = pattern[0]
            elif i >= 2 and ts[i-1].text == '.':
                bound = bind_arguments(args, ('options',))
                literal = ts[i-2]
            else:
                raise ValueError('no literal receiver')
            yield line, decode_literal(literal), options(bound.get('options')), None
        except ValueError as e:
            yield line, None, 0, str(e)


def audit(icu, label, files, verbose):
    counts = collections.Counter()
    for file in files:
        name = file.relative_to(REPO).as_posix() if file.is_relative_to(REPO) else str(file)
        try:
            for line, pattern, flags, skip in sites(file.read_text(encoding='utf-8-sig')):
                error = None if skip else icu.check(pattern, flags)
                kind = 'SKIP' if skip else 'FAIL' if error else 'OK'
                counts[kind] += 1
                if skip or error or verbose:
                    detail = skip or f'{error or "compiled"}: {ascii(pattern)}'
                    print(f'{name}:{line}: {kind} {detail}')
        except ValueError as e:
            raise ValueError(f'{name}: {e}') from e
    print(f'{label}: files={len(files)} ok={counts["OK"]} failed={counts["FAIL"]} skipped={counts["SKIP"]}')
    return counts['FAIL']


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('files', type=Path, nargs='*')
    parser.add_argument('--verbose', action='store_true')
    args = parser.parse_args()
    try:
        icu = ICU()
        print('Host ICU', icu.version)
        if args.files:
            return int(bool(audit(icu, 'selected files', [f.resolve() for f in args.files], args.verbose)))
        failed = 0
        for root in ROOTS:
            files = sorted((REPO / root).rglob('*.kt'))
            if not files:
                raise ValueError('missing or empty production Kotlin root: ' + root)
            failed += audit(icu, root, files, args.verbose)
        return int(bool(failed))
    except (OSError, AttributeError, RuntimeError, ValueError) as e:
        parser.exit(2, f'ICU regex audit: {e}\n')


if __name__ == '__main__':
    sys.exit(main())
