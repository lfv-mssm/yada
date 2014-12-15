package com.lfv.lanzius.application;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.lfv.lanzius.Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <p>
 * <p>
 *
 * @author <a href="mailto:naber@nlr.nl.com">Bastiaan Naber</a>
 * @version Yada 1.0
 * 
 * @see ApplicationBase
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
public class EventSwitchController implements Runnable {

    private Log log;

    private ViewEventHandler handler;
    private Thread thread;
    private boolean isStarted;

    File file;
    private FileInputStream fis;

    public EventSwitchController(ViewEventHandler handler, String eventDeviceName) {
        log = LogFactory.getLog(getClass());
        this.handler = handler;

        file = new File(eventDeviceName);
    }

    public void start() {
        if(!isStarted) {
            log.debug("Starting event interface");

            try {
                fis = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            thread = new Thread(this, "EventSwitch");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void stop() {
        // dispose all the resources after using them.
        try {
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        isStarted = false;
    }

    public void run() {
        byte buffer[] = new byte[100];
        int n;
        int i;

        log.debug("Running");

        try {
            do {
                // Read data from the device
                n = fis.read(buffer);
                //
/*
                System.out.format("Read %d bytes: ", n);
                for (i=0; i<n; i++) {
                    System.out.format("%02x", buffer[i]);
                }
                System.out.print("\n");
*/
                // Looking at the above output it seems byte 20 has the button state
                if (buffer[44] == 1) {
                    if (handler!=null) {
                        handler.talkButtonPressed(Constants.DEVICE_FTSW);
                    }
                }
                else {
                    if (handler!=null) {
                        handler.talkButtonReleased(Constants.DEVICE_FTSW);
                    }
                }
            } while (n != -1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
