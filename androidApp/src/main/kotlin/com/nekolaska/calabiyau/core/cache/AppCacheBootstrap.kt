package com.nekolaska.calabiyau.core.cache

import com.nekolaska.calabiyau.feature.wiki.achievement.api.AchievementApi
import com.nekolaska.calabiyau.feature.wiki.bgm.api.BgmApi
import com.nekolaska.calabiyau.feature.wiki.collaboration.api.CollaborationApi
import com.nekolaska.calabiyau.feature.wiki.history.api.GameHistoryApi
import com.nekolaska.calabiyau.feature.wiki.tips.api.GameTipsApi
import com.nekolaska.calabiyau.feature.wiki.imprint.api.ImprintApi
import com.nekolaska.calabiyau.feature.wiki.announcement.api.AnnouncementApi
import com.nekolaska.calabiyau.feature.wiki.balance.api.BalanceDataApi
import com.nekolaska.calabiyau.feature.wiki.bio.api.BioCardApi
import com.nekolaska.calabiyau.feature.character.list.CharacterListApi
import com.nekolaska.calabiyau.feature.character.costume.CostumeFilterApi
import com.nekolaska.calabiyau.feature.wiki.item.api.ItemCatalogApi
import com.nekolaska.calabiyau.feature.wiki.meme.api.MemeApi
import com.nekolaska.calabiyau.feature.wiki.meow.api.MeowLanguageApi
import com.nekolaska.calabiyau.feature.wiki.oath.api.OathApi
import com.nekolaska.calabiyau.feature.wiki.playerlevel.api.PlayerLevelApi
import com.nekolaska.calabiyau.feature.wiki.gallery.api.GalleryApi
import com.nekolaska.calabiyau.feature.wiki.game.api.GameModeApi
import com.nekolaska.calabiyau.feature.wiki.map.api.MapListApi
import com.nekolaska.calabiyau.feature.wiki.story.api.StoryApi
import com.nekolaska.calabiyau.feature.wiki.stringer.api.StringerPushCardApi
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
        AchievementApi
        BgmApi
        CollaborationApi
        GameHistoryApi
        GameTipsApi
        ImprintApi
        ItemCatalogApi
        MemeApi
        MeowLanguageApi
        OathApi
        PlayerLevelApi
        StoryApi
        StringerPushCardApi
    }
}