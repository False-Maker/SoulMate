package com.soulmate.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soulmate.data.service.CrisisInterventionManager
import com.soulmate.data.service.EmotionalHealthReportGenerator
import com.soulmate.data.service.MindWatchService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * SafetyViewModel - 安全中心 ViewModel
 *
 * 管理危机干预资源和情绪健康报告的数据
 */
@HiltViewModel
class SafetyViewModel @Inject constructor(
    private val crisisInterventionManager: CrisisInterventionManager,
    private val mindWatchService: MindWatchService,
    private val reportGenerator: EmotionalHealthReportGenerator
) : ViewModel() {

    // --- 危机资源 ---
    fun getCrisisResources() = crisisInterventionManager.getCrisisResources()

    // --- 情绪报告 ---
    private val _reportState = MutableStateFlow<EmotionalHealthReportGenerator.HealthReport?>(null)
    val reportState: StateFlow<EmotionalHealthReportGenerator.HealthReport?> = _reportState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _exportResult = MutableStateFlow<File?>(null)
    val exportResult: StateFlow<File?> = _exportResult.asStateFlow()

    /**
     * 加载周报
     */
    fun loadWeeklyReport() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 生成报告（包含最近7天数据）
                val report = reportGenerator.generateWeeklyReport()
                _reportState.value = report
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 导出 PDF
     */
    fun exportPdf() {
        val report = _reportState.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val file = reportGenerator.exportToPdf(report)
                _exportResult.value = file
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 清除导出状态
     */
    fun clearExportResult() {
        _exportResult.value = null
    }
}
