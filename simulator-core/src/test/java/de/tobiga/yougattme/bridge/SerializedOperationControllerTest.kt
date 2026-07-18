package de.tobiga.yougattme.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SerializedOperationControllerTest {
    private class FakeScheduler : OperationTimeoutScheduler {
        data class Task(val action: () -> Unit, var cancelled: Boolean = false)
        val tasks = mutableListOf<Task>()
        override fun schedule(delayMillis: Long, action: () -> Unit): ScheduledCancellation {
            val task = Task(action)
            tasks += task
            return ScheduledCancellation { task.cancelled = true }
        }
        fun runNext() = tasks.firstOrNull { !it.cancelled }?.also { it.cancelled = true }?.action?.invoke()
    }

    @Test fun `operations start and complete strictly in order`() {
        val scheduler = FakeScheduler()
        val started = mutableListOf<String>()
        val completed = mutableListOf<String>()
        val queue = SerializedOperationController<String>(
            starter = { started += it; true }, timeoutMillis = { 1000 }, scheduler = scheduler,
            completion = { operation, success, _ -> completed += "$operation:$success" }
        )

        queue.enqueue("read")
        queue.enqueue("subscribe")
        assertEquals(listOf("read"), started)
        queue.finishActive(true)
        assertEquals(listOf("read", "subscribe"), started)
        queue.finishActive(true)
        assertEquals(listOf("read:true", "subscribe:true"), completed)
    }

    @Test fun `failed start completes and advances queue`() {
        val scheduler = FakeScheduler()
        val completed = mutableListOf<String>()
        val queue = SerializedOperationController<String>(
            starter = { it != "bad" }, timeoutMillis = { 1000 }, scheduler = scheduler,
            completion = { operation, success, _ -> completed += "$operation:$success" }
        )
        queue.enqueue("bad")
        queue.enqueue("good")
        assertEquals("good", queue.active)
        assertEquals(listOf("bad:false"), completed)
    }

    @Test fun `timeout fails active and pending work and quarantines late completion`() {
        val scheduler = FakeScheduler()
        val completed = mutableListOf<String>()
        val timedOut = mutableListOf<String>()
        val queue = SerializedOperationController<String>(
            starter = { true }, timeoutMillis = { 1000 }, scheduler = scheduler,
            completion = { operation, success, _ -> completed += "$operation:$success" },
            onTimeout = { timedOut += it }
        )
        queue.enqueue("write")
        queue.enqueue("read")
        scheduler.runNext()
        queue.finishActive(true) // late transport callback must not complete anything else
        assertNull(queue.active)
        assertEquals(listOf("write:false", "read:false"), completed)
        assertEquals(listOf("write"), timedOut)
    }

    @Test fun `clear fails active and queued work`() {
        val scheduler = FakeScheduler()
        val completed = mutableListOf<String>()
        val queue = SerializedOperationController<String>(
            starter = { true }, timeoutMillis = { 1000 }, scheduler = scheduler,
            completion = { operation, success, _ -> completed += "$operation:$success" }
        )
        queue.enqueue("discover")
        queue.enqueue("subscribe")
        queue.clear()
        assertNull(queue.active)
        assertEquals(listOf("discover:false", "subscribe:false"), completed)
    }

    @Test fun `nonfatal discovery timeout advances to remaining work`() {
        val scheduler = FakeScheduler()
        val started = mutableListOf<String>()
        val completed = mutableListOf<String>()
        val queue = SerializedOperationController<String>(
            starter = { started += it; true }, timeoutMillis = { 1000 }, scheduler = scheduler,
            completion = { operation, success, _ -> completed += "$operation:$success" },
            continueAfterTimeout = { it == "optional" }
        )
        queue.enqueue("optional")
        queue.enqueue("required")
        scheduler.runNext()
        assertEquals(listOf("optional", "required"), started)
        assertEquals(listOf("optional:false"), completed)
        queue.finishActive(true)
        assertEquals(listOf("optional:false", "required:true"), completed)
    }
}
