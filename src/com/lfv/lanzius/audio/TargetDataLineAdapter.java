package com.lfv.lanzius.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Control;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * <p>
 * TargetDataLineAdapter
 * <p>
 * Copyright &copy; LFV 2007, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2 (Lanzius)
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
public class TargetDataLineAdapter implements TargetDataLine {

    protected AudioFormat audioFormat = new AudioFormat(16000.0f,16,1,true,false); // 16KHz, signed 16bit, mono, little endian
    protected int         bufferSize  = 2560;                                      // 4 * 20ms @ 16KHz

    protected boolean     open;
    private   boolean     wasted;

    public void open(AudioFormat format, int bufferSize) throws LineUnavailableException {
        if(wasted)
            throw new LineUnavailableException("Line cannot be reopened");

        if(open)
            throw new IllegalStateException("Line is already open");

        if(bufferSize<=0 || (bufferSize&1)!=0)
            throw new IllegalArgumentException("Invalid buffer size ("+bufferSize+")");

        if(!format.matches(audioFormat))
            throw new LineUnavailableException("Unsupported audio format");

        this.audioFormat = format;
        this.bufferSize  = bufferSize;
        this.open        = true;
        this.wasted      = true;
    }

    public void open(AudioFormat format) throws LineUnavailableException {
        open(format, bufferSize);
    }

    public void open() throws LineUnavailableException {
        open(audioFormat);
    }

    public void close() {
        open = false;
        bufferSize = 0;
    }

    public boolean isOpen() {
        return open;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public AudioFormat getFormat() {
        return audioFormat;
    }

    public int read(byte[] b, int off, int len) {
        return len;
    }

    public void drain() {
    }

    public void flush() {
    }

    public void start() {
    }

    public void stop() {
    }

    public boolean isRunning() {
        return false;
    }

    public boolean isActive() {
        return false;
    }

    public int available() {
        return 0;
    }

    public int getFramePosition() {
        throw new UnsupportedOperationException("Method not supported");
    }

    public long getLongFramePosition() {
        throw new UnsupportedOperationException("Method not supported");
    }

    public long getMicrosecondPosition() {
        throw new UnsupportedOperationException("Method not supported");
    }

    public float getLevel() {
        throw new UnsupportedOperationException("Method not supported");
    }

    public Line.Info getLineInfo() {
        throw new UnsupportedOperationException("Method not supported");
    }

    public Control[] getControls() {
        throw new UnsupportedOperationException("Method not supported");
    }

    public boolean isControlSupported(Control.Type control) {
        throw new UnsupportedOperationException("Method not supported");
    }

    public Control getControl(Control.Type control) {
        throw new UnsupportedOperationException("Method not supported");
    }

    public void addLineListener(LineListener listener) {
        throw new UnsupportedOperationException("Method not supported");
    }

    public void removeLineListener(LineListener listener) {
        throw new UnsupportedOperationException("Method not supported");
    }
}
