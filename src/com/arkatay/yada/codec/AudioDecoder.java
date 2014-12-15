package com.arkatay.yada.codec;

import com.arkatay.yada.base.Time;
import com.lfv.lanzius.application.VolumeAdjustable;
import com.lfv.lanzius.audio.MixingDataLine;
import java.io.StreamCorruptedException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.DataLine;
import com.lfv.yada.net.AttributeData;
import com.lfv.yada.net.Packet;

/**
 * <p>
 * The AudioDecoder class is an abstract class that needs to be extended with a
 * decodeFrame and a concealFrame method. The conceal frame method builds up a
 * packet without any encoded data. This function is called when packet loss has
 * occurred and a voice frame must be passed to the output line. The
 * decoder inherits from JitterBuffer which allows other modules to
 * post packets to the decoder. Data packets are posted by the ClientPacketReceiver
 * object. The decoder uses the dispatcher thread to execute at 20ms intervals.
 * Because the underlying system scheduler is not exact the interval may vary a
 * bit from 20 ms in some cases, but the mean value is exactly 20ms according to
 * the system clock.
 * <p>
 * Copyright &copy; LFV 2006, <a href="http://www.lfv.se">www.lfv.se</a>
 *
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
public abstract class AudioDecoder extends JitterBuffer implements AudioCodecConstants, VolumeAdjustable {

    private int decoderId;

    // Output line
    private Mixer mixer;
    private SourceDataLine outputLine;
    private int outputLineBufferSize;
    protected int audioFormatIndex;

    // Decoding and playing
    protected int lastDecodedFrameSize;
    private long startTimeInNanos;
    private int jitterBufferSizeInPackets;
    private int jitterBufferUpperLimitInPackets;
    private int expectedSequenceNbr;
    private boolean forceStop;
    private boolean hasWritten;
    private boolean firstRun;

    // Monitor mixing
    MixingDataLine.Channel monitorChannel;

    // Buffers
    private byte[] decodeBuffer;
    private byte[] fillupBuffer;
    protected int decodedFrameSizeInBytes;
    protected long decodedFrameSizeInNanos;

    // Fault handling
    private int nbrConsecBrokenWrites;
    private int nbrConsecNegativeSleep;
    private int nbrConsecLostPackets;
    private long diffTimeNanosLimit;

    // Misc
    private int[] channelIdArray;
    private long nextWriteTimeInNanos;
    private List<AudioDecoderListener> listeners;
    private AudioRecorder audioRecorder;

    // States
    private static final int STATE_OFF   = 0;
    private static final int STATE_IDLE  = 1;
    private static final int STATE_RUN   = 2;
    private static final int STATE_STOP  = 3;
    private int state;

    public abstract AudioFormat[] getSupportedAudioFormats();

    private static float constOutputBufferSize = outputBufferSizeDef;
    public static float setOutputBufferSize(float outputBufferSize) {
        if(outputBufferSize<outputBufferSizeMin) outputBufferSize = outputBufferSizeMin;
        if(outputBufferSize>outputBufferSizeMax) outputBufferSize = outputBufferSizeMax;
        constOutputBufferSize = outputBufferSize;
        return outputBufferSize;
    }

    private static int constJitterBufferSize = jitterBufferSizeDef;
    public static int setJitterBufferSize(int jitterBufferSize) {
        if(jitterBufferSize<jitterBufferSizeMin) jitterBufferSize = jitterBufferSizeMin;
        if(jitterBufferSize>jitterBufferSizeMax) jitterBufferSize = jitterBufferSizeMax;
        constJitterBufferSize = jitterBufferSize;
        return jitterBufferSize;
    }

    protected AudioDecoder(Mixer mixer, int decoderId) {
        super("Tdecoder", 64);
        this.mixer = mixer;
        this.decoderId = decoderId;
        listeners = new LinkedList<AudioDecoderListener>();
        thread.setPriority((Thread.NORM_PRIORITY+Thread.MAX_PRIORITY)/2);
        // Start in state off
        state = STATE_OFF;
    }

    @Override
    public void startModule() {
        throw new UnsupportedOperationException("This method has been overridden with startModule(int audioFormatIndex)");
    }

    public void startModule(int audioFormatIndex) throws LineUnavailableException {
        decodedFrameSizeInNanos = 20L*millisToNanos;

        // Check bounds
        AudioFormat[] audioFormats = getSupportedAudioFormats();
        if(audioFormatIndex<0 || audioFormatIndex>=audioFormats.length)
            throw new LineUnavailableException("Audio format array out of bounds");

        // Setup line
        AudioFormat audioFormat = audioFormats[audioFormatIndex];
        this.outputLine = createLine(audioFormat);
        this.audioFormatIndex = audioFormatIndex;

        // Calculate stuff
        decodedFrameSizeInBytes = (int)(audioFormat.getFrameRate()*audioFormat.getFrameSize()*decodedFrameSizeInNanos/(1000*millisToNanos));
        diffTimeNanosLimit = diffTimeMillisLimit*millisToNanos;

        jitterBufferSizeInPackets = constJitterBufferSize;
        jitterBufferUpperLimitInPackets = jitterBufferUpperLimitConstant+(int)Math.round(jitterBufferSizeInPackets*jitterBufferUpperLimitFactor);
        log.debug("Setting jitter buffer size to "+jitterBufferSizeInPackets+" and maximum size to "+jitterBufferUpperLimitInPackets);

        // Open the output line, the wanted buffer size is N times as big as the frame size
        outputLine.open(audioFormat, 4*decodedFrameSizeInBytes);
        outputLineBufferSize = outputLine.getBufferSize();
        log.debug("Output line is open with buffer size "+outputLineBufferSize);

        // Create a buffer for the decoded frame
        decodeBuffer = new byte[decodedFrameSizeInBytes];
        fillupBuffer = new byte[decodedFrameSizeInBytes];

        super.startModule();
    }

    public void addListener(AudioDecoderListener listener) {
        listeners.add(listener);
    }

    public void removeListener(AudioDecoderListener listener) {
        listeners.remove(listener);
    }

    public void setAudioRecorder(AudioRecorder audioRecorder) {
        this.audioRecorder = audioRecorder;
    }

    public void setMonitorChannel(MixingDataLine.Channel monitorChannel) {
        this.monitorChannel = monitorChannel;
    }

    public int getDecoderId() {
        return decoderId;
    }

    /**
     * Stopping the decoder even though the talk spurt is not ended. If the
     * input channel port is still open for this decoder the decoder will be
     * instantly restarted.
     *
     */
    public void forceStop() {
        forceStop = true;
    }

    /**
     * Conceals one frame of voice data when the encoded source data is missing.
     *
     * @param decoded an empty byte array to which the concealed frame should be written
     * @return size of the decoded frame in bytes
     */
    protected abstract int concealFrame(byte[] decoded);

    /**
     * Decodes one frame of voice data.
     *
     * @param dataType reserved for future extensions, not used
     * @param encoded a byte array containing encoded voice data for one captured frame
     * @param offset the offset to start reading from in the encoded byte array
     * @param length the length of the encoded data in bytes
     * @param decoded an empty byte array to which the decoded frame should be written
     * @return size of the decoded frame in bytes
     */
    protected abstract int decodeFrame(int dataType, byte[] encoded, int offset, int length, byte[] decoded) throws StreamCorruptedException;

    @Override
    protected final void threadStart() throws InterruptedException {
        // Start the output line
        outputLine.start();

        // Go to idle state and wait for incoming packet on the queue
        state=STATE_IDLE;
    }

    @Override
    protected final void threadStop() throws InterruptedException {
        // Stop and close
        if(outputLine!=null) {
            if(hasWritten)
                outputLine.flush();
            if(outputLine.isRunning())
                outputLine.stop();
            if(outputLine.isOpen())
                outputLine.close();
            outputLine = null;
        }
    }

    protected boolean threadMain() throws InterruptedException {
        if(outputLine==null) {
            log.error("Output line is null, stopping decoder module!");
            return false;
        }

        if(state==STATE_IDLE) {

            // Clear the packet queue
            clear();

            // Flush the output line to be sure that it is empty
            // Only flush if anything has ever been written to the
            // output line. This patches a problem with Intel HDA
            // soundcards when using Linux.
            if(hasWritten)
                outputLine.flush();

            // Mute fillup buffer
            for(int k=0;k<decodedFrameSizeInBytes;k++)
                fillupBuffer[k] = 0;

            // Get the first packet from the queue
            waitItem();

            // Set start time
            startTimeInNanos = Time.getNanos();

            // Go to state run
            state = STATE_RUN;
            forceStop = false;
            firstRun = true;

            // Take a look at the packet
            Packet packet = (Packet)peekItem();

            // If the packet is a reset packet
            if(checkReset(packet)) {
                // Stay in idle state
                state = STATE_IDLE;
                return true;
            }

            log.debug("Audio packet received in idle state, starting decoder process");

            // Setup jitter buffer filter to allow only packets from the initiating terminal
            setFilter(packet.getSourceId());

            // Get the sequence number
            expectedSequenceNbr = packet.getSequenceNbr();

            // Store the channel collection locally
            channelIdArray = packet.getAttributeList(Packet.ATTR_CHANNEL_LIST);
            if(log.isDebugEnabled())
                log.debug("DecoderId: "+decoderId+", channel list: "+Arrays.toString(channelIdArray));

            // Call registred listeners
            Iterator<AudioDecoderListener> iter = listeners.iterator();
            while(iter.hasNext()) {
                AudioDecoderListener listener = iter.next();
                listener.startDecoding(this, channelIdArray);
            }

            // Setup variables
            lastDecodedFrameSize   = decodedFrameSizeInBytes;
            nbrConsecBrokenWrites  = 0;
            nbrConsecNegativeSleep = 0;
            nbrConsecLostPackets   = 0;

            // Run again after the fill-up time
            nextWriteTimeInNanos = startTimeInNanos + (jitterBufferSizeInPackets-1)*decodedFrameSizeInNanos + (decodedFrameSizeInNanos>>2);

        }
        else if(state==STATE_RUN) {
            long currentTimeInNanos = Time.getNanos();
            long diffTimeInNanos = currentTimeInNanos-nextWriteTimeInNanos;

            if(log.isTraceEnabled()) {
                log.trace("Running at "+((currentTimeInNanos-startTimeInNanos)/millisToNanos)+"ms, expected: "+((nextWriteTimeInNanos-startTimeInNanos)/millisToNanos)+"ms, Number of packets in queue: "+getSize());
                super.print();
            }

            // Check for force stop flag
            if(forceStop) {
                log.debug("Decoder forced to stop, stopping");
                state = STATE_STOP;
                forceStop = false;
                return true;
            }

            // Get expected packet from jitter buffer
            Packet packet = getPacket(expectedSequenceNbr);
            expectedSequenceNbr++;

            // Conceal frame
            if(packet==null) {
                // If too many packets are consecutively lost, assume it is
                // the end of a talk spurt and go back to idle state
                nbrConsecLostPackets++;
                if(nbrConsecLostPackets > consecLostPacketsLimit) {
                    if(log.isDebugEnabled())
                        log.debug("Number of consecutive lost packets above limit, stopping decoder, decoderId: "+decoderId);
                    state = STATE_STOP;
                    return true;
                }

                // Missing packet, something needs to be outputted anyway
                // Let the subclass implementation take care of what to output
                // Only conceal the first three frames, then output silence
                if(nbrConsecLostPackets<=3) {
                    lastDecodedFrameSize = concealFrame(decodeBuffer);
                }
                else {
                    for(int i=0;i<decodedFrameSizeInBytes;i++)
                        decodeBuffer[i] = 0;
                }
            }

            // Decode frame
            else {
                // Clear lost packet count
                nbrConsecLostPackets = 0;

                // Check reset flag
                if(checkReset(packet)) {
                    if(log.isDebugEnabled())
                        log.debug("Packet received with reset flag set, stopping decoder, decoderId: "+decoderId);
                    state = STATE_STOP;
                    return true;
                }

                // Get the encoded data load from the packet
                AttributeData data = packet.getAttributeData();
                if(data==null) {
                    log.error("Decoder received a packet without a data load, stopping decoder, decoderId: "+decoderId);
                    state = STATE_STOP;
                    return true;
                }

                // Decode the packet
                try {
                    lastDecodedFrameSize = decodeFrame(data.getDataType(), data.getBuffer(), data.getOffset(), data.getLength(), decodeBuffer);
                } catch(StreamCorruptedException ex) {
                    log.warn("Corrupted data in packet ", ex);
                    // Conceal the packet instead
                    lastDecodedFrameSize = concealFrame(decodeBuffer);
                }
            }

            if(lastDecodedFrameSize!=decodedFrameSizeInBytes) {
                log.error("Decoded or concealed frame contains wrong number of bytes ("+lastDecodedFrameSize+"), stopping decoder, decoderId: "+decoderId);
                state = STATE_STOP;
                return true;
            }

            float lowerBuffer = 0.4444f*constOutputBufferSize-0.7778f;
            if(lowerBuffer<0.05f) lowerBuffer = 0.05f;
            float upperBuffer = constOutputBufferSize - lowerBuffer;

            int bytesToWrite = decodedFrameSizeInBytes;
            int availableBytes = outputLineBufferSize-outputLine.available();
            if(availableBytes<0) availableBytes=0;

            if(log.isDebugEnabled())
                log.debug("dec>                               avail: "+availableBytes+" ("+(int)(lowerBuffer*decodedFrameSizeInBytes)+"-"+(int)((constOutputBufferSize-1.0f)*decodedFrameSizeInBytes)+") jitter: "+getSize());

            // Must skip if max is reached
            if(availableBytes>=outputLineBufferSize)
                availableBytes = 100000;

            // Fill up output buffer to wanted level
            if(firstRun) {
                int wantedLevel = (int)((upperBuffer*0.5f + lowerBuffer - 0.5f)*decodedFrameSizeInBytes);
                int bytesToFill = wantedLevel-availableBytes;
                int maxLoops    = 2*consecBrokenWritesLimit;
                while(bytesToFill>8 && maxLoops>0) {
                    int bytesToFillPartial = (bytesToFill>decodedFrameSizeInBytes)?decodedFrameSizeInBytes:bytesToFill;
                    int bytesFilled = write(fillupBuffer, bytesToFillPartial);
                    if(bytesFilled<0) {
                        state = STATE_STOP;
                        return true;
                    }
                    bytesToFill -= bytesFilled;
                    maxLoops--;
                }
                if(maxLoops==0) {
                    log.error("Audio decoder fillup loop locked, output sound line broken?");
                }
                availableBytes = wantedLevel;
                firstRun = false;
                if(log.isDebugEnabled())
                    log.debug("dec>                               fill silence to level "+wantedLevel);
            }

            // Too many bytes in buffer, skip write or only write some bytes
            if(availableBytes>(constOutputBufferSize-1.0f)*decodedFrameSizeInBytes) {
                int wantedLevel = (int)((upperBuffer*0.5f + lowerBuffer + 0.5f)*decodedFrameSizeInBytes);
                bytesToWrite = (wantedLevel-availableBytes)&(~3);
                if(bytesToWrite<0)                       bytesToWrite = 0;
                if(bytesToWrite>decodedFrameSizeInBytes) bytesToWrite = decodedFrameSizeInBytes;
                if(log.isDebugEnabled()) {
                    if(bytesToWrite==0)
                        log.debug("dec>                               too many bytes, skip one packet");
                    else
                        log.debug("dec>                               too many bytes, write only "+bytesToWrite+" bytes");
                }
            }

            // Too few bytes in buffer, conceal and fill up
            else if(availableBytes<lowerBuffer*decodedFrameSizeInBytes) {
                int wantedLevel = (int)((upperBuffer*0.5f + lowerBuffer - 0.5f)*decodedFrameSizeInBytes);
                int bytesToFill = (wantedLevel-availableBytes)&(~3);
                if(bytesToFill>16) {
                    concealFrame(fillupBuffer);
                    if(bytesToFill>decodedFrameSizeInBytes) bytesToFill = decodedFrameSizeInBytes;
                    if(log.isDebugEnabled())
                        log.debug("dec>                               too few bytes, fill up "+bytesToFill+" bytes");
                    int bytesFilled = write(fillupBuffer, bytesToFill);
                    if(bytesFilled<0) {
                        state = STATE_STOP;
                        return true;
                    }
                }
            }

            // Write packet to playout buffer
            int bytesWritten = write(decodeBuffer, bytesToWrite);
            if(bytesWritten<0) {
                state = STATE_STOP;
                return true;
            }

            // Write decoded data to monitor mixer channel
            if(monitorChannel!=null) {
                // This function will return immediately if monitoring is inactive
                monitorChannel.write(decodeBuffer, 0, lastDecodedFrameSize);
            }

            // Write the decoded data to the recorder
            if(audioRecorder!=null) {
                // This function will return immediately if no channels are being recorded
                audioRecorder.recordFrame(decodeBuffer, 0, lastDecodedFrameSize, channelIdArray);
            }

            // Clean up jitter buffer
            int size = clean(expectedSequenceNbr);

            // Setup for next tick
            nextWriteTimeInNanos += decodedFrameSizeInNanos;

            // Fault handling
            if(size>jitterBufferUpperLimitInPackets) {
                if(log.isDebugEnabled())
                    log.debug("The size of the jitter buffer has exceeded the maximum limit ("+size+"), discarding "+(size-jitterBufferSizeInPackets)+" packets");
                packet = peekLastPacket();
                expectedSequenceNbr = packet.getSequenceNbr() - jitterBufferSizeInPackets + 1;
                clean(expectedSequenceNbr);
            }
            if(diffTimeInNanos>diffTimeNanosLimit) {
                if(log.isDebugEnabled())
                    log.debug("Diff time above limit ("+(diffTimeInNanos/millisToNanos)+"ms), stopping decoder, decoderId: "+decoderId);
                state = STATE_STOP;
                return true;
            }
            if(nbrConsecNegativeSleep>consecNegativeSleepLimit) {
                if(log.isDebugEnabled())
                    log.debug("Number of consecutive negative sleeps above limit ("+nbrConsecNegativeSleep+"), stopping decoder, decoderId: "+decoderId);
                state = STATE_STOP;
                return true;
            }
        }
        else if(state==STATE_STOP) {
            // Call registred listeners
            Iterator<AudioDecoderListener> iter = listeners.iterator();
            while(iter.hasNext()) {
                AudioDecoderListener listener = iter.next();
                listener.stopDecoding(this, channelIdArray);
            }

            // Remove the filter from the jitterbuffer
            clearFilter();

            // Go to idle state
            state = STATE_IDLE;

            // Run directly again
            return true;
        }
        else {
            log.error("Invalid audio decoder state, going to IDLE");
            state = STATE_IDLE;
        }

        // Sleep
        long sleepTimeInMillis = (nextWriteTimeInNanos-Time.getNanos()+(millisToNanos>>1))/millisToNanos;
        //System.out.println("Decoder sleeping for "+(sleepTimeInNanos/millisToNanos)+" ms");
        if(sleepTimeInMillis<0L)
            nbrConsecNegativeSleep++;
        else {
            Thread.sleep(sleepTimeInMillis);
            nbrConsecNegativeSleep = 0;
        }

        return true;
    }

    public void setVolume(float volume) {
        if(outputLine!=null) {
            if(volume<0.0001f) volume=0.0001f;
            float gain_dB = (float)(20.0*Math.log10(volume));
            FloatControl ctrl = (FloatControl)outputLine.getControl(FloatControl.Type.MASTER_GAIN);
            gain_dB = Math.max(gain_dB, ctrl.getMinimum());
            gain_dB = Math.min(gain_dB, ctrl.getMaximum());
            ctrl.setValue(gain_dB);
        }
    }

    private SourceDataLine createLine(AudioFormat audioFormat) throws LineUnavailableException {
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
        return (SourceDataLine) mixer.getLine(lineInfo);
    }

    private boolean checkReset(Packet packet) {

        if(packet.getFlag(Packet.FLAG_RESET)) {

            int[] resetChannels = packet.getAttributeList(Packet.ATTR_RESET_CHANNELS);

            if(log.isDebugEnabled())
                log.debug("Received reset packet: "+Arrays.toString(resetChannels));

            if(resetChannels==null)
                return true;

            for(int channel : resetChannels) {
                if(channel==decoderId)
                    return true;
            }
        }

        return false;
    }

    private int write(byte[] buffer, int length) {

        // Round down to even four
        int bytesToWrite = length&(~3);

        // Only write if there are bytes in the buffer
        if(bytesToWrite>0) {

            // Write bytes
            int bytesWritten = outputLine.write(buffer, 0, bytesToWrite);

            // Now a write has been performed, ok to flush (bug in IntelHDA using ALSA)
            hasWritten = true;

            // Check for incomplete write
            if(bytesWritten < bytesToWrite) {

                log.warn("Incomplete write (decoder="+decoderId+", requested="+bytesToWrite+", actual="+bytesWritten+")");
                if(bytesWritten<0) log.error("Negative result from output line write!");

                if(nbrConsecBrokenWrites>consecBrokenWritesLimit) {
                    log.warn("Output sound line is broken, recreating and restarting line!");

                    // Stop and close
                    if(hasWritten)             outputLine.flush();
                    if(outputLine.isRunning()) outputLine.stop();
                    if(outputLine.isOpen())    outputLine.close();

                    // Create a new line, open it and start it
                    hasWritten = false;
                    try {

                        AudioFormat audioFormat = getSupportedAudioFormats()[audioFormatIndex];
                        outputLine = createLine(audioFormat);
                        outputLine.open(audioFormat, 4*decodedFrameSizeInBytes);
                        outputLineBufferSize = outputLine.getBufferSize();
                        log.debug("Output line is re-opened with buffer size "+outputLineBufferSize);

                        outputLine.start();

                    } catch (LineUnavailableException ex) {
                        log.error("Unable to recreate the output audio line!", ex);
                        outputLine = null;
                    }

                    // Restart the decoder
                    return -1;
                }
                else {
                    nbrConsecBrokenWrites++;
                }

                bytesWritten = bytesToWrite;
            }
            else {
                nbrConsecBrokenWrites = 0;
            }

            return bytesWritten;
        }

        return 0;
    }
}
