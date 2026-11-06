package com.juying.tv.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.juying.tv.JuyingApp
import com.juying.tv.MainActivity
import com.juying.tv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.SecureRandom

class McpServerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: McpServer? = null
    private val tasks = McpTaskRegistry()

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(8787, "正在启动…"))
        scope.launch {
            val graph = JuyingApp.instance.graph
            if (!graph.settings.mcpEnabled.first()) {
                stopSelf()
                return@launch
            }
            var token = graph.settings.mcpToken.first()
            if (token.isBlank()) {
                token = generateToken()
                graph.settings.setMcpToken(token)
            }
            val port = graph.settings.mcpPort.first()
            server?.stop()
            val newServer = McpServer(port, token, graph, tasks)
            try {
                newServer.start(5000, false)
                server = newServer
                McpRuntime.onStarted(port)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(port, "运行中"))
            } catch (e: Exception) {
                McpRuntime.onError(e.message ?: "MCP 服务启动失败")
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(port, "启动失败：${e.message ?: "端口被占用"}"))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        McpRuntime.onStopped()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "聚影 MCP 服务",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(port: Int, status: String): Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("聚影 TV · MCP 服务")
            .setContentText("$status · 端口 $port")
            .setContentIntent(intent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START = "com.juying.tv.mcp.START"
        const val ACTION_STOP = "com.juying.tv.mcp.STOP"
        private const val CHANNEL_ID = "juying_mcp"
        private const val NOTIFICATION_ID = 7801

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, McpServerService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, McpServerService::class.java))
        }

        fun generateToken(): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return "juying_" + (1..32).map { chars[SecureRandom().nextInt(chars.length)] }.joinToString("")
        }
    }
}
