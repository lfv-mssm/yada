package com.arkatay.yada.codec;

import com.lfv.lanzius.Constants;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.arkatay.yada.base.HashInteger;

/**
 * <p>
 * The AudioRecorder class is used to record audio decoded by an audio decoder
 * and store it as an audio file.
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
public class AudioRecorder implements AudioCodecConstants, AudioDecoderListener {

    private final int SILENCE_BUFFER_SIZE = 1024;

    private Log log;
    private AudioFormat audioFormat;
    private boolean injectSilence;
    private boolean recording;
    private byte[] silenceBuffer;
    private int samplesPerMillisecond;
    private Map<HashInteger,AudioChannelRecorder> recorderMap;
    private HashInteger hashInteger;

    /**
     * Creates a new instance of AudioRecorder with an audio format that the
     * input voice data is using.
     *
     * @param  audioFormat the audioFormat used by the recorded voice data
     */
    public AudioRecorder(AudioFormat audioFormat, boolean injectSilence) {
        // Create a logger for this class
        log = LogFactory.getLog(getClass());

        this.audioFormat = audioFormat;
        this.injectSilence = injectSilence;

        hashInteger = new HashInteger();
        recorderMap = new HashMap<HashInteger,AudioChannelRecorder>(32);
        silenceBuffer = new byte[SILENCE_BUFFER_SIZE];

        // Fill silence buffer
        for(int i=0;i<SILENCE_BUFFER_SIZE;i++) {
            silenceBuffer[i] = 0;
        }

        // Calculate nbr samples per millisecond
        samplesPerMillisecond = (int)(audioFormat.getFrameRate()*audioFormat.getFrameSize()/1000);
    }

    public synchronized void startRecording(int channelId, String channelName, int monitoredTerminalId) {

        // Return if already recording on channel
        hashInteger.setValue(channelId);
        if(recorderMap.containsKey(hashInteger)) {
            return;
        }

        // Start recording
        try {
            recorderMap.put( new HashInteger(channelId),
                             new AudioChannelRecorder(channelId, channelName, monitoredTerminalId));
            recording = true;
        } catch (FileNotFoundException ex) {
            log.error("Unable to create a temporary audio recording file ", ex);
        }
    }

    public synchronized void stopRecording(int channelId) {

        hashInteger.setValue(channelId);
        AudioChannelRecorder acr = recorderMap.remove(hashInteger);

        // Stop recorder
        if(acr!=null) {
            acr.stopRecording();
        }

        // stop recording if no channels are active
        if(recorderMap.isEmpty())
            recording = false;
    }

    public synchronized int getMonitoredTerminalId(int channelId) {
        hashInteger.setValue(channelId);
        AudioChannelRecorder acr = recorderMap.get(hashInteger);
        if(acr!=null) {
            return acr.monitoredTerminalId;
        }
        return 0;
    }

    public synchronized void stopDecoding(AudioDecoder source, int[] channelIdArray) {
        if(!recording)
            return;

        if(injectSilence) {
            long time = System.currentTimeMillis();
            for(int i=0;i<channelIdArray.length;i++) {
                hashInteger.setValue(channelIdArray[i]);
                AudioChannelRecorder acr = (AudioChannelRecorder)recorderMap.get(hashInteger);
                if(acr!=null) {
                    acr.decodeStopTime = time;
                }
            }
        }
    }

    public void startDecoding(AudioDecoder source, int[] channelIdArray) {}

    /**
     * Records one frame of decoded voice data. This method is called from the
     * audio decoder.
     *
     * @param  decodeBuffer the decoded buffer from the audio decoder
     * @param  offset the offset into the decoded buffer to read from
     * @param  length the length of the decoded buffer
     * @param  channelIdArray an array of channels that the decoder operates on
     */
    public synchronized void recordFrame(byte[] decodeBuffer, int offset, int length, int[] channelIdArray) {

        log.error("recordFrame "+recording);
        if(!recording)
            return;

        for(int i=0;i<channelIdArray.length;i++) {
            hashInteger.setValue(channelIdArray[i]);
            AudioChannelRecorder acr = recorderMap.get(hashInteger);
            if(acr!=null) {
                // Write data to temporary file
                try {
                    // Inject silence
                    if(acr.decodeStopTime>0 && injectSilence) {

                        int silenceTimeInMillis = (int)(System.currentTimeMillis() - acr.decodeStopTime);
                        int silenceTimeInBytes  = samplesPerMillisecond * silenceTimeInMillis;

                        while(silenceTimeInBytes>0) {

                            // Number of silent bytes to write for each frame
                            int silenceBytesToWrite = SILENCE_BUFFER_SIZE;
                            if(silenceTimeInBytes<silenceBytesToWrite)
                                silenceBytesToWrite = silenceTimeInBytes;

                            // Write silence
                            acr.outputStream.write(silenceBuffer, 0, silenceBytesToWrite);
                            silenceTimeInBytes -= silenceBytesToWrite;
                        }

                        // Reset time
                        acr.decodeStopTime = 0;
                    }

                    acr.outputStream.write(decodeBuffer, offset, length);

                } catch (IOException ex) {
                    log.error("Failure when writing to temporary sound recording file. ", ex);
                }
            }
        }
    }

    private String s2(int value) {
        if(value<10)
            return "0"+value;
        else
            return String.valueOf(value);
    }

    private class AudioChannelRecorder {

        private int          channelId;
        private int          monitoredTerminalId;
        private String       channelName;
        private String       temporaryFilename;
        private long         recordStartTime;
        private long         decodeStopTime;
        private OutputStream outputStream;

        private AudioChannelRecorder(int channelId, String channelName, int monitoredTerminalId) throws FileNotFoundException {

            this.channelId           = channelId;
            this.channelName         = channelName;
            this.monitoredTerminalId = monitoredTerminalId;

            this.recordStartTime    = System.currentTimeMillis();
            new File("data/recordings/temp").mkdirs();
            this.temporaryFilename  = "data/recordings/temp/r"+recordStartTime+".raw";
            this.outputStream       = new FileOutputStream(temporaryFilename);

            log.info("Starting recording on channel "+channelId+" ("+temporaryFilename+")");

            // Set this to zero to start recording when the first voice
            // comes in, set to current time to start recording when record
            // button is pressed
            decodeStopTime = recordStartTime;
        }

        private void stopRecording() {

            // Flush and close the outputstream
            try {
                outputStream.flush();
                outputStream.close();
            } catch (IOException ex) {
                log.error("Unable to close the temporary audio file", ex);
                return;
            }

            // The temporary recorded file
            File f = new File(temporaryFilename);

            // Create a real filename
            String filename;
            if(monitoredTerminalId>0) {
                filename = "Monitor("+monitoredTerminalId+")-";
            }
            else {
                filename = "Channel("+(channelId&Constants.ID_MASK_CHANNEL);
                if(channelName!=null) {
                    filename += "_"+channelName;
                }
                filename += ")-Group("+((channelId&Constants.ID_MASK_GROUP)>>Constants.ID_BITSHIFT)+")-";
            }

            // Add date and time
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.setTimeInMillis(recordStartTime);
            filename += "Date("+
                        c.get(Calendar.YEAR) +
                        s2(c.get(Calendar.MONTH)+1) +
                        s2(c.get(Calendar.DAY_OF_MONTH)) +
                        ")-Time(UTC" +
                        s2(c.get(Calendar.HOUR_OF_DAY)) +
                        s2(c.get(Calendar.MINUTE))+
                        s2(c.get(Calendar.SECOND)) +
                        ")";

            // Remove invalid characters that may exist in the channel name
            filename = filename.replace('/',  '!');
            filename = filename.replace('\\', '!');
            filename = filename.replace(':',  '!');
            filename = filename.replace('*',  '!');
            filename = filename.replace('?',  '!');
            filename = filename.replace('"',  '!');
            filename = filename.replace('<',  '!');
            filename = filename.replace('>',  '!');
            filename = filename.replace('|',  '!');

            // Add extension
            filename = "data/recordings/"+filename+".wav";

            log.info("Stopping recording on channel "+channelId+" ("+filename+")");

            try {
                // Copy to wav file and remove temporary raw file
                FileInputStream inputStream = new FileInputStream(f);
                AudioInputStream ais = new AudioInputStream(inputStream, audioFormat, f.length()/audioFormat.getFrameSize());
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(filename));
                ais.close();
                inputStream.close();
            } catch (IOException ex) {
                log.error("Unable to convert the temporary audio file to a wav file", ex);
            }

            // Remove temporary file
            f.delete();
        }
    }
}
