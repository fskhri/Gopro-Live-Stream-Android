package com.gopro.rebuild.di

import android.content.Context
import com.gopro.rebuild.ble.GoProScanner
import com.gopro.rebuild.media.MediaDownloader
import com.gopro.rebuild.media.PreviewPlayer
import com.gopro.rebuild.media.GoProUdpRelay
import com.gopro.rebuild.media.RtmpStreamer
import com.gopro.rebuild.wifi.GoProWifiApi
import com.gopro.rebuild.wifi.WifiConnectionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGoProScanner(
        @ApplicationContext context: Context
    ): GoProScanner = GoProScanner(context)

    @Provides
    @Singleton
    fun provideGoProWifiApi(
        wifiConnectionManager: WifiConnectionManager
    ): GoProWifiApi = GoProWifiApi(wifiConnectionManager)

    @Provides
    @Singleton
    fun provideWifiConnectionManager(
        @ApplicationContext context: Context
    ): WifiConnectionManager = WifiConnectionManager(context)

    @Provides
    @Singleton
    fun providePreviewPlayer(
        @ApplicationContext context: Context,
        wifiConnectionManager: WifiConnectionManager
    ): PreviewPlayer = PreviewPlayer(context, wifiConnectionManager)

    @Provides
    @Singleton
    fun provideMediaDownloader(
        @ApplicationContext context: Context
    ): MediaDownloader = MediaDownloader(context)

    @Provides
    @Singleton
    fun provideTiktokRepository(
        @ApplicationContext context: Context
    ): com.gopro.rebuild.tiktok.TiktokRepository = com.gopro.rebuild.tiktok.TiktokRepository(context)

    @Provides
    @Singleton
    fun provideTiktokService(
        repository: com.gopro.rebuild.tiktok.TiktokRepository
    ): com.gopro.rebuild.tiktok.TiktokService = com.gopro.rebuild.tiktok.TiktokService(repository)

    @Provides
    @Singleton
    fun provideGoProUdpRelay(
        wifiConnectionManager: WifiConnectionManager
    ): GoProUdpRelay = GoProUdpRelay(wifiConnectionManager)

    @Provides
    @Singleton
    fun provideRtmpStreamer(
        udpRelay: GoProUdpRelay
    ): RtmpStreamer = RtmpStreamer(udpRelay)
}
