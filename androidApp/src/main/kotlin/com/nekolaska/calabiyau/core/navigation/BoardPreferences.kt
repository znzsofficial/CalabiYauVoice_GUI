package com.nekolaska.calabiyau.core.navigation

import com.nekolaska.calabiyau.core.preferences.AppPrefs

/** Persistence boundary, injectable so account/cache races are tested without Android storage. */
internal interface BoardPreferences {
    var nickname: String?
    var postAsGuest: Boolean
    var lastIdentity: String?
    var guestId: String?
    var draftsJson: String?
    fun flushDrafts(): Boolean
}

internal object AppBoardPreferences : BoardPreferences {
    override var nickname: String?
        get() = AppPrefs.anonymousNickname
        set(value) { AppPrefs.anonymousNickname = value }
    override var postAsGuest: Boolean
        get() = AppPrefs.boardPostAsGuest
        set(value) { AppPrefs.boardPostAsGuest = value }
    override var lastIdentity: String?
        get() = AppPrefs.boardLastIdentity
        set(value) { AppPrefs.boardLastIdentity = value }
    override var guestId: String?
        get() = AppPrefs.anonymousGuestId
        set(value) { AppPrefs.anonymousGuestId = value }
    override var draftsJson: String?
        get() = AppPrefs.messageBoardDraftsJson
        set(value) { AppPrefs.messageBoardDraftsJson = value }
    override fun flushDrafts() = AppPrefs.flushMessageBoardDrafts()
}
