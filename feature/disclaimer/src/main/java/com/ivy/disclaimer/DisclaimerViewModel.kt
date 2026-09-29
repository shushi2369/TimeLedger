package com.ivy.disclaimer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.data.repository.LegalRepository
import com.ivy.navigation.Navigation
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisclaimerViewModel @Inject constructor(
    private val navigation: Navigation,
    private val legalRepo: LegalRepository,
) : ComposeViewModel<DisclaimerViewState, DisclaimerViewEvent>() {

    private var checkboxes by mutableStateOf(LegalCheckboxes)

    @Composable
    override fun uiState(): DisclaimerViewState {
        return DisclaimerViewState(
            checkboxes = checkboxes,
            agreeButtonEnabled = checkboxes.all(CheckboxViewState::checked),
        )
    }

    override fun onEvent(event: DisclaimerViewEvent) {
        when (event) {
            DisclaimerViewEvent.OnAgreeClick -> handleAgreeClick()
            is DisclaimerViewEvent.OnCheckboxClick -> handleCheckboxClick(event)
        }
    }

    private fun handleAgreeClick() {
        viewModelScope.launch {
            legalRepo.setDisclaimerAccepted(accepted = true)
            navigation.back()
        }
    }

    private fun handleCheckboxClick(event: DisclaimerViewEvent.OnCheckboxClick) {
        checkboxes = checkboxes.mapIndexed { index, item ->
            if (index == event.index) {
                item.copy(
                    checked = !item.checked
                )
            } else {
                item
            }
        }.toImmutableList()
    }

    companion object {
        // Legal text - fork 决策：中文优先（原英文条款语义保持一致，2026-09-27）
        val LegalCheckboxes = listOf(
            CheckboxViewState(
                text = "本人确认：本应用为开源软件，按“原样”提供，" +
                        "不含任何明示或暗示的保证。" +
                        "本人完全接受使用中可能出现的错误、缺陷或故障风险，" +
                        "并自行承担使用本应用的全部后果。",
                checked = false,
            ),
            CheckboxViewState(
                text = "本人理解：应用不对数据的准确性、可靠性或完整性作任何担保。" +
                        "手动备份数据是本人的责任，" +
                        "本人同意不就任何数据丢失向应用追责。",
                checked = false,
            ),
            CheckboxViewState(
                text = "本人特此免除应用开发者、贡献者及发行方就以下事项产生的任何责任：" +
                        "索赔、损害、律师费或损失，" +
                        "包括因安全漏洞或数据不准确导致的损失。",
                checked = false,
            ),
            CheckboxViewState(
                text = "本人知悉并接受：应用可能显示误导性信息或存在不准确之处。" +
                        "在基于应用数据做出任何决定前，" +
                        "本人有责任自行核实财务数据与计算结果的完整性。",
                checked = false,
            ),
        ).toImmutableList()
    }
}