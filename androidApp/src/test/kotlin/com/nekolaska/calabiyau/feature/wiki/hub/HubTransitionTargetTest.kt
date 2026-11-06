package com.nekolaska.calabiyau.feature.wiki.hub

import kotlin.test.*

class HubTransitionTargetTest {
    @Test fun interruptedPushAndPopKeepTheirOwnSegmentDirection() {
        val home = HubTransitionTarget(WikiRoute.Home, 0)
        val list = HubTransitionTarget(WikiRoute.Characters, 1)
        val detail = HubTransitionTarget(WikiRoute.CharDetail("测试", null), 2)
        assertFalse(list.isPopFrom(home))
        assertTrue(home.isPopFrom(list))
        assertFalse(detail.isPopFrom(list))
        assertTrue(list.isPopFrom(detail))
        // Later pushes do not change the already-created return segment.
        assertTrue(home.isPopFrom(list))
    }
}
