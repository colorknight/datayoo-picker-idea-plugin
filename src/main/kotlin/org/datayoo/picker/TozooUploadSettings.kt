package org.datayoo.picker

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 应用级配置：在「数由 GUI」标题栏「+」→「Tozoo 根地址…」中填写并保存；所有工程共用。
 */
@State(name = "DatayooTozooUploadSettings", storages = [Storage("datayoo-tozoo-upload.xml")])
@Service(Service.Level.APP)
class TozooUploadSettings : PersistentStateComponent<TozooUploadSettings.State> {

  data class State(
    /**
     * 含 context-path，例如 `http://127.0.0.1:8080/member`。
     * 留空则「数由 GUI」商城列上传不可用（必须先配置 Tozoo）。
     */
    var baseUrl: String = ""
  )

  private var state = State()

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }

  fun isConfigured(): Boolean = state.baseUrl.isNotBlank()
}
