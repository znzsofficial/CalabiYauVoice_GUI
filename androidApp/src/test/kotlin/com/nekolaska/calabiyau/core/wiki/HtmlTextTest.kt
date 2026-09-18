package com.nekolaska.calabiyau.core.wiki

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTextTest {
    @Test fun separatesBlocksAndNormalizesEntities() {
        assertEquals("第一项\n第二项\n甲 乙\n丙", HtmlText.clean(
            "<div>第一项</div><div>第二项</div><ul><li>甲&nbsp;乙</li><li>丙&#xfffc;</li></ul><script>bad()</script>"))
    }
}
