package com.aatorque.stats

import com.ezylang.evalex.BaseException
import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.aatorque.datastore.Display
import timber.log.Timber
import java.util.concurrent.ScheduledFuture

class TorqueData(val display: Display) {

    var pid: String? = null
    var minValue: Double = Double.POSITIVE_INFINITY
    var maxValue: Double = Double.NEGATIVE_INFINITY
    private var expression: Expression? = null
    var lastDataStr: String? = null
    var refreshTimer: ScheduledFuture<*>? = null
    var hasReceivedNonZero = false

    var notifyUpdate: ((TorqueData) -> Unit)? = null
        set(value) {
            field = value
            value?.let { it(this) }
        }

    var lastData: Double = 0.0
        set(value) {
            lastDataStr = convertIfNeeded(value)
            field = if (lastDataStr != null) {
                try {
                    lastDataStr!!.toDouble()
                } catch (e: NumberFormatException) {
                    lastData
                }
            } else {
                value
            }
            if (field > maxValue) {
                maxValue = field
            }
            if (field < minValue) {
                minValue = field
            }
        }
    companion object {
        const val PREFIX = "torque_"
        val drawableRegex = Regex("res/drawable/(?<name>.+)\\.[a-z]+")
        val evalConfig: ExpressionConfiguration = ExpressionConfiguration.builder()
            .decimalPlacesRounding(2)
            .build()
    }

    init {
        val value = display.pid
        if (value.startsWith(PREFIX)) {
            pid = value.substring(PREFIX.length)
        }
    }

    private fun convertIfNeeded(value: Double): String? {
        if (!display.enableScript || display.customScript == "") return null
        if (expression == null) {
            val config = if(display.wholeNumbers) {
                ExpressionConfiguration.builder().decimalPlacesRounding(0).build()
            } else {
                evalConfig
            }
            expression = Expression(display.customScript.replace("x", "*"), config)
        }
        return try {
            expression!!.with("a", value).evaluate().stringValue
        } catch (e: BaseException) {
            Timber.e("Unable to parse", e)
            e.printStackTrace()
            "Error"
        }
    }

    fun getDrawableName(): String? {
        val match = drawableRegex.matchEntire(display.icon)
        if (match != null) {
            return match.groups["name"]!!.value
        }
        return null
    }

    fun sendNotifyUpdate() {
        if (notifyUpdate == null) {
            Timber.e("Cannot update, notifyUpdate is null")
        } else {
            notifyUpdate?.let {
                it(this)
            }
        }
    }

    fun stopRefreshing(isDestroying: Boolean = false) {
        refreshTimer?.cancel(true)
        refreshTimer = null
        if (isDestroying) {
            notifyUpdate = null
        }
    }

}