package com.jetbrains.rider.plugins.unreal

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.protocolOrThrow
import com.jetbrains.rd.util.reactive.IProperty
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rider.plugins.unreal.model.ConnectionInfo
import com.jetbrains.rider.plugins.unreal.model.PlayState
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.RdRiderModel
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution

@Service(Service.Level.PROJECT)
class UnrealHost(val project: Project) {
    companion object {
        fun getInstance(project:Project) = project.service<UnrealHost>()
    }
    val logger = Logger.getInstance(UnrealHost::class.java)

    internal val playStateModel: IProperty<PlayState> = Property(PlayState.Idle)
    val playState: PlayState
        get() = playStateModel.value
    var playMode: Int = 0

    internal val model = project.solution.rdRiderModel
    val isUnrealEngineSolution:Boolean
        get() = model.isUnrealEngineSolution.value
    val isConnectedToUnrealEditor:Boolean
        get() = model.isConnectedToUnrealEditor.value
    val connectionInfo:ConnectionInfo?
        get() = model.connectionInfo.valueOrNull
    val isRiderLinkInstallationInProgress:Boolean
        get() = model.riderLinkInstallationInProgress.value
    val isRefreshProjectsInProgress:Boolean
        get() = model.refreshInProgress.value
    val isUproject:Boolean
        get() = model.isUproject.value
    val isInstallInfoAvailable:Boolean
        get() = model.isInstallInfoAvailable.value
    val isHotReloadAvailable: Boolean
        get() = model.isHotReloadAvailable.value
    val isHotReloadCompiling: Boolean
        get() = model.isHotReloadCompiling.value

    fun <R> performModelAction(action:(RdRiderModel)->R) {
        model.protocolOrThrow.scheduler.invokeOrQueue {
            action(model)
        }
    }
}
