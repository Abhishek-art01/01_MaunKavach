package com.maunkavach.crypto

/**
 * Tracks state needed to reject replayed/out-of-order messages, per the spec's section 5 and
 * 22 ("Reject duplicate message_id", "Reject old/replayed counter"). Backed by the local
 * encrypted DB (see MaunKavachDbHelper) — call sites should persist [RatchetState.receiveCounter]
 * and a bounded window of recent message_ids after each successful accept.
 *
 * Note: `seenMessageIds` here is an in-memory set for the current session; a production
 * build should persist at least the last N message_ids per contact (or a counter-based
 * window) to disk so a replay can't slip through after an app restart before the set repopulates.
 */
class ReplayProtection {
    private val seenMessageIds = mutableSetOf<String>()
    private val lastReceivedCounter = mutableMapOf<String, Long>()

    sealed class Result {
        object Accepted : Result()
        object DuplicateMessageId : Result()
        object StaleOrReplayedCounter : Result()
    }

    @Synchronized
    fun checkAndRecord(contactId: String, messageId: String, counter: Long): Result {
        if (messageId in seenMessageIds) return Result.DuplicateMessageId
        val last = lastReceivedCounter[contactId] ?: -1L
        if (counter <= last) return Result.StaleOrReplayedCounter

        seenMessageIds.add(messageId)
        lastReceivedCounter[contactId] = counter
        return Result.Accepted
    }

    fun loadPersistedState(contactId: String, lastCounter: Long, recentIds: Collection<String>) {
        lastReceivedCounter[contactId] = lastCounter
        seenMessageIds.addAll(recentIds)
    }
}
