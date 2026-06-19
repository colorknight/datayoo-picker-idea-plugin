package org.datayoo.picker

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 同一 [Project] 同时只允许一路打包会话。
 * 「按Module打包」时同会话内不同 module 可并行执行，此时持有多个 [OSProcessHandler]。
 */
@Service(Service.Level.PROJECT)
class MavenPackCoordinator {
  private val packInProgress = AtomicBoolean(false)
  private val mavenHandlers = CopyOnWriteArrayList<OSProcessHandler>()
  private val stopRequested = AtomicBoolean(false)

  fun tryBeginPack(): Boolean = packInProgress.compareAndSet(false, true)

  fun endPack() {
    packInProgress.set(false)
    mavenHandlers.clear()
    stopRequested.set(false)
  }

  fun attachMavenHandler(handler: OSProcessHandler) {
    mavenHandlers.add(handler)
  }

  fun detachMavenHandler(handler: OSProcessHandler) {
    mavenHandlers.remove(handler)
  }

  /** @return 是否对任一关联的 Maven 子进程发出了终止信号 */
  fun requestStopPack(): Boolean {
    val handlers = mavenHandlers.toList()
    if (handlers.isEmpty()) return false
    var stopped = false
    stopRequested.set(true)
    for (h in handlers) {
      if (!h.isProcessTerminated) {
        h.destroyProcess()
        stopped = true
      }
    }
    return stopped
  }

  fun consumeStopRequested(): Boolean = stopRequested.getAndSet(false)
}
