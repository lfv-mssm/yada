package com.arkatay.yada.codec;

import java.io.StreamCorruptedException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;

/**
 * <p>
 * The NullDecoder extends the AudioDecoder and adds functionality for passing
 * through the audio data. No decompression is done with this decoder.
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
public class NullDecoder extends AudioDecoder {

    private static AudioFormat[] formats = {
        new AudioFormat(16000.0f,16,1,true,false),
        new AudioFormat(16000.0f,16,2,true,false)
    };

    /**
     * Creates a new instance of NullDecoder
     *
     */
    public NullDecoder(Mixer mixer, int decoderId) {
        super(mixer, decoderId);
    }

    public AudioFormat[] getSupportedAudioFormats() {
        return formats;
    }

    protected int concealFrame(byte[] decoded) {
        // The NullDecoder does not perform any concealment
        return lastDecodedFrameSize;
    }

    protected int decodeFrame(int dataType, byte[] encoded, int offset, int length, byte[] decoded) throws StreamCorruptedException {
        // The NullDecoder just copies the data

        // Mono
        if(audioFormatIndex==0) {
            System.arraycopy(encoded,offset,decoded,0,length);
            return length;
        }

        // Convert mono to stereo
        else {
            byte b0,b1;
            int i=offset;
            for(int j=0;j<length*2;j+=4) {
                // Get the 16 bit mono sample
                b0 = encoded[i+0];
                b1 = encoded[i+1];

                // Copy sample to left channel
                decoded[j+0] = b0;
                decoded[j+1] = b1;

                // Copy sample to right channel
                decoded[j+2] = b0;
                decoded[j+3] = b1;

                i+=2;
            }
            return length*2;
        }
    }
}
