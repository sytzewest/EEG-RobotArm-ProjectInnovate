package com.nhlstenden.mindwave

import com.fasterxml.jackson.module.kotlin.readValue

sealed class HeadsetData {
    companion object {

        //picks packet type based on json contents
        fun from(json: String) = when {
            "status" in json -> mapper.readValue<StatusReport>(json)
            "eSense" in json -> mapper.readValue<ProcessedData>(json)
            "blink" in json -> mapper.readValue<BlinkStrength>(json)
            "mental" in json -> mapper.readValue<MentalEffort>(json)
            "familiarity" in json -> mapper.readValue<Familiarity>(json)
            "raw" in json -> mapper.readValue<RawData>(json)
            else -> throw IllegalArgumentException("Unrecognized headset data JSON")
        }
    }

    //status report packets, usually sent when the Connector is idling or searching for a headset
    data class StatusReport(val poorSignalLevel: Int, val status: String) : HeadsetData()

    //processed data received up to once a second
    data class ProcessedData(val eSense: ESenseData, val eegPower: EEGPowerData, val poorSignalLevel: Int) :
        HeadsetData() {
        data class ESenseData(val attention: Int, val meditation: Int)

        data class EEGPowerData(
            val delta: Int,
            val theta: Int,
            val lowAlpha: Int,
            val highAlpha: Int,
            val lowBeta: Int,
            val highBeta: Int,
            val lowGamma: Int,
            val highGamma: Int
        )
    }

    //packets with secondary values
    data class BlinkStrength(val blinkStrength: Int) : HeadsetData()

    data class MentalEffort(val mentalEffort: Double) : HeadsetData()

    data class Familiarity(val familiarity: Double) : HeadsetData()

    //raw data packet with a single power value
    data class RawData(val rawEeg: Int) : HeadsetData()
}