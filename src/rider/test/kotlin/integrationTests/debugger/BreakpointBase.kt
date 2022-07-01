package integrationTests.debugger

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.options.SettingsEditor
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.ide.model.unrealModel
import com.jetbrains.rd.platform.diagnostics.LogTraceScenario
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rider.build.actions.BuildSolutionAction
import com.jetbrains.rider.diagnostics.LogTraceScenarios
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.RiderRunConfigurationBase
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.ToolsetVersion
import com.jetbrains.rider.test.framework.combine
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.scriptingApi.*
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject
import java.io.File
import java.nio.file.Files
import java.time.Duration

@TestEnvironment(
    platform = [PlatformType.WINDOWS],
    toolset = ToolsetVersion.TOOLSET_16_CPP,
    coreVersion = CoreVersion.DEFAULT
)
class BreakpointBase : UnrealTestProject() {
    init {
        projectDirectoryName = "EmptyUProject"
        openSolutionParams.waitForCaches = true
        openSolutionParams.projectModelReadyTimeout = Duration.ofSeconds(150)
        openSolutionParams.backendLoadedTimeout = Duration.ofSeconds(150)
        openSolutionParams.initWithCachesTimeout = Duration.ofSeconds(120)
    }

    override val traceScenarios: Set<LogTraceScenario>
        get() = setOf(LogTraceScenarios.Debugger)

    override val traceCategories: List<String>
        get() = listOf(
            "#com.jetbrains.cidr.execution.debugger",
            "com.jetbrains.rider.test.framework"
        )

    @BeforeMethod
    override fun testSetup(){
        super.testSetup()
        testDataDirectory.combine("additionalSource", "plugins", "DebugTestPlugin")
            .copyRecursively(activeSolutionDirectory.resolve("Plugins").resolve("DebugTestPlugin"))
    }

    @Test(dataProvider = "enginesAndOthers")
    fun toggleTest(@Suppress("UNUSED_PARAMETER") caseName: String, openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        unrealInTestSetup(openWith, engine)

        setConfigurationAndPlatform(project, "DebugGame Editor", "Win64")
        buildWithChecks(project, BuildSolutionAction(), "Build solution", useIncrementalBuild = false)

        testDebugProgram(
            {
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 12)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 17)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 28)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 30)
            }, {
                dumpProfile.customRegexToMask["<address>"] = Regex("0x[\\da-fA-F]{16}")

                waitForPause()                  // 28: someNumber = Foo(someNumber);
                dumpFullCurrentData()
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 30)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 30)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 32)
                resumeSession()
                waitForPause()                  // 12: return fooNum * 2;
                dumpFullCurrentData()

                toggleBreakpoint(project, "DebugTestPlugin.cpp", 31)
                toggleBreakpoint(project, "DebugTestPlugin.cpp", 31)

                resumeSession()
                waitForPause()                  // 17: return b * 3;
                dumpFullCurrentData()

                resumeSession()
                waitForPause()                  // 30: someNumber = Moo(someNumber);
                dumpFullCurrentData()

                resumeSession()
                waitForPause()                  // 12: return fooNum * 2;
                dumpFullCurrentData()

                resumeSession()
                waitForPause()                  // 32: }
                dumpFullCurrentData()
            },
            exitProcessAfterTest = true
        )
    }

    fun testDebugProgram(beforeRun: ExecutionEnvironment.() -> Unit, test: DebugTestExecutionContext.() -> Unit, exitProcessAfterTest: Boolean = false){
        withRunConfigurationEditorWithFirstConfiguration<RiderRunConfigurationBase, SettingsEditor<RiderRunConfigurationBase>>(project) {}
        testDebugProgram(project, testGoldFile, beforeRun, test, {}, exitProcessAfterTest)
    }

    // Mandatory function before opening an unreal project
    private fun unrealInTestSetup(openWith: EngineInfo.UnrealOpenType, engine: UnrealEngine) {
        unrealInfo.currentEngine = engine

        println("Test starting with $engine, opening by $openWith.")

        replaceUnrealEngineVersionInUproject(uprojectFile, unrealInfo.currentEngine!!)

        if (openWith == EngineInfo.UnrealOpenType.Sln) {
            generateSolutionFromUProject(uprojectFile)
            openSolutionParams.minimalCountProjectsMustBeLoaded = null
        } else {
            openSolutionParams.minimalCountProjectsMustBeLoaded = 1400 // TODO: replace the magic number with something normal
        }

        project = openProject(openWith)
        assert(project.solution.unrealModel.isUnrealSolution.hasTrueValue)
    }

    @DataProvider
    fun enginesAndOthers(): MutableIterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
        val uniqueDataString: (String, UnrealEngine) -> String = { baseString: String, engine: UnrealEngine ->
            "$baseString${engine.id.replace('.', '_')}"
        }

        val openWith = EngineInfo.UnrealOpenType.Sln
        val egs5: UnrealEngine = unrealInfo.testingEngines.find { it.id == "5.0" && it.isInstalledBuild }!!
        val egs4: UnrealEngine = unrealInfo.testingEngines.find { it.id == "4.27" && it.isInstalledBuild }!!

        result.add(arrayOf(uniqueDataString("$openWith", egs5), openWith, egs5))
        result.add(arrayOf(uniqueDataString("$openWith", egs4), openWith, egs4))

        frameworkLogger.debug("Data Provider was generated: $result")
        return result.iterator()
    }
}