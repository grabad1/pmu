package rs.etf.focusguard.hilt

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.etf.focusguard.sensors.MicrophoneNoiseSource
import rs.etf.focusguard.sensors.NoiseSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    @Binds
    @Singleton
    abstract fun bindsNoiseSource(source: MicrophoneNoiseSource): NoiseSource
}
