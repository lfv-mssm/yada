package com.arkatay.yada.codec;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;
import org.xiph.speex.SpeexEncoder;

/**
 * <p>
 * The JSpeexEncoder extends the AudioEncoder and adds functionality for encoding
 * frames using the JSpeex voice codec. See www.speex.org and http://jspeex.sourceforge.net
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
public class JSpeexEncoder extends AudioEncoder implements JSpeexConstants {

    private SpeexEncoder encoder;
    private int quality;

    private static AudioFormat[] formats = {
        new AudioFormat(SAMPLERATE_WIDEBAND,16,CHANNELS_MONO,true,false),
        new AudioFormat(SAMPLERATE_WIDEBAND,16,CHANNELS_STEREO,true,false)
    };

    /**
     * Creates a new instance of JSpeexEncoder with the specified quality. Valid
     * quality values are between 1 and 10 where 10 is the highest quality
     * producing the largest data packets.
     *
     * @param quality a value between 1 and 10
     */
    public JSpeexEncoder(Mixer mixer, int quality) {
        super(mixer);
        this.quality = quality;
        encoder = new SpeexEncoder();
        encoder.init(MODE_WIDEBAND, quality, SAMPLERATE_WIDEBAND, CHANNELS_MONO);
    }

    public AudioFormat[] getSupportedAudioFormats() {
        return formats;
    }

    protected void init() {
        log.debug("Initializing jspeex encoder with quality "+quality);

        // Let processData setup static variables if a number of different
        // frames aren't processed initially, the encoding step will take
        // more than 100ms per frame... stupid encoder...
        byte[] b1 = new byte[640];
        byte[] b2 = new byte[1024];
        for(int i=0;i<128;i++) {
            if(i<96) {
                // Fill up with random data
                for(int j=0;j<b1.length;j++)
                    b1[j] = (byte)(Math.random()*255.0);
            }
            else {
                // Fill up with silence
                for(int j=0;j<b1.length;j++)
                    b1[j] = (byte)0;
            }
            encoder.processData(b1,0,b1.length);
            encoder.getProcessedData(b2, 0);
        }
    }

    protected int encodeFrame(byte[] captured, int length, byte[] encoded, int offset) {
        if(length<(2*FRAMESAMPLES_WIDEBAND))
            return 0;

        // Convert stereo to mono (just take the left channel)
        if(audioFormatIndex==1) {
            byte b0,b1;
            int j=0;
            for(int i=0;i<length;i+=4) {
                // Get a 16 bit sample from the left channel
                b0 = captured[i+0];
                b1 = captured[i+1];

                // Store it in encoded buffer
                captured[j+0] = b0;
                captured[j+1] = b1;

                j+=2;
            }
            length /= 2;
        }

        // Now it can be encoded
        encoder.processData(captured,0,length);
        return encoder.getProcessedData(encoded, offset);
    }
}
