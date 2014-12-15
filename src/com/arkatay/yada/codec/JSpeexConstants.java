package com.arkatay.yada.codec;

/**
 * <p>
 * Constants used by the JSpeex encoder and decoder.
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
public interface JSpeexConstants {
    public int MODE_NARROWBAND = 0;
    public int MODE_WIDEBAND = 1;
    public int MODE_ULTRAWIDEBAND = 2;

    public int QUALITY_0  = 0;
    public int QUALITY_1  = 1;
    public int QUALITY_2  = 2;
    public int QUALITY_3  = 3;
    public int QUALITY_4  = 4;
    public int QUALITY_5  = 5;
    public int QUALITY_6  = 6;
    public int QUALITY_7  = 7;
    public int QUALITY_8  = 8;
    public int QUALITY_9  = 9;
    public int QUALITY_10 = 10;

    public int SAMPLERATE_NARROWBAND = 8000;
    public int SAMPLERATE_WIDEBAND = 16000;
    public int SAMPLERATE_ULTRAWIDEBAND = 32000;

    public int FRAMESAMPLES_NARROWBAND = 160;
    public int FRAMESAMPLES_WIDEBAND = 320;
    public int FRAMESAMPLES_ULTRAWIDEBAND = 640;

    public int CHANNELS_MONO = 1;
    public int CHANNELS_STEREO = 2;
}
