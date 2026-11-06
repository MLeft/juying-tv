package com.juying.tv.mcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class McpTask(
    val id: String,
    val type: String,
    val status: String,
    val message: String,
    val progress: Int,
    val total: Int,
    val result: String?,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long
)

class McpTaskRegistry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val tasks = LinkedHashMap<String, McpTask>()
    private val jobs = HashMap<String, Job>()

    fun start(
        type: String,
        total: Int = 0,
        block: suspend (McpTaskReporter) -> String?
    ): McpTask {
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        val now = System.currentTimeMillis()
        val task = McpTask(
            id = id,
            type = type,
            status = "running",
            message = "已开始",
            progress = 0,
            total = total,
            result = null,
            error = null,
            createdAt = now,
            updatedAt = now
        )
        synchronized(tasks) { tasks[id] = task }
        val reporter = object : McpTaskReporter {
            override fun report(message: String, progress: Int?, total: Int?) {
                update(id) {
                    it.copy(
                        message = message,
                        progress = progress ?: it.progress,
                        total = total ?: it.total,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        }
        val job = scope.launch {
            try {
                val result = block(reporter)
                update(id) {
                    it.copy(
                        status = "completed",
                        message = "已完成",
                        progress = if (it.total > 0) it.total else it.progress,
                        result = result,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            } catch (e: CancellationException) {
                update(id) {
                    it.copy(
                        status = "cancelled",
                        message = "已取消",
                        updatedAt = System.currentTimeMillis()
                    )
                }
                throw e
            } catch (e: Exception) {
                update(id) {
                    it.copy(
                        status = "failed",
                        message = e.message ?: "任务失败",
                        error = e.message ?: "未知错误",
                        updatedAt = System.currentTimeMillis()
                    )
                }
            } finally {
                synchronized(jobs) { jobs.remove(id) }
            }
        }
        synchronized(jobs) { jobs[id] = job }
        return task
    }

    suspend fun get(id: String): McpTask? = lock.withLock { synchronized(tasks) { tasks[id] } }

    fun list(): List<McpTask> = synchronized(tasks) { tasks.values.toList() }

    fun cancel(id: String): Boolean {
        val job = synchronized(jobs) { jobs[id] } ?: return false
        job.cancel()
        return true
    }

    private inline fun update(id: String, transform: (McpTask) -> McpTask) {
        synchronized(tasks) {
            val current = tasks[id] ?: return
            tasks[id] = transform(current)
        }
    }
}

interface McpTaskReporter {
    fun report(message: String, progress: Int? = null, total: Int? = null)
}
