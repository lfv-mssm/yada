package com.arkatay.yada.codec;

/**
 * <p>
 * The AudioCodecConstants interface contains settings for the encoder and the
 * decoder. The values are chosen to make the codec stable on most systems but
 * may be modified if codec problems occur.
 * <p>
 * Copyright &copy; LFV 2006, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:info@arkatay.com">Andreas Alptun</a>
 * @version Yada 1.0
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
public interface AudioCodecConstants {

    public static final int   jitterBufferSizeDef = 4;
    public static final int   jitterBufferSizeMax = 20;
    public static final int   jitterBufferSizeMin = 1;

    public static final float outputBufferSizeDef = 3.5f;
    public static final float outputBufferSizeMax = 4.0f;
    public static final float outputBufferSizeMin = 1.1f;

    public static final float inputBufferSizeDef  = 3.5f;
    public static final float inputBufferSizeMax  = 3.9f;
    public static final float inputBufferSizeMin  = 1.1f;

    /**
     * The maximum difference time in milliseconds between two executions before
     * the codec is reset.
     */
    public static final int diffTimeMillisLimit = 500;

    /**
     * The maximum number of consecutive negative sleeps before the codec is
     * reset.
     */
    public static final int consecNegativeSleepLimit = 12;

    /**
     * The maximum number of packets in the jitterbuffer used on the adjustment
     * slider.
     */
    public static final int jitterBufferSliderUpperSizeInPackets = 20;

    /**
     * The minimum number of packets in the jitterbuffer used on the adjustment
     * slider.
     */
    public static final int jitterBufferSliderLowerSizeInPackets = 2;

    /**
     * The initial number of packets in the jitterbuffer if the size hasn't been
     * adjusted manually using the slider
     */
    public static final int jitterBufferSliderInitSizeInPackets = 5;

    /**
     * The number of packets in the playout buffer. At each execution the playout
     * buffer will be filled up to this value from data taken from the jitter
     * buffer.
     */
    public static final int playoutBufferSizeInPackets = 3;

    /**
     * The maximum number of consecutive lost packets before the decoder is
     * assuming end of stream hence going back to idle state
     */
    public static final int consecLostPacketsLimit = 25;

    /**
     * The maximum number of consecutive broken writes, i.e. writes that
     * return less written bytes than requested
     */
    public static final int consecBrokenWritesLimit = 10;

    /**
     * The constant used to calculate the upper limit of the jitter buffer. This
     * value is added to the product of the number of packet in the jitter buffer
     * and jitterBufferUpperLimitFactor.
     * @see AudioCodecConstants#jitterBufferUpperLimitFactor
     */
    public static final int jitterBufferUpperLimitConstant = 2;

    /**
     * The factor used to calculate the upper limit of the jitter buffer. This value
     * is multiplied with the number of packets in the jitter buffer to get the
     * maximum value before packets are thrown out
     * @see AudioCodecConstants#jitterBufferUpperLimitConstant
     */
    public static final double jitterBufferUpperLimitFactor = 1.32;


    public static final int TIMEOUT_WAITIDLE = 2000;

    // Dont touch
    public static final long millisToNanos = 1000000L;
}
