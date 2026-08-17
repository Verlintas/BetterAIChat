package com.betteraichat

import android.app.Application
import com.betteraichat.core.chat.ChatRepository
import com.betteraichat.core.db.AppDatabase
import com.betteraichat.core.engine.ChatEngine
import com.betteraichat.core.storage.SettingsRepository
import com.betteraichat.core.skills.SkillRepository
import com.betteraichat.providers.ProviderFactory
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.DeviceToolRunner
import com.betteraichat.skills.SkillActionExecutor
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.ToolRegistry
import com.betteraichat.skills.tools.DeviceInfoTool
import com.betteraichat.skills.tools.GetClipboardTool
import com.betteraichat.skills.tools.LoadSkillTool
import com.betteraichat.skills.tools.OpenAppTool
import com.betteraichat.skills.tools.OpenSettingsTool
import com.betteraichat.skills.tools.SendNotificationTool
import com.betteraichat.skills.tools.SetAlarmTool
import com.betteraichat.skills.tools.SetBrightnessTool
import com.betteraichat.skills.tools.SetClipboardTool
import com.betteraichat.skills.tools.SetFlashlightTool
import com.betteraichat.skills.tools.SetScreenTimeoutTool
import com.betteraichat.skills.tools.SetVolumeTool
import com.betteraichat.skills.tools.SpeakTextTool
import com.betteraichat.skills.tools.TakeScreenshotTool
import com.betteraichat.skills.tools.WebReadTool
import com.betteraichat.skills.tools.WebSearchTool
import com.betteraichat.tools.ScreenshotManager
import com.betteraichat.tools.ShizukuManager
import com.betteraichat.skills.tools.RunShellTool

class BetterAIChatApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Application) {

    val appContext = context.applicationContext
    val db = AppDatabase.get(context)
    val settings = SettingsRepository(context)
    val repository = ChatRepository(db)
    val skillRepository = SkillRepository(context.applicationContext)

    private val screenshotManager = ScreenshotManager(context.applicationContext)
    private val toolContext = ToolContext(context.applicationContext, screenshotManager)

    val shizukuManager = ShizukuManager()
    val actionExecutor = SkillActionExecutor(context.applicationContext)

    val tools: List<DeviceTool> = listOf(
        OpenAppTool(),
        SendNotificationTool(),
        SetBrightnessTool(),
        SetVolumeTool(),
        DeviceInfoTool(),
        TakeScreenshotTool(),
        WebSearchTool(),
        WebReadTool(),
        SetClipboardTool(),
        GetClipboardTool(),
        SetAlarmTool(),
        SetFlashlightTool(),
        OpenSettingsTool(),
        SetScreenTimeoutTool(),
        RunShellTool { shizukuManager.granted.value },
        SpeakTextTool(),
        LoadSkillTool({ skillRepository.loadAll() }, { registry }, actionExecutor)
    )
    val registry = ToolRegistry(tools)
    val runner = DeviceToolRunner(registry, toolContext)
    val engine = ChatEngine(
        { ProviderFactory.create(it) },
        registry,
        runner
    )

    val screenshotManagerRef = screenshotManager
}
