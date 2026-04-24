package com.nekolaska.calabiyau.feature.wiki.navigation.model

data class NavSection(
    val title: String,
    val items: List<NavItem>
)

data class NavItem(
    val title: String,
    val url: String?,
    val children: List<NavItem> = emptyList()
)
