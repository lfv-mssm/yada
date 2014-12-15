package com.lfv.lanzius.application;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Mixer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <p>
 * SoundClip
 * <p>
 * Copyright &copy; LFV 2007, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2.0 (Lanzius)
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
public class SoundClip implements VolumeAdjustable {

    private Log log;

    private static Timer soundTimer;

    private boolean period;
    private TimerTask periodicPlayTask;

    private AudioInputStream stream;
    private Clip clip;
    private int periodMillis;

    private float volumeAdjustment;

    public SoundClip(Mixer outputMixer,String filenameAlternativeA, String filenameAlternativeB, int periodMillis) {
        this(outputMixer, filenameAlternativeA, filenameAlternativeB, periodMillis, 1);
    }

    public SoundClip(Mixer outputMixer,String filenameAlternativeA, String filenameAlternativeB, int periodMillis, float volumeAdjustment) {
        log = LogFactory.getLog(getClass());

        if(soundTimer==null)
            soundTimer = new Timer("Ssoundclip", true);

        boolean altA = true;
        this.periodMillis = periodMillis;
        this.volumeAdjustment = volumeAdjustment;

        // Try to open the first alternative clip
        try {
            stream = AudioSystem.getAudioInputStream(new File(filenameAlternativeA));
            DataLine.Info dataLineInfo = new DataLine.Info(Clip.class, stream.getFormat());
            clip = (Clip)outputMixer.getLine(dataLineInfo);
            clip.open(stream);
        }
        catch(Exception ex) {
            // The first alternative clip could not be opened, try with second alternative
            try {
                if(stream!=null)
                    stream.close();
                if(filenameAlternativeB==null)
                    throw ex;
                stream = AudioSystem.getAudioInputStream(new File(filenameAlternativeB));
                DataLine.Info dataLineInfo = new DataLine.Info(Clip.class, stream.getFormat());
                clip = (Clip)outputMixer.getLine(dataLineInfo);
                clip.open(stream);
                altA = false;
            } catch (Exception ex2) {
                log.error("Unable to get stream for file "+filenameAlternativeA);
                log.error("Unable to get stream for file "+filenameAlternativeB);
                if(stream!=null) {
                    try {
                        stream.close();
                    } catch (IOException ex3) {
                        log.error("Error closing stream ",ex3);
                    }
                }
                stream = null;
                return;
            }
        }

        int clipLength = (int)(clip.getMicrosecondLength()/1000L);
        log.debug("Loading sound clip "+(altA?filenameAlternativeA:filenameAlternativeB)+ " ("+clipLength+"ms)");
        // Check length
        if(periodMillis < clipLength)
            throw new IllegalArgumentException("The periodMillis value must be larger than length of the clip");
    }

    public static void closeCommonTimer() {
        if(soundTimer!=null)
            soundTimer.cancel();
        soundTimer = null;
    }

    public synchronized void playOnce() {
        if(clip!=null) {
            clip.setFramePosition(0);
            clip.start();
        }
    }

    private synchronized void playPeriodic() {
        if(periodicPlayTask==null)
            return;

        // period or idle period
        if(period&&clip!=null) {
            clip.setFramePosition(0);
            clip.start();
        }

        period = !period;
    }

    public synchronized void play() {
        if(stream!=null && clip!=null) {

            // Stop if already running
            stop();
            period = true;

            // Create a new sound task and schedule it
            periodicPlayTask = new TimerTask() {
                public void run() {
                    playPeriodic();
                }
            };
            soundTimer.schedule(periodicPlayTask, 100, periodMillis);
        }
        else
            log.warn("Trying to play sound but no sound has been loaded");
    }

    public synchronized void stop() {
        if(periodicPlayTask!=null) {
            periodicPlayTask.cancel();
            periodicPlayTask = null;
        }
        if(clip!=null)
            clip.stop();
    }

    public synchronized void close() {
        stop();
        if(clip!=null) {
            clip.close();
            clip = null;
        }
    }

    public synchronized void setVolume(float volume) {
        if(stream!=null && clip!=null) {
            volume *= volumeAdjustment;
            if(volume<0.0001f) volume=0.0001f;
            float gain_dB = (float)(20.0*Math.log10(volume));
            FloatControl ctrl = (FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
            gain_dB = Math.max(gain_dB, ctrl.getMinimum());
            gain_dB = Math.min(gain_dB, ctrl.getMaximum());
            ctrl.setValue(gain_dB);
        }
    }
}
