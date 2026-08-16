package com.betteraichat

import android.app.Application
import com.betteraichat.core.chat.ChatRepository
import com.betteraichat.core.db.AppDatabase
import com.betteraichat.core.engine.ChatEngine
import com.betteraichat.core.storage.SettingsRepository
import com.betteraichat.providers.ProviderFactory
import com.betteraichat.skills.DeviceToolRunner
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.ToolRegistry
import com.betteraichat.skills.tools.DeviceInfoTool
import com.betteraichat.skills.tools.OpenAppTool
import com.betteraichat.skills.tools.SendNotificationTool
import com.betteraichat.skills.tools.SetBrightnessTool
import com.betteraichat.skills.tools.SetVolumeTool
import com.betteraichat.skills.tools.TakeScreenshotTool
import com.betteraichat.tools.ScreenshotManager

class BetterAIChatApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Application) {

    val db = AppDatabase.get(context)
    val settings = SettingsRepository(context)
    val repository = ChatRepository(db)

    private val screenshotManager = ScreenshotManager(context.applicationContext)
    private val toolContext = ToolContext(context.applicationContext, screenshotManager)

    val tools = listOf(
        OpenAppTool(),
        SendNotificationTool(),
        SetBrightnessTool(),
        SetVolumeTool(),
        DeviceInfoTool(),
        TakeScreenshotTool()
    )
    val registry = ToolRegistry(tools)
    val runner = DeviceToolRunner(tools, toolContext)
    val engine = ChatEngine({ ProviderFactory.create(it) }, registry, runner)

    val screenshotManagerRef = screenshotManager
}
