package org.datayoo.picker

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 按工程持久化「排除的算子」：键与列表逻辑一致，为 `descriptor类全名|OpDefiner name`（见工具窗口内 [operatorRuntimeKey]）。
 * 默认扫描后隐藏被排除项；勾选「显示已排除」后可右键恢复。
 */
@State(name = "DatayooPickerOperatorExcludeSettings", storages = [Storage("datayoo-picker-operator-exclude.xml")])
@Service(Service.Level.PROJECT)
class OperatorExcludeSettings : PersistentStateComponent<OperatorExcludeSettings.State> {

  data class State(
    var excludedOperatorKeys: MutableList<String> = mutableListOf()
  )

  private var state = State()

  override fun getState(): State = state

  override fun loadState(loaded: State) {
    state = loaded
    state.excludedOperatorKeys = state.excludedOperatorKeys
      .asSequence()
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .distinct()
      .toMutableList()
  }

  fun isExcluded(operatorKey: String): Boolean {
    val k = operatorKey.trim()
    if (k.isBlank()) return false
    return state.excludedOperatorKeys.any { it == k }
  }

  fun addExcluded(operatorKey: String) {
    val k = operatorKey.trim()
    if (k.isBlank()) return
    if (state.excludedOperatorKeys.none { it == k }) {
      state.excludedOperatorKeys.add(k)
    }
  }

  fun removeExcluded(operatorKey: String) {
    val k = operatorKey.trim()
    state.excludedOperatorKeys.removeAll { it == k }
  }
}
