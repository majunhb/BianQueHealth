package com.bianque.health.engine.data

import com.bianque.health.bp.domain.model.BloodPressureResult
import com.bianque.health.face.domain.model.FaceDiagnosisResult
import com.bianque.health.pulse.domain.model.PulseDiagnosisResult
import com.bianque.health.tongue.domain.model.TongueDiagnosisResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 诊断结果缓存 — 桥接各诊断模块和健康报告页面。
 *
 * 各诊断 Screen 将结果存入缓存，HealthReportScreen 从缓存读取
 * 所有模块的结果，调用 HealthEngineRepository 生成综合报告。
 */
@Singleton
class DiagnosisCache @Inject constructor() {

    var faceResult: FaceDiagnosisResult? = null
    var tongueResult: TongueDiagnosisResult? = null
    var pulseResult: PulseDiagnosisResult? = null
    var bpResult: BloodPressureResult? = null

    fun hasAllResults(): Boolean {
        return faceResult != null && tongueResult != null && pulseResult != null && bpResult != null
    }

    fun clear() {
        faceResult = null
        tongueResult = null
        pulseResult = null
        bpResult = null
    }

    fun getSummary(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        faceResult?.let { map["面诊"] = "面色${it.overallComplexion}，置信度${String.format("%.0f", it.confidence * 100)}%" }
        tongueResult?.let { map["舌诊"] = "${it.tongueColor}舌，${it.coatingColor}苔" }
        pulseResult?.let { map["脉诊"] = "${it.pulseType}，${it.pulseRate}次/分" }
        bpResult?.let { map["血压"] = "${it.systolic}/${it.diastolic} mmHg" }
        return map
    }
}