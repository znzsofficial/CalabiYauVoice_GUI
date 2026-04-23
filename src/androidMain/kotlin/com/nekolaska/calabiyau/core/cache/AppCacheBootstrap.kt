package com.nekolaska.calabiyau.core.cache

import com.nekolaska.calabiyau.feature.wiki.announcement.AnnouncementApi
import com.nekolaska.calabiyau.feature.wiki.balance.BalanceDataApi
import com.nekolaska.calabiyau.feature.wiki.bio.BioCardApi
import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.character.costume.CostumeFilterApi
import com.nekolaska.calabiyau.feature.wiki.gallery.GalleryApi
import com.nekolaska.calabiyau.feature.wiki.game.GameModeApi
import com.nekolaska.calabiyau.feature.wiki.map.MapListApi
import com.nekolaska.calabiyau.feature.wiki.decoration.PlayerDecorationApi
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