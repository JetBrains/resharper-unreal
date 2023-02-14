package integrationTests.refactorings.rename

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.IdeActions.ACTION_RENAME
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.testFramework.ProjectViewTestUtil
import com.jetbrains.ide.model.uiautomation.BeCheckbox
import com.jetbrains.ide.model.uiautomation.BeTextBox
import com.jetbrains.rd.ide.model.UnrealVersion
import com.jetbrains.rd.ui.bedsl.extensions.getBeControlById
import com.jetbrains.rd.ui.bedsl.extensions.tryGetBeControlById
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.actions.RiderActions
import com.jetbrains.rider.model.refactorings.BeRefactoringsPage
import com.jetbrains.rider.projectView.actions.SolutionViewAddActionGroup
import com.jetbrains.rider.projectView.actions.newFile.RiderNewFileCustomAction
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.framework.*
import com.jetbrains.rider.test.scriptingApi.*
import integrationTests.refactorings.rename.CoreRedirects.Companion.renameClassFactory
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.Assert
import org.testng.annotations.*
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealClassProject
import testFrameworkExtentions.UnrealConstants
import java.io.File
import java.time.Duration


/**
 * Unreal rename refactoring tests.
 * When we rename Unreal symbol we use Core Redirect functionality (simplified this is a text pointer from
 * the old name to the new one in the .ini file).
 *
 * The tests rename different symbols (see [symbolsToRenameArray]) twice to make sure that rename works normally and all needed redirects appear.
 *
 * TestNG [Factory] [renameClassFactory] generates classes based on [engineVersion] (see [UnrealConstants.testingVersions])
 * and [pmType] (see [UnrealConstants.projectModelTypes]),
 * then each test is generated by [DataProvider] [differentUnrealSymbols] and we get a separate test for each intersection.
 * Project opened before each class (see [UnrealClassProject]), set of tests on each engine+model run on single-opened project.
 */
// TODO replace dumping functions with generic ones from TestFramework
@Epic("Refactorings")
@Feature("Rename")
@TestEnvironment(platform = [PlatformType.WINDOWS_X64], coreVersion = CoreVersion.LATEST_STABLE)
class CoreRedirects(private val engineVersion: UnrealVersion, private val pmType: EngineInfo.UnrealOpenType) :
    UnrealClassProject() {

    override val traceCategories: List<String>
        get() = listOf(
            "#com.jetbrains.rider.test.framework.dataContextTree"
        )

    companion object {
        @Factory
        fun renameClassFactory(): Array<Any> {
            val result: ArrayList<Any> = arrayListOf()

            UnrealConstants.testingVersions.forEach { version ->
                UnrealConstants.projectModelTypes.forEach { pmType ->
                    result.add(CoreRedirects(version, pmType))
                }
            }
            frameworkLogger.info("Factory was generated: ${result.joinToString()}")
            println("Factory was generated: ${result.joinToString()}")
            return result.toArray()
        }
    }

    @BeforeClass(dependsOnMethods = ["putSolutionToTempDir"])
    fun prepareAndOpenSolution() {
//        configureAndOpenUnrealProject(pmType, unrealInfo.getEngine(engineVersion), disableEnginePlugins)
        prepareUnrealProject(pmType, unrealInfo.getEngine(engineVersion))
        backupProject(File(tempDirectory).resolve("${projectDirectoryName}_backup"))
        project = openProject(pmType)
    }

    @AfterMethod
    fun restoreInitialProjectState() {
        closeAllOpenedEditors(project)
        restoreProject(activeSolutionDirectory,
            File(tempDirectory).resolve("${projectDirectoryName}_backup"),
            BackupRestoreProfile().apply {
                ignoreDirs.add("Intermediate")
                ignoreDirs.add("Logs")
            })
    }

    override val testCaseNameToTempDir = "RenameTempTestRunDir"
    override val clearCaches: Boolean = false

    init {
        projectDirectoryName = "TestProjectAndPlugin"
    }

    private val symbolsToRenameArray = arrayOf(
        Pair("Class", "AMyActor"),
        Pair("Property", "bMyProperty"),
        Pair("Struct", "FMyStruct"),
        Pair("Enum", "EMyEnum"),
        Pair("EnumClass", "EMyEnumClass")
    )

    @DataProvider
    fun differentUnrealSymbols(): Iterator<Array<Any>> {
        val result: ArrayList<Array<Any>> = arrayListOf()
        symbolsToRenameArray.forEach { symbol ->
            result.add(
                arrayOf(uniqueDataString("$pmType", unrealInfo.currentEngine) + symbol.first, symbol)
            )
        }
        frameworkLogger.info("Data Provider was generated: ${result.joinToString()}")
        return result.iterator()
    }

    private val renameDumpProfile = TestProjectModelDumpFilesProfile().apply {
        extensions.clear()
        extensions.addAll(arrayOf("cpp", "h", "ini")) // only these extensions would be dumped
    }

    @Test(dataProvider = "differentUnrealSymbols")
    fun project(@Suppress("UNUSED_PARAMETER") caseName: String,
        symbolToRename: Pair<String, String>
    ) {
        val projectFile = "$activeSolutionDirectory/Source/$projectDirectoryName/MyActor.h"

        val symbolValue = symbolToRename.second

        withDumpEachStep(actions = arrayOf(
            Pair("Rename ${symbolToRename.first} in project") {
                withOpenedEditor(project, projectFile) {
                    renameUnrealSymbol(
                        symbolValue,
                        "${symbolValue}Rename",
                        editor = this
                    )
                }
            },
            Pair("Second rename ${symbolToRename.first} in project") {
                withOpenedEditor(project, projectFile) {
                    renameUnrealSymbol(
                        "${symbolValue}Rename",
                        "${symbolValue}SecondRename",
                        editor = this
                    )
                }
            }
        )
        )
    }

    @Test(dataProvider = "differentUnrealSymbols")
    fun projectPlugin(@Suppress("UNUSED_PARAMETER") caseName: String,
        symbolToRename: Pair<String, String>
    ) {
        val pluginFile = "$activeSolutionDirectory/Plugins/TestPlugin/Source/TestPlugin/Public/MyActorPlugin.h"
        val pluginDumpedItems = listOf(
            File(activeSolutionDirectory, "Plugins").resolve("TestPlugin").resolve("Config"),
            File(activeSolutionDirectory, "Plugins").resolve("TestPlugin").resolve("Source")
        )
        val symbolValue = symbolToRename.second + "Plugin"

        withDumpEachStep(dumpItems = pluginDumpedItems, actions = arrayOf(
            Pair("Rename ${symbolToRename.first} in plugin") {
                withOpenedEditor(project, pluginFile) {
                    renameUnrealSymbol(
                        symbolValue, "${symbolValue}Rename",
                        editor = this
                    )
                }
            },
            Pair("Second rename ${symbolToRename.first} in plugin") {
                withOpenedEditor(project, pluginFile) {
                    renameUnrealSymbol(
                        "${symbolValue}Rename", "${symbolValue}SecondRename",
                        editor = this
                    )
                }
            }
        ))
    }
    
    /**
     * Performs some [actions] and writes the action title (Pair.first from each action) and content of [dumpItems] (can be files or dirs)
     * according to [dumpProfile] to [testGoldFile] after each action.
     */
    private fun withDumpEachStep(
        testGoldFile: File = File(testCaseGoldDirectory, testMethod.name),
        dumpItems: List<File> = listOf(
            File(activeSolutionDirectory, "Source"),
            File(activeSolutionDirectory, "Config")
        ),
        dumpProfile: TestProjectModelDumpFilesProfile = renameDumpProfile,
        dumpInitBlock: Boolean = true,
        vararg actions: Pair<String, () -> Unit>
    ) {
        executeWithGold(testGoldFile) { printStream ->
            val resultDump = StringBuilder()

            if (dumpInitBlock) {
                @Suppress("MoveLambdaOutsideParentheses")
                resultDump.append(doActionAndDump("Init", dumpItems, dumpProfile, {}))
            }
            
            for (action in actions) {
                resultDump.append(doActionAndDump(action.first, dumpItems, dumpProfile, action.second))
            }

            printStream.append(resultDump)
        }
    }

    /**
     * Creates an empty [StringBuilder], do some [action] and write [dumpCaption] and content of [dumpItems] (can be files or dirs)
     * according to [dumpProfile] to this builder.
     * @return [StringBuilder] filled with the resulting dump.
     */
    private fun doActionAndDump(
        dumpCaption: String,
        dumpItems: List<File>,
        dumpProfile: TestProjectModelDumpFilesProfile = TestProjectModelDumpFilesProfile(),
        action: () -> Unit
    ): StringBuilder {
        val sb = StringBuilder()
        sb.appendLine(dumpCaption)
        sb.appendLine()
        action()
        for (item in dumpItems) {
            assert(item.exists())
            if (item.isDirectory) {
                dumpFiles(sb, item, false, dumpProfile)
            } else if (item.isFile) {
                sb.appendLine("[${item.name}]")
                sb.appendLine(item.readText())
                sb.appendLine()
            }
        }
        return sb
    }

    /**
     * Renames Unreal Engine symbol from [oldSymbolName] to [newSymbolName].
     * Can rename files along with the symbol, for example when renaming class (optional) by [renameFile].
     * Can add Core Redirects (optional) by [addCoreRedirect].
     * Some [editor] must be set to work, for example by [withOpenedEditor].
     */
    private fun renameUnrealSymbol(
        oldSymbolName: String,
        newSymbolName: String,
        renameFile: Boolean = false,
        addCoreRedirect: Boolean = true,
        editor: EditorImpl
    ) {
        logger.debug("Start renaming $oldSymbolName to $newSymbolName")
        editor.setCaretBeforeWord(oldSymbolName)
        var pageNumber = 0
        withPageWithClickedNext(project, function = {
            executeAction(ACTION_RENAME, editor.dataContext)
        }, pageActions = {
            val page = this as BeRefactoringsPage
            val content = page.content
            when (pageNumber) {
                0 -> {
                    val nameCheckbox = content.getBeControlById<BeTextBox>("Rename.Name")
                    nameCheckbox.text.set(newSymbolName)

                    val renameFileCheckbox = content.tryGetBeControlById<BeCheckbox>("Rename.RenameFile")
                    renameFileCheckbox?.property?.set(renameFile) // TODO fix exceptions and set(true)
                }

                1 -> {
                    val addCoreRedirectCheckbox = content.getBeControlById<BeCheckbox>("ShouldCoreRedirect")
                    addCoreRedirectCheckbox.property.set(addCoreRedirect)
                }

                else -> {
                    Assert.fail("Unexpected page $page")
                }
            }
            pageNumber += 1
        })
        waitBackendAndWorkspaceModel(project)
        persistAllFilesOnDisk()
    }
}