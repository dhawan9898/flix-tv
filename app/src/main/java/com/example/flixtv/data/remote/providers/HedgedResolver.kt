package com.example.flixtv.data.remote.providers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Boxed so "timed out waiting" (null from withTimeoutOrNull) differs from "candidate failed".
private class Outcome<T>(val value: T?)

/**
 * Resolves the first of [candidates] (in preference order) for which [resolve] yields a
 * non-null result.
 *
 * Candidates aren't tried strictly one after another: a slow or hanging mirror would hold
 * playback for its full network timeouts before the next one was even asked. Instead each
 * candidate gets [hedgeDelayMs] of head start, then the next starts in parallel; a candidate
 * that fails outright (throws or returns null) hands over immediately. A fast first candidate
 * behaves exactly like a sequential walk. Returns null when every candidate fails.
 */
suspend fun <T : Any> resolveFirst(
    candidates: List<String>,
    hedgeDelayMs: Long,
    resolve: suspend (String) -> T?
): T? = coroutineScope {
    if (candidates.isEmpty()) return@coroutineScope null

    val outcomes = Channel<Outcome<T>>(Channel.UNLIMITED)
    var launched = 0
    var finished = 0

    fun launchNext() {
        val candidate = candidates[launched++]
        launch {
            val value = try {
                resolve(candidate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            outcomes.send(Outcome(value))
        }
    }

    launchNext()
    var winner: T? = null
    while (winner == null && finished < candidates.size) {
        val outcome = if (launched < candidates.size) {
            withTimeoutOrNull(hedgeDelayMs) { outcomes.receive() }
        } else {
            outcomes.receive()
        }
        if (outcome == null) {
            launchNext() // hedge: the running candidate is slow, start the next alongside it
            continue
        }
        finished++
        winner = outcome.value
        if (winner == null && launched < candidates.size) launchNext()
    }
    coroutineContext.cancelChildren()
    winner
}
