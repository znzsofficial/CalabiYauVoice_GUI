"""Run offline: python -B -m unittest discover -s scripts -p test_check_android_icu_regex.py"""

import unittest

from check_android_icu_regex import ICU, sites


class AndroidIcuAuditTest(unittest.TestCase):
    def test_activity_wikitable_regression_against_real_icu(self):
        # Exact pattern from the failing ActivityDetailParser before the closing } was escaped.
        old = r'''Regex("""(?ms)^\{\|[^\r\n]*\bklbqtable\b[^\r\n]*\r?\n(.*?)^\|}""")'''
        fixed = old.replace(r'\|}', r'\|\}')
        icu = ICU()
        (line, bad, flags, skip), (_, good, _, _) = sites(old + '\n' + fixed)
        self.assertEqual((line, flags, skip), (1, 0, None))
        self.assertIn('U_REGEX_RULE_SYNTAX', icu.check(bad, flags))
        self.assertIsNone(icu.check(good, flags))

    def test_comments_templates_flags_and_kotlin_escapes(self):
        src = r'''/* nested /* Regex("}") */ comment */
            val example = """example: "}".toRegex()"""
            val templated = Regex("${if (key == "x") "}" else "{"}")
            val concat = Regex("prefix" + suffix)
            val unknownFlags = Regex("}", options)
            val escaped = "\\d+\t\u4E2D\$".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            val anchor = Regex("""(?:x|$)""") // Regex("}")
            val literal = Regex("}", RegexOption.LITERAL)
            val variable = pattern.toRegex()
        '''
        found = list(sites(src))
        self.assertEqual([line for line, _, _, skip in found if skip], [3, 4, 5, 9])
        checked = [(line, pattern, flags) for line, pattern, flags, skip in found if not skip]
        self.assertEqual(checked, [(6, '\\d+\t中$', 10), (7, '(?:x|$)', 0), (8, '}', 16)])
        icu = ICU()
        for _, pattern, flags in checked:
            self.assertIsNone(icu.check(pattern, flags))

    def test_named_arguments_are_checked_in_kotlin_parameter_order(self):
        src = '''Regex(pattern = "}")
            Regex(option = RegexOption.LITERAL, pattern = "}")
            Regex("x", options = setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
            "}".toRegex(option = RegexOption.LITERAL)
            "x".toRegex(options = setOf(RegexOption.MULTILINE))
            Regex(pattern = prefix + "}", option = RegexOption.LITERAL)
        '''
        *checked, dynamic = sites(src)
        self.assertEqual([(pattern, flags, skip) for _, pattern, flags, skip in checked],
                         [('}', 0, None), ('}', 16, None), ('x', 10, None), ('}', 16, None), ('x', 8, None)])
        self.assertIsNotNone(dynamic[3])
        icu = ICU()
        self.assertIn('U_REGEX_RULE_SYNTAX', icu.check(checked[0][1], checked[0][2]))
        for _, pattern, flags, _ in checked[1:]:
            self.assertIsNone(icu.check(pattern, flags))


if __name__ == '__main__':
    unittest.main()
