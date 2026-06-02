package org.datayoo.picker

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 同一 [Project] 同时只允许一路「串行 Maven 打包」。
 * 多路并行会互相 `clean` / 覆盖 `target/classes`，导致 `descriptor-plugin` 在
 * `descriptorPack` 阶段报 `descriptor.includes matched 0 operators` 等假失败。
 *
 * 同时持有当前 [OSProcessHandler]，供「停止打包」调用 [destroyProcess]。
 */
@Service(Service.Level.PROJECT)
class MavenPackCoordinator {
  private val packInProgress = AtomicBoolean(false)
  private val currentMavenHandler = AtomicReference<OSProcessHandler?>(null)
  private val stopRequested = AtomicBoolean(false)

  fun tryBeginPack(): Boolean = packInProgress.compareAndSet(false, true)

  fun endPack() {
    packInProgress.set(false)
    currentMavenHandler.set(null)
    stopRequested.set(false)
  }

  fun attachMavenHandler(handler: OSProcessHandler) {
    currentMavenHandler.set(handler)
  }

  fun detachMavenHandler() {
    currentMavenHandler.set(null)
  }

  /** @return 是否对已关联的 Maven 子进程发出了终止信号 */
  fun requestStopPack(): Boolean {
    val h = currentMavenHandler.get() ?: return false
    if (h.isProcessTerminated) return false
    stopRequested.set(true)
    h.destroyProcess()
    return true
  }

  /**
   * [processTerminated] 时调用：是否为本插件「停止打包」按钮触发的结束。
   * 注意：不能依赖 [ProcessListener.processWillTerminate] 判断——在 ConsoleView 切换/正常退出时
   * 平台仍可能把 `willBeDestroyed` 置为 true，导致未点停止却出现「用户中断」。
   */
  fun consumeStopRequested(): Boolean = stopRequested.getAndSet(false)
}
