package com.nekolaska.calabiyau.core.navigation

import data.SharedJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID

@Serializable
internal data class PendingBoardPost(
    val content: String,
    val authorName: String?,
    val actor: String,
    val replyToId: Long?,
    val requestId: String,
    val createdAt: Long
)

@Serializable
internal data class BoardDraft(
    val content: String = "",
    val replyToId: Long? = null,
    val replyToName: String? = null,
    val pending: PendingBoardPost? = null,
    val targetUnavailable: Boolean = false
)

/** Persist the payload and its key together, before starting network I/O. Never persist cookies. */
internal class BoardDraftStore(
    read: () -> String?,
    private val write: (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val drafts = runCatching {
        SharedJson.decodeFromString<Map<String, BoardDraft>>(read() ?: "{}")
    }.getOrDefault(emptyMap()).toMutableMap()

    private var owner: String? = null
    fun useIdentity(identity: String?) { owner = identity }
    private fun key(threadId: Long?) = "${checkNotNull(owner) { "请先确认发言身份" }}:${threadId ?: 0L}"
    fun get(threadId: Long?) = owner?.let { drafts[key(threadId)] } ?: BoardDraft()

    fun edit(threadId: Long?, content: String, replyToId: Long?, replyToName: String?) {
        val old = get(threadId)
        val targetChanged = old.replyToId != replyToId
        put(threadId, old.copy(content = content, replyToId = replyToId, replyToName = replyToName,
            targetUnavailable = old.targetUnavailable && !targetChanged))
    }

    /** Background refresh may invalidate a target, but must never rewrite an uncertain payload. */
    fun markTargetUnavailable(threadId: Long?) {
        val old = get(threadId)
        put(threadId, old.copy(targetUnavailable = true))
    }

    fun selectTarget(threadId: Long?, replyToId: Long?, replyToName: String?) {
        val old = get(threadId)
        put(threadId, old.copy(replyToId = replyToId, replyToName = replyToName, targetUnavailable = false))
    }

    fun prepare(threadId: Long?, authorName: String?, actor: String): PendingBoardPost {
        check(owner == actor) { "发言身份已变化，请先确认当前账号" }
        val draft = get(threadId)
        check(!draft.targetUnavailable) { "原回复目标已不可用。请先核对上次是否发送成功，再明确选择新的回复目标。" }
        val content = draft.content.trim()
        val replyTo = if (threadId == null) null else draft.replyToId ?: threadId
        val previous = draft.pending
        if (previous != null && previous.content == content && previous.authorName == authorName &&
            previous.actor == actor && previous.replyToId == replyTo) {
            // Server retains deduplication records for seven days. Do not silently
            // resend an uncertain request after that guarantee expires.
            check(now() - previous.createdAt < 7L * 24 * 60 * 60 * 1000) {
                "这条待确认留言已超过 7 天，请先核对是否发送成功；修改内容后可作为新留言发送。"
            }
            return previous
        }
        val pending = PendingBoardPost(content, authorName, actor, replyTo, UUID.randomUUID().toString(), now())
        put(threadId, draft.copy(pending = pending))
        return pending
    }

    fun acknowledge(threadId: Long?, requestId: String, actor: String? = owner) {
        val storedKey = "${actor ?: return}:${threadId ?: 0L}"
        if (drafts[storedKey]?.pending?.requestId == requestId) {
            drafts.remove(storedKey)
            save()
        }
    }

    private fun put(threadId: Long?, draft: BoardDraft) { drafts[key(threadId)] = draft; save() }
    private fun save() = write(SharedJson.encodeToString(drafts.toMap()))
}
