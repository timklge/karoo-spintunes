package de.timklge.karoospintunes

import de.timklge.karoospintunes.datatypes.PlayerDataType
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class KarooSpintunesExtension : KarooExtension("karoo-spintunes", BuildConfig.VERSION_NAME) {
    companion object {
        const val TAG = "karoo-spintunes"
    }

    private val servicesProvider: KarooSpintunesServices = KarooSpintunesServices(
        get(), get(), get(), get(), get(), get(), get(), get(), get())
    private val playerDataType: PlayerDataType by inject()

    override val types: List<DataTypeImpl> = listOf(playerDataType)

    override fun onCreate() {
        servicesProvider.startJobs()
        super.onCreate()
    }

    override fun onDestroy() {
        servicesProvider.cancelJobs()
        super.onDestroy()
    }
}