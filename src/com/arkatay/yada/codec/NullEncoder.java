package com.arkatay.yada.codec;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;

/**
 * <p>
 * The NullEncoder extends the AudioEncoder and adds functionality for passing
 * through the audio data. No compression is done with this encoder.
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
public class NullEncoder extends AudioEncoder {

    private static AudioFormat[] formats = {
        new AudioFormat(16000.0f,16,1,true,false),
        new AudioFormat(16000.0f,16,2,true,false),
        new AudioFormat(8000.0f,16,1,true,false),
        new AudioFormat(8000.0f,8,1,true,false),
        new AudioFormat(8000.0f,16,1,false,false),
        new AudioFormat(8000.0f,16,1,true,true),
        new AudioFormat(8000.0f,16,1,true,true)
    };

    public NullEncoder(Mixer mixer) {
        super(mixer);
    }

    public AudioFormat[] getSupportedAudioFormats() {
        return formats;
    }

    protected void init() {
    }

    protected int encodeFrame(byte[] captured, int length, byte[] encoded, int offset) {
        if(length<=0)
            return 0;

        // Mono
        if(audioFormatIndex==0) {
            System.arraycopy(captured,0,encoded,offset,length);
            return length;
        }

        // Convert stereo to mono (just take the left channel)
        else {
            byte b0,b1;
            int j=offset;
            for(int i=0;i<length;i+=4) {
                // Get a 16 bit sample from the left channel
                b0 = captured[i+0];
                b1 = captured[i+1];

                // Store it in encoded buffer
                encoded[j+0] = b0;
                encoded[j+1] = b1;

                j+=2;
            }

            return length/2;
        }
    }
}
