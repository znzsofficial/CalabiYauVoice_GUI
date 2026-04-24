package com.nekolaska.calabiyau.core.cache

import com.nekolaska.calabiyau.feature.wiki.announcement.api.AnnouncementApi
import com.nekolaska.calabiyau.feature.wiki.balance.api.BalanceDataApi
import com.nekolaska.calabiyau.feature.wiki.bio.api.BioCardApi
import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.character.costume.CostumeFilterApi
import com.nekolaska.calabiyau.feature.wiki.gallery.api.GalleryApi
import com.nekolaska.calabiyau.feature.wiki.game.api.GameModeApi
import com.nekolaska.calabiyau.feature.wiki.map.api.MapListApi
import com.nekolaska.calabiyau.feature.wiki.decoration.api.PlayerDecorationApi
import com.nekolaska.calabiyau.feature.wiki.gallery.WallpaperApi
import com.nekolaska.calabiyau.feature.weapon.list.WeaponListApi
import com.nekolaska.calabiyau.feature.weapon.skin.WeaponSkinFilterApi

object AppCacheBootstrap {

    fun ensureRegistered() {
        WeaponListApi
        CharacterListApi
        MapListApi
        CostumeFilterApi
        WeaponSkinFilterApi
        BioCardApi
        AnnouncementApi
        GameModeApi
        WallpaperApi
        BalanceDataApi
        GalleryApi
        PlayerDecorationApi
    }
}