package com.aatorque.stats
import android.os.Handler
import android.os.Looper
import com.aatorque.datastore.Display
import timber.log.Timber
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

enum class ConnectStatus {
    CONNECTING_TORQUE, CONNECTING_ECU, CONNECTED, SETUP_GAUGE
}

typealias ConStatusFn = ((ConnectStatus) -> Unit)

class TorqueRefresher {
    val data = HashMap<Int, TorqueData>()
    private val executor = ScheduledThreadPoolExecutor(7)
    val handler = Handler(Looper.getMainLooper())
    var lastConnectStatus = ConnectStatus.CONNECTING_TORQUE
    var conWatcher: ConStatusFn? = null

    companion object {
        const val REFRESH_INTERVAL = 300L
    }


    fun populateQuery(pos: Int, query: Display): TorqueData {
        data[pos]?.stopRefreshing(true)
        val td = TorqueData(query)
        data[pos] = td
        Timber.i("Setting query: $query for pos $pos")
        return td
    }

    fun makeExecutors(service: TorqueService) {
        var foundValid = false
        data.values.forEachIndexed { index, torqueData ->
            val refreshOffset = (REFRESH_INTERVAL / data.size) * index
            if (torqueData.pid != null) {
                foundValid = true
                if (torqueData.refreshTimer == null) {
                    Timber.i("Scheduled item in position $index with $refreshOffset delay")
                    doRefresh(service, torqueData)
                    torqueData.refreshTimer = executor.scheduleWithFixedDelay({
                        try {
                            doRefresh(service, torqueData)
                        } catch (e: Exception) {
                            Timber.e("Refresh failed in pos $index", e)
                        }
                    }, refreshOffset, REFRESH_INTERVAL, TimeUnit.MILLISECONDS)
                }
            } else {
                Timber.i("No reason to schedule item in position $index")
            }
        }
        conWatcher?.invoke(if (foundValid) lastConnectStatus else ConnectStatus.SETUP_GAUGE)
    }

    fun doRefresh(service: TorqueService, torqueData: TorqueData) {
        service.runIfConnected { ts ->
            var value = try {
                 ts.getPIDValuesAsDouble(arrayOf(torqueData.pid!!))[0]
            } catch (e: ArrayIndexOutOfBoundsException) {
                Timber.e("Torque returned invalid data")
                return@runIfConnected
            }
            torqueData.lastData = value
            Timber.d("Got valid $value from torque for ${torqueData.display.label}")
            if (value != 0.0 || torqueData.hasReceivedNonZero) {
                torqueData.hasReceivedNonZero = true
                handler.post {
                    torqueData.sendNotifyUpdate()
                    if (value != 0.0 && lastConnectStatus != ConnectStatus.CONNECTED) {
                        lastConnectStatus = ConnectStatus.CONNECTED
                        conWatcher?.let { it(ConnectStatus.CONNECTED) }
                    }
                }
            }
        }
    }

    fun stopExecutors() {
        Timber.i("Telling Torque refreshers to stop")
        for (td in data.values) {
            td.stopRefreshing()
        }
    }

    fun hasChanged(idx: Int, otherScreen: Display?): Boolean {
        if (!data.containsKey(idx)) return true
        return data[idx]?.display?.equals(otherScreen) != true
    }

    fun watchConnection(service: TorqueService, notifyConState: ConStatusFn) {
        notifyConState(lastConnectStatus)
        service.addConnectCallback {
            if (lastConnectStatus == ConnectStatus.CONNECTING_TORQUE) {
                lastConnectStatus = ConnectStatus.CONNECTING_ECU
                notifyConState(lastConnectStatus)
            }
            conWatcher = notifyConState
        }
    }

}
