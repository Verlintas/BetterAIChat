package com.betteraichat

import android.app.Application
import com.betteraichat.core.chat.ChatRepository
import com.betteraichat.core.db.AppDatabase
import com.betteraichat.core.engine.ChatEngine
import com.betteraichat.core.provider.ChatProvider
import com.betteraichat.core.model.ProviderId
import com.betteraichat.core.storage.SettingsRepository
import com.betteraichat.core.skills.SkillRepository
import com.betteraichat.providers.ProviderFactory
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.DeviceToolRunner
import com.betteraichat.skills.SkillActionExecutor
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.ToolRegistry
import com.betteraichat.skills.tools.CalculatorTool
import com.betteraichat.skills.tools.DeviceInfoTool
import com.betteraichat.skills.tools.DownloadFileTool
import com.betteraichat.skills.tools.FetchRssTool
import com.betteraichat.skills.tools.GenerateQrTool
import com.betteraichat.skills.tools.GetWeatherTool
import com.betteraichat.skills.tools.KeepScreenOnTool
import com.betteraichat.skills.tools.ManageAppTool
import com.betteraichat.skills.tools.GetClipboardTool
import com.betteraichat.skills.tools.GetForegroundAppTool
import com.betteraichat.skills.tools.GetTimeTool
import com.betteraichat.skills.tools.LoadSkillTool
import com.betteraichat.skills.tools.MediaControlTool
import com.betteraichat.skills.tools.NetworkStatusTool
import com.betteraichat.skills.tools.OpenAppTool
import com.betteraichat.skills.tools.OpenDialerTool
import com.betteraichat.skills.tools.OpenSettingsTool
import com.betteraichat.skills.tools.RingerModeTool
import com.betteraichat.skills.tools.ScreenOcrTool
import com.betteraichat.skills.tools.SendNotificationTool
import com.betteraichat.skills.tools.SetAlarmTool
import com.betteraichat.skills.tools.SetBrightnessTool
import com.betteraichat.skills.tools.SetClipboardTool
import com.betteraichat.skills.tools.SetDndTool
import com.betteraichat.skills.tools.SetFlashlightTool
import com.betteraichat.skills.tools.SetPowerSaverTool
import com.betteraichat.skills.tools.SetScreenTimeoutTool
import com.betteraichat.skills.tools.SetWifiTool
import com.betteraichat.skills.tools.SetVolumeTool
import com.betteraichat.skills.tools.ShareTextTool
import com.betteraichat.skills.tools.SpeakTextTool
import com.betteraichat.skills.tools.TakeScreenshotTool
import com.betteraichat.skills.tools.UaPressTool
import com.betteraichat.skills.tools.UaSwipeTool
import com.betteraichat.skills.tools.UaTapTool
import com.betteraichat.skills.tools.UaTypeTool
import com.betteraichat.skills.tools.VibrateTool
import com.betteraichat.skills.tools.WebReadTool
import com.betteraichat.skills.tools.WriteDocumentTool
import com.betteraichat.skills.tools.WebSearchTool
import com.betteraichat.tools.ScreenshotManager
import com.betteraichat.tools.ShizukuManager
import com.betteraichat.skills.tools.RunShellTool
import com.betteraichat.skills.tools.ScheduleRepeatTool

class BetterAIChatApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) { container.setAppInForeground(true) }
            override fun onActivityPaused(activity: android.app.Activity) { container.setAppInForeground(false) }
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
        })
    }
}

class AppContainer(context: Application) {

    val appContext = context.applicationContext
    @Volatile
    var appInForeground = false
        private set

    fun setAppInForeground(value: Boolean) {
        appInForeground = value
    }
    var pendingShareText: String? = null
    var pendingShareImage: android.net.Uri? = null
    val shareNavTick = kotlinx.coroutines.flow.MutableStateFlow(0)
    val db = AppDatabase.get(context)
    val settings = SettingsRepository(context)
    val repository = ChatRepository(db)
    val skillRepository = SkillRepository(context.applicationContext)

    private val screenshotManager = ScreenshotManager(context.applicationContext)
    private val ocrBridge = com.betteraichat.tools.ScreenOcr(screenshotManager)
    private val accessibilityBridge = object : com.betteraichat.skills.AccessibilityBridge {
        override fun connected(): Boolean = com.betteraichat.tools.BacAccessibilityService.connected()
        override fun windowTitle(): String? = com.betteraichat.tools.BacAccessibilityService.instance?.windowTitle()
        override suspend fun typeText(text: String): String =
            com.betteraichat.tools.BacAccessibilityService.instance?.typeText(text)
                ?: "ERROR:无障碍服务未连接"
        override suspend fun pressKey(key: String): String =
            com.betteraichat.tools.BacAccessibilityService.instance?.pressKey(key)
                ?: "ERROR:无障碍服务未连接"
        override suspend fun tap(x: Int, y: Int): String =
            com.betteraichat.tools.BacAccessibilityService.instance?.tap(x, y)
                ?: "ERROR:无障碍服务未连接"
        override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): String =
            com.betteraichat.tools.BacAccessibilityService.instance?.swipe(x1, y1, x2, y2, durationMs)
                ?: "ERROR:无障碍服务未连接"
    }
    private val toolContext = ToolContext(
        context.applicationContext,
        screenshotManager,
        ocrBridge,
        accessibilityBridge
    )

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
        ScheduleRepeatTool(),
        LoadSkillTool({ skillRepository.loadAll() }, { registry }, actionExecutor),
        GetTimeTool(),
        MediaControlTool(),
        RingerModeTool(),
        ShareTextTool(),
        OpenDialerTool(),
        VibrateTool(),
        NetworkStatusTool(),
        UaTypeTool(),
        UaPressTool(),
        UaTapTool(),
        UaSwipeTool(),
        GetForegroundAppTool(),
        DownloadFileTool(),
        ScreenOcrTool(),
        SetDndTool(),
        ManageAppTool { shizukuManager.granted.value },
        WriteDocumentTool(),
        SetWifiTool { shizukuManager.granted.value },
        SetPowerSaverTool { shizukuManager.granted.value },
        FetchRssTool(),
        GetWeatherTool(),
        CalculatorTool(),
        GenerateQrTool(),
        KeepScreenOnTool()
    )
    val registry = ToolRegistry(tools)
    val runner = DeviceToolRunner(registry, toolContext)
    val providerFactory: (ProviderId) -> ChatProvider = { ProviderFactory.create(it) }
    val engine = ChatEngine(
        providerFactory,
        registry,
        runner
    )
    val speechPlayer = com.betteraichat.tools.SpeechPlayer(context.applicationContext)

    val screenshotManagerRef = screenshotManager
}
