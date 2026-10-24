package com.gopro.rebuild.media

import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.ts.TsPayloadReader

/**
 * GoPro Hero (6 Black and likely 5/7) preview PMTs declare a private stream
 * (`stream_type 0x80`) on a PID that never carries any payload packets.
 *
 * media3's [DefaultTsPayloadReaderFactory] maps stream_type 0x80 to an MPEG-2
 * video reader (`case 2: case 128: return new H262Reader(...)`). That registers
 * an [androidx.media3.exoplayer.source.SampleQueue] which never receives a
 * Format, so [androidx.media3.exoplayer.source.ProgressiveMediaPeriod]'
 * prepare loop (`for (SampleQueue q : sampleQueues) if (q.getUpstreamFormat()
 * == null) return;`) can never complete. The player then errors with
 * "Loading finished before preparation is complete" and the preview stays black.
 *
 * Dropping the 0x80 stream leaves only the real H.264 + AAC tracks, which
 * prepare normally.
 */
@UnstableApi
class GoProTsExtractorFactory : ExtractorsFactory {

    override fun createExtractors(): Array<Extractor> = arrayOf(
        TsExtractor(
            TsExtractor.MODE_SINGLE_PMT,
            TimestampAdjuster(0L),
            GoProTsPayloadReaderFactory(DefaultTsPayloadReaderFactory(0)),
            TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES
        )
    )
}

/**
 * Wraps a [TsPayloadReader.Factory] but refuses to build a reader for the
 * GoPro private stream type, so no phantom track is registered for it.
 */
@UnstableApi
private class GoProTsPayloadReaderFactory(
    private val delegate: TsPayloadReader.Factory
) : TsPayloadReader.Factory {

    override fun createInitialPayloadReaders() = delegate.createInitialPayloadReaders()

    override fun createPayloadReader(
        streamType: Int,
        esInfo: TsPayloadReader.EsInfo
    ): TsPayloadReader? {
        if (streamType == GOPRO_PRIVATE_STREAM_TYPE) return null
        return delegate.createPayloadReader(streamType, esInfo)
    }

    private companion object {
        /** GoPro preview private stream type (declared on PID 0x200, no payload). */
        const val GOPRO_PRIVATE_STREAM_TYPE = 0x80
    }
}
