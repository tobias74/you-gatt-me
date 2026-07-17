package de.tobiga.yougattme.bridge

import java.util.ArrayDeque

fun interface ScheduledCancellation {
    fun cancel()
}

fun interface OperationTimeoutScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): ScheduledCancellation
}

/**
 * Platform-neutral one-at-a-time operation arbiter. Android's GATT queue uses this controller,
 * while unit tests can drive it with a fake transport and clock.
 */
class SerializedOperationController<T>(
    private val starter: (T) -> Boolean,
    private val timeoutMillis: (T) -> Long,
    private val scheduler: OperationTimeoutScheduler,
    private val completion: (T, Boolean, ByteArray?) -> Unit,
    private val onTimeout: (T) -> Unit = {}
) {
    private val pending = ArrayDeque<T>()
    private var timeoutCancellation: ScheduledCancellation? = null

    var active: T? = null
        private set

    fun enqueue(operation: T) {
        pending += operation
        startNext()
    }

    fun finishActive(success: Boolean, value: ByteArray? = null) {
        val operation = active ?: return
        timeoutCancellation?.cancel()
        timeoutCancellation = null
        active = null
        completion(operation, success, value)
        startNext()
    }

    fun clear() {
        timeoutCancellation?.cancel()
        timeoutCancellation = null
        val running = active
        active = null
        if (running != null) completion(running, false, null)
        while (pending.isNotEmpty()) completion(pending.removeFirst(), false, null)
    }

    private fun startNext() {
        if (active != null || pending.isEmpty()) return
        val operation = pending.removeFirst()
        active = operation
        if (!starter(operation)) {
            finishActive(false)
            return
        }
        // The starter may complete an immediate barrier synchronously.
        if (active === operation) {
            timeoutCancellation = scheduler.schedule(timeoutMillis(operation)) {
                if (active === operation) timeout(operation)
            }
        }
    }

    private fun timeout(operation: T) {
        timeoutCancellation = null
        active = null
        completion(operation, false, null)
        // The ATT transaction is now ambiguous. Fail queued work and let the transport reconnect;
        // otherwise a late callback could be mistaken for the next operation on the same UUID.
        while (pending.isNotEmpty()) completion(pending.removeFirst(), false, null)
        onTimeout(operation)
    }
}
