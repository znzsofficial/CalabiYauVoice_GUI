package com.nekolaska.calabiyau.feature.wiki.hub

import kotlin.test.*

class HubTransitionTargetTest {
    @Test fun outgoingWeaponUserActionCannotTruncateAReplacementPage() {
        val character = WikiRoute.CharDetail("甲", null); val weapon = WikiRoute.WeaponDetail("测试")
        val stack = listOf(WikiRoute.Home, character, weapon)
        val page = HubTransitionTarget(weapon, 2)
        assertEquals(stack.take(2), page.openWeaponUser(stack, false, "甲") { error("already in stack") })
        assertSame(stack, page.openWeaponUser(stack, true, "甲") { null })
        val replacement = stack + WikiRoute.WeaponSkins("测试")
        assertSame(replacement, page.openWeaponUser(replacement, false, "甲") { null })
    }
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
