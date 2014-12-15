package com.lfv.lanzius.audio;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;

/**
 * <p>
 * NoiseDataLine
 * <p>
 * Copyright &copy; LFV 2008, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2.1.4 (Lanzius)
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
public class MixingDataLine extends TargetDataLineAdapter {

    private List<Channel> channelList;
    private int[]         mixBuffer;

    private boolean       started;

    public MixingDataLine() {
        channelList = new ArrayList<Channel>(32);
    }

    public Channel getMixerChannel() {
        Channel c = new Channel();
        channelList.add(c);
        return c;
    }

    @Override
    public void open(AudioFormat format, int bufferSize) throws LineUnavailableException {
        super.open(format, bufferSize);
        // Create mix result buffer
        mixBuffer = new int[bufferSize>>1];
        started = false;
    }

    @Override
    public void start() {
        if(open)
            started = true;
    }

    @Override
    public void stop() {
        started = false;
    }

    @Override
    public boolean isRunning() {
        return (open&&started);
    }

    @Override
    public int available() {
        // Always one packet available if line is started
        return (open&&started)?640:0;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
        int i,j,length2;

        // Round down to even (16 bit samples)
        length &= ~1;

        // Clamp length just to be sure
        if(length>bufferSize)
            length = bufferSize;

        // Length of mix buffer is halv the size of the read buffer
        length2 = length>>1;

        // Clear the requested length of the mix result buffer
        for(i=0;i<length2;i++) {
            mixBuffer[i] = 0;
        }

        // Go through the channels and add them to the mix buffer
        Iterator<Channel> iter = channelList.iterator();
        while(iter.hasNext()) {
            Channel c = iter.next();
            c.mix(mixBuffer, length);
        }

        // Clamp and copy to destination array
        for(i=0,j=offset;i<length2;i++) {
            int sample = mixBuffer[i];

            if(sample> 32767) sample =  32767;
            if(sample<-32768) sample = -32768;

            buffer[j  ] = (byte)((sample   )&0xff);
            buffer[j+1] = (byte)((sample>>8)&0xff);
            j+=2;
        }

        return length;
    }

    @Override
    public void flush() {
        // Go through the channels and flush them
        Iterator<Channel> iter = channelList.iterator();
        while(iter.hasNext()) {
            Channel c = iter.next();
            c.flush();
        }
    }

    public class Channel {

        // Must be power of two
        private final int BUFFER_SIZE = 4096;
        private final int BUFFER_MASK = 4095;

        private final int BUFFER_MAX  = 1048576;
        private final int BUFFER_WRAP = 250; //(250*4096=1024000)

        private boolean active;
        private int     readIndex;
        private int     writeIndex;
        private byte[]  channelBuffer;

        private Channel() {
            if((BUFFER_SIZE&(BUFFER_SIZE-1))!=0)
                throw new RuntimeException("BUFFER_SIZE must be power of two!");
            channelBuffer = new byte[BUFFER_SIZE];
        }

        private synchronized void mix(int[] mixBuffer, int length) {

            int available = writeIndex-readIndex;

            // If buffer is inactive wait until 50% full
            if(!active) {
                if(available >= (BUFFER_SIZE/2)) {
                    // Drain to 50%
                    readIndex = writeIndex - (BUFFER_SIZE/2);
                    // and activate channel
                    active = true;
                }
            }

            // Mix channelBuffer to mixBuffer if channel is active
            if(active) {
                int i,sampleLo,sampleHi;

                // Clip to channel buffer size
                if(length>available) {
                    length = available;
                }

                // Mix samples
                length >>= 1;
                for(i=0;i<length;i++) {
                    sampleLo = channelBuffer[(readIndex  )&BUFFER_MASK];
                    sampleHi = channelBuffer[(readIndex+1)&BUFFER_MASK];
                    mixBuffer[i] += (sampleHi<<8) | (sampleLo&0xff);
                    readIndex += 2;
                }

                // If buffer is empty, deactivate channel
                available = writeIndex-readIndex;
                if(available<2) {
                    readIndex  = 0;
                    writeIndex = 0;
                    active     = false;
                }
            }
        }

        public synchronized int write(byte[] buffer, int offset, int length) {

            // Dont write if data line is not started or length is negative
            if(!started || length<=0)
                return 0;

            // Round down to even (16 bit samples)
            length &= ~1;

            // Clip to fit, If overflow move readIndex to get latest samples
            int available = BUFFER_SIZE-(writeIndex-readIndex);
            if(length>available) {
                readIndex = writeIndex + length - BUFFER_SIZE;
            }

            // copy input buffer to circular channel buffer
            for(int i=0;i<length;i++) {
                channelBuffer[(writeIndex+i)&BUFFER_MASK] = buffer[offset+i];
            }

            // Move write index
            writeIndex += length;

            // Move indices down to lower range if growing too large
            if(writeIndex >= BUFFER_MAX) {
              int d = BUFFER_WRAP*BUFFER_SIZE;
              writeIndex -= d;
              readIndex  -= d;
            }

            return length;
        }

        private synchronized void flush() {
            writeIndex = 0;
            readIndex  = 0;
            active = false;
        }
    }
}
