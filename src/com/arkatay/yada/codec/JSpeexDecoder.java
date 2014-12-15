package com.arkatay.yada.codec;

import java.io.StreamCorruptedException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import org.xiph.speex.SpeexDecoder;

/**
 * <p>
 * The JSpeexDecoder extends the AudioDecoder and adds functionality for decoding
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
public class JSpeexDecoder extends AudioDecoder implements JSpeexConstants {

    private SpeexDecoder decoder;
    private byte[] monoToStereoConversionBuffer;

    private static AudioFormat[] formats = {
        new AudioFormat(SAMPLERATE_WIDEBAND,16,CHANNELS_MONO,true,false),
        new AudioFormat(SAMPLERATE_WIDEBAND,16,CHANNELS_STEREO,true,false)
    };

    public JSpeexDecoder(Mixer mixer, int decoderId) {
        super(mixer, decoderId);
        decoder = new SpeexDecoder();
        decoder.init(MODE_WIDEBAND, SAMPLERATE_WIDEBAND, CHANNELS_MONO, true);
    }

    public AudioFormat[] getSupportedAudioFormats() {
        return formats;
    }

    @Override
    public void startModule(int audioFormatIndex) throws LineUnavailableException {
        super.startModule(audioFormatIndex);

        // Stereo
        if(audioFormatIndex==1)
            monoToStereoConversionBuffer = new byte[decodedFrameSizeInBytes/2];
    }

    protected int concealFrame(byte[] decoded) {
        try {
            decoder.processData(false);
        } catch (StreamCorruptedException ex) {
            // Cant really happen since there is no stream
            return lastDecodedFrameSize;
        }
        return decoder.getProcessedData(decoded, 0);
    }

    protected int decodeFrame(int dataType, byte[] encoded, int offset, int length, byte[] decoded) throws StreamCorruptedException {
        decoder.processData(encoded,offset,length);

        // Mono
        if(audioFormatIndex==0)
            return decoder.getProcessedData(decoded, 0);

        // Convert mono to stereo
        else {
            int decodedLength = decoder.getProcessedData(monoToStereoConversionBuffer, 0);
            byte b0,b1;
            int i=0;
            for(int j=0;j<decodedLength*2;j+=4) {
                // Get the 16 bit mono sample
                b0 = monoToStereoConversionBuffer[i+0];
                b1 = monoToStereoConversionBuffer[i+1];

                // Copy sample to left channel
                decoded[j+0] = b0;
                decoded[j+1] = b1;

                // Copy sample to right channel
                decoded[j+2] = b0;
                decoded[j+3] = b1;

                i+=2;
            }
            return decodedLength*2;
        }
    }
}
