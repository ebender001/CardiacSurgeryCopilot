package dev.benderapps.heartteamprep.service

import com.parse.boltsinternal.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Bridges a Parse SDK Bolts [Task] (its callback/Task-based async style) into
 * a suspend function, the Android equivalent of `await`-ing a Parse-Swift
 * call on iOS.
 */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    continueWith { task ->
        when {
            task.isFaulted -> continuation.resumeWithException(task.error)
            task.isCancelled -> continuation.cancel()
            else -> continuation.resume(task.result)
        }
        null
    }
}
