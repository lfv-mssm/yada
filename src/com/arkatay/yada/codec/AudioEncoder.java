package com.arkatay.yada.codec;

import com.arkatay.yada.base.Time;
import com.lfv.yada.net.client.Distributor;
import com.lfv.lanzius.audio.MixingDataLine;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.lfv.yada.net.AttributeData;
import com.lfv.yada.net.Packet;
import com.lfv.yada.net.PacketPool;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;

/**
 * <p>
 * The AudioEncoder class is an abstract class that needs to be extended with
 * an encodeFrame method. The encoder uses a thread running a loop that executes
 * at 20ms intervals. Because the underlying task scheduler is not exact the
 * interval may vary a bit from 20 ms in some cases, but the mean value is
 * exactly 20ms according to the system clock.
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
public abstract class AudioEncoder implements Runnable, AudioCodecConstants {

    protected Log log;
    private Thread thread;
    private Distributor distributor;
    private PacketPool pool;

    // Input line
    private Mixer mixer;
    private TargetDataLine inputLine;
    private int inputLineBufferSize;
    protected int audioFormatIndex;

    // Monitor mixing
    MixingDataLine.Channel monitorChannel;

    // Timing and stuff
    private long startTimeInNanos;
    private long nextReadTimeInNanos;
    private long diffTimeNanosLimit;
    private int sequenceNbr;
    private int capturedFrameSizeInBytes;
    private long capturedFrameSizeInNanos;
    private int nbrConsecNegativeSleep;
    private boolean synchronizeTimer;

    // Buffers
    private byte[] captureBuffer;

    // States
    private static final int STATE_OFF   = 1;
    private static final int STATE_IDLE  = 2;
    private static final int STATE_START = 3;
    private static final int STATE_RUN   = 4;
    private static final int STATE_STOP  = 5;
    private static final int STATE_DYING = 6;
    private static final int STATE_DEAD  = 7;
    private int state;

    private List<Integer> resetChannelsList;

    private static float constInputBufferSize = inputBufferSizeDef;
    public static float setInputBufferSize(float inputBufferSize) {
        if(inputBufferSize<inputBufferSizeMin) inputBufferSize = inputBufferSizeMin;
        if(inputBufferSize>inputBufferSizeMax) inputBufferSize = inputBufferSizeMax;
        constInputBufferSize = inputBufferSize;
        return inputBufferSize;
    }

    protected AudioEncoder(Mixer mixer) {
        // Create a logger for this class
        log = LogFactory.getLog(getClass());

        this.mixer = mixer;

        // Create the thread
        thread = new Thread(this, "Tencoder");
        thread.setPriority((Thread.NORM_PRIORITY+Thread.MAX_PRIORITY)/2);

        // Reset list
        resetChannelsList = new LinkedList<Integer>();

        pool = PacketPool.getPool();

        // Start in state off
        state = STATE_OFF;
    }

    public abstract AudioFormat[] getSupportedAudioFormats();

    protected abstract void init();

    /**
     * Encodes one frame of voice data.
     *
     * @param captured a byte array containing voice data for one captured frame
     * @param length the length of the captured data in bytes
     * @param encoded an empty byte array to which the encoded data should be written
     * @param offset the offset to start writing to in the encoded byte array
     * @return size of the encoded frame in bytes
     */
    protected abstract int encodeFrame(byte[] captured, int length, byte[] encoded, int offset);

    /**
     * Sets the packet distributor on which the prepared packed will be posted.
     * The packet distributor sends the packet to all recipients.
     *
     * @param  distributor the packet distributor
     */
    public void setPacketDistributor(Distributor distributor) {
        this.distributor = distributor;
    }

    public void setMonitorChannel(MixingDataLine.Channel monitorChannel) {
        this.monitorChannel = monitorChannel;
    }

    public void startModule(int audioFormatIndex) throws LineUnavailableException {
        startModule(null, audioFormatIndex);
    }

    public void startModule(TargetDataLine inputLine, int audioFormatIndex) throws LineUnavailableException {
        capturedFrameSizeInNanos = 20L*millisToNanos;

        if(state!=STATE_OFF)
            throw new IllegalStateException("Trying to re-start the encoder");

        // Check bounds
        AudioFormat[] audioFormats = getSupportedAudioFormats();
        if(audioFormatIndex<0 || audioFormatIndex>=audioFormats.length)
            throw new LineUnavailableException("Audio format array out of bounds");

        // Get format
        AudioFormat audioFormat = audioFormats[audioFormatIndex];

        // Create line if created internally
        if(inputLine==null) {
            inputLine = createLine(audioFormat);
        }

        // Validate the audio format if external
        else if(!audioFormat.matches(inputLine.getFormat())) {
            throw new LineUnavailableException("Audio format not supported");
        }

        this.inputLine = inputLine;
        this.audioFormatIndex = audioFormatIndex;

        // Call init on the sub-class implementation
        init();

        // Calculate stuff
        capturedFrameSizeInBytes = (int)(audioFormat.getFrameRate()*audioFormat.getFrameSize()*capturedFrameSizeInNanos/(1000*millisToNanos));
        diffTimeNanosLimit = diffTimeMillisLimit*millisToNanos;

        // Open the input line, the wanted buffer size is N times as big as the frame size
        inputLine.open(audioFormat, 4*capturedFrameSizeInBytes);
        inputLineBufferSize = inputLine.getBufferSize();
        log.debug("Input line is open with buffer size "+inputLineBufferSize);

        // Create a buffer for the captured frame
        captureBuffer = new byte[capturedFrameSizeInBytes];

        // Go to state idle
        state = STATE_IDLE;

        // Start the capturing thread, it will block until startProcessing is called
        thread.start();
    }

    public synchronized void stopModule() {
        if(state==STATE_OFF)
            return;
        log.debug("Stopping encoder module");
        if(state==STATE_IDLE||state==STATE_STOP) {
            state = STATE_DEAD;
            thread.interrupt();
        }
        else
            state = STATE_DYING;
    }

    public synchronized void startProcessing() {
        try {
            // Wait until state is idle
            long t = System.currentTimeMillis();
            while((state!=STATE_IDLE)&&(System.currentTimeMillis()<(t+TIMEOUT_WAITIDLE)))
                wait(100);
            if(state!=STATE_IDLE)
                log.warn("Wait for state idle in startProcessing timed out, unable to start processing! (state=="+state+")");
            else {
                resetChannelsList.clear();
                state = STATE_START;
                notifyAll();
            }
        } catch (InterruptedException ex) {
            log.warn("InterruptedException in startProcessing!", ex);
        }
    }

    public synchronized void stopProcessing() {
        if((state==STATE_START)||(state==STATE_RUN)) {
            state = STATE_STOP;
        }
    }

    public synchronized void resetChannel(int channelId) {
        resetChannelsList.add(channelId);
    }

    public synchronized void resetChannels(Collection<Integer> channels) {
        resetChannelsList.addAll(channels);
    }

    public void run() {
        if(distributor==null) {
            log.error("Packet distributor is not set");
            return;
        }
        try {
            while((!thread.isInterrupted())&&(state!=STATE_DEAD)) {
                if(!threadMain()) {
                    log.error("The encoder thread was interrupted by an error");
                    state = STATE_DEAD;
                }
            }
        } catch(InterruptedException ex) {
            if(state!=STATE_DEAD)
                log.warn("Encoder interrupted unexpectedly ", ex);
        }

        if(state==STATE_DEAD) {
            // It's ok, the dispatcher was manually interrupted
            log.debug("Encoder module successfully stopped");
        }

        if(inputLine!=null) {
            if(inputLine.isRunning())
               inputLine.stop();
            if(inputLine.isOpen())
                inputLine.close();
        }
    }

    private boolean threadMain() throws InterruptedException {
        synchronized(this) {
            if(state==STATE_IDLE) {
                wait();
                if(state==STATE_IDLE||state==STATE_STOP)
                    state=STATE_IDLE;
                else if(state==STATE_DYING)
                    state=STATE_DEAD;
                else
                    state=STATE_START;
                return true;
            }
            else if(state==STATE_START) {

                // Go to run state
                state = STATE_RUN;

                // Startup the line
                inputLine.flush();
                inputLine.start();

                // Wait until there are some samples available in the buffer
                inputLine.read(captureBuffer,0,640);

                // Set the start time
                startTimeInNanos = Time.getNanos();
                synchronizeTimer = true;

                // Initialize packet sequence number
                sequenceNbr = 1;

                // Run again in 20ms
                nextReadTimeInNanos = startTimeInNanos + capturedFrameSizeInNanos;
            }
            else if(state==STATE_RUN) {
                long currentTimeInNanos = Time.getNanos();
                long diffTimeInNanos = currentTimeInNanos-nextReadTimeInNanos;

                if(log.isTraceEnabled())
                    log.trace("Running at "+((currentTimeInNanos-startTimeInNanos)/millisToNanos)+"ms, expected: "+((nextReadTimeInNanos-startTimeInNanos)/millisToNanos)+"ms, diffTime: "+(diffTimeInNanos/millisToNanos)+"ms");

                boolean readFrame = true;
                int bytesToDrain = -1;
                int availableBytes = inputLine.available();
                if(availableBytes<0) availableBytes=0;

                if(log.isDebugEnabled())
                    log.debug("enc> avail: "+availableBytes+" (640-"+(int)(constInputBufferSize*capturedFrameSizeInBytes)+")");

                // Must drain if max is reached
                if(availableBytes==inputLineBufferSize)
                    availableBytes=Integer.MAX_VALUE;

                // Too many available bytes, drain buffer
                if(availableBytes>constInputBufferSize*capturedFrameSizeInBytes) {
                    int wantedLevel = (int)((constInputBufferSize*0.5f + 0.5f)*capturedFrameSizeInBytes);
                    bytesToDrain = availableBytes-wantedLevel;
                    log.debug("enc> too many bytes, drain");
                }

                // Too few available bytes, skip one frame
                else if(availableBytes<capturedFrameSizeInBytes) {
                    int wantedLevel = (int)((constInputBufferSize*0.5f - 0.5)*capturedFrameSizeInBytes);
                    bytesToDrain = availableBytes-wantedLevel;
                    readFrame = false;
                    log.debug("enc> too few bytes, drain and skip");
                }

                if(bytesToDrain>0) {
                    if(bytesToDrain<availableBytes)
                        bytesToDrain = availableBytes;
                    if(bytesToDrain>capturedFrameSizeInBytes)
                        bytesToDrain = capturedFrameSizeInBytes;
                    bytesToDrain -= bytesToDrain&7;
                    int bytesDrained = inputLine.read(captureBuffer, 0, bytesToDrain);
                    if(log.isDebugEnabled())
                        log.debug("enc> drain "+bytesDrained+" bytes");
                }

                if(readFrame) {
                    // Prepare a new packet to send
                    Packet packet = pool.borrowPacket(Packet.DATA | Packet.REQUEST);
                    packet.setSequenceNbr(sequenceNbr);

                    // Reset channels and remove later from distributor
                    if(!resetChannelsList.isEmpty()) {
                        if(log.isDebugEnabled())
                            log.debug("Resetting channels " + java.util.Arrays.toString(resetChannelsList.toArray()));
                        packet.setFlag(Packet.FLAG_RESET);
                        packet.addAttributeList(Packet.ATTR_RESET_CHANNELS, resetChannelsList);
                    }

                    // Read from the input line without blocking
                    inputLine.read(captureBuffer, 0, capturedFrameSizeInBytes);

                    // Prepare a data load
                    AttributeData attrData = packet.prepareAttributeData(0);

                    // Encode the frame and write the output into the packet data
                    int bytesEncoded = encodeFrame(captureBuffer, capturedFrameSizeInBytes, attrData.getBuffer(), attrData.getOffset());

                    // Only send if there is data available
                    if(bytesEncoded>0) {

                        // Add the encoded data
                        packet.addAttributeData(bytesEncoded);

//                        // Test for simulating packet loss
//                        double packetLossPercentage = 30;
//                        if(Math.random()<packetLossPercentage/100.0) {
//                            log.info("Dropping packet: "+packet);
//                            pool.returnPacket(packet);
//                            packet = null;
//                        }
//                        else

                        // Send packet to all recipients in the distributor
                        distributor.distributePacket(packet);

                        if(synchronizeTimer) {
                            nextReadTimeInNanos  = Time.getNanos() - (capturedFrameSizeInNanos>>2);
                            synchronizeTimer = false;
                        }

                        //log.info("System Time: "+((currentTimeInNanos-startTimeInNanos)/millisToNanos)+"ms, Packets sent: "+sequenceNbr+" = "+(sequenceNbr*20)+"ms, Available read: "+availableBytes);
                    }
                    else {
                        log.warn("No bytes encoded, packet not sent!");
                        pool.returnPacket(packet);
                    }

                    // Now the resetted channel can be removed from the distributor
                    if(!resetChannelsList.isEmpty()) {
                        distributor.remove(resetChannelsList);
                        resetChannelsList.clear();
                    }
                }

                // Write captured data to monitor mixer channel, last frame if readFrame is false
                if(monitorChannel!=null) {
                    // This function will return immediately if monitoring is inactive
                    monitorChannel.write(captureBuffer, 0, capturedFrameSizeInBytes);
                }

                // Next packet sequence number
                sequenceNbr++;

                // Setup for next tick
                nextReadTimeInNanos += capturedFrameSizeInNanos;

                // Fault checking
                boolean reset = false;
                if(diffTimeInNanos>diffTimeNanosLimit) {
                    log.warn("Diff time above limit ("+(diffTimeInNanos/millisToNanos)+"ms), resetting encoder");
                    reset = true;
                }
                if(nbrConsecNegativeSleep>consecNegativeSleepLimit) {
                    log.warn("Number of consecutive negative sleeps above limit ("+nbrConsecNegativeSleep+"), resetting encoder");
                    reset = true;
                }

                if(reset) {

                    // Stay in run state
                    state = STATE_RUN;

                    // Send reset to recipients
                    sendReset();

                    // Flush the input line
                    inputLine.flush();

                    // Wait until there are some samples available in the buffer
                    inputLine.read(captureBuffer,0,640);

                    // Set the start time
                    startTimeInNanos = Time.getNanos();
                    synchronizeTimer = true;

                    // Initialize packet sequence number
                    sequenceNbr = 1;

                    // Run again in 20ms
                    nextReadTimeInNanos = startTimeInNanos + capturedFrameSizeInNanos;
                }
            }
            else if(state==STATE_STOP||state==STATE_DYING) {

                // Send reset to recipients
                sendReset();

                // Stopping input line
                inputLine.stop();
                inputLine.flush();

                // Remove all from distributor
                distributor.removeAll();

                // Go to idle or dead state
                state = (state==STATE_DYING)?STATE_DEAD:STATE_IDLE;
                notifyAll();

                return true;
            }
        } // synchronized

        // Sleep
        long sleepTimeInMillis = (nextReadTimeInNanos-Time.getNanos()+(millisToNanos>>1))/millisToNanos;
        //System.out.println("Encoder sleeping for "+(sleepTimeInNanos/millisToNanos)+" ms");
        if(sleepTimeInMillis<0L)
            nbrConsecNegativeSleep++;
        else {
            Thread.sleep(sleepTimeInMillis);
            nbrConsecNegativeSleep = 0;
        }

        return true;
    }

    private TargetDataLine createLine(AudioFormat audioFormat) throws LineUnavailableException {
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
        return (TargetDataLine) mixer.getLine(lineInfo);
    }

    private void sendReset() {
        Packet packet;
        packet = pool.borrowPacket(Packet.DATA | Packet.REQUEST);
        packet.setSequenceNbr(sequenceNbr);
        packet.setFlag(Packet.FLAG_RESET);
        distributor.distributePacket(packet);
    }
}
