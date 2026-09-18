package com.nekolaska.calabiyau.core.navigation

import data.ApiResult

/** Only authoritative resource/auth failures invalidate cached data. Transport errors do not. */
internal fun ApiResult.Error.invalidatesDiscussion(): Boolean = apiCode == "DISCUSSION_UNAVAILABLE"
internal fun ApiResult.Error.invalidatesNotificationSession(): Boolean = httpStatus == 401
