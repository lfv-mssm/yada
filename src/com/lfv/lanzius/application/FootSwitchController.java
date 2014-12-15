package com.lfv.lanzius.application;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.lfv.lanzius.Constants;

/**
 * <p>
 * The FootSwitchController is monitoring a usb attached foot switch and checks
 * the switch periodically. When the switch is closed the actionRadioTalkButtonPressed
 * is called on the ApplicationBase and when the switch is opened the
 * actionRadioTalkButtonReleased is called. This module uses ftdi d2xx
 * drivers or rxtx com drivers.
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
 * @see ApplicationBase
 */
public class FootSwitchController implements Runnable {

    private static final int SWITCH_STATE_DISCONNECTED = 0;
    private static final int SWITCH_STATE_UP = 1;
    private static final int SWITCH_STATE_PRESSED = 2;
    private static final int SWITCH_STATE_DOWN = 3;
    private static final int SWITCH_STATE_RELEASED = 4;

    private static final int FTSW_VERSION = 0x0212;

    private static native void setSwitchFunction(boolean inverted);
    private static native int  getSwitchVersion();
    private static native int  getSwitchState();

    private Log log;

    private ViewEventHandler handler;
    private Thread thread;
    private boolean isStarted;
    private boolean isWaiting;
    private boolean isInverted;
    private int pollTimeMillis;

    private SerialPort serialPort;
    private String footSwitchInterface;
    private boolean isFTDI;

    public FootSwitchController(ViewEventHandler handler, String footSwitchInterface, int pollTimeMillis, boolean inverted) {
        log = LogFactory.getLog(getClass());
        this.handler = handler;
        this.footSwitchInterface = footSwitchInterface;
        this.pollTimeMillis = pollTimeMillis;
        this.isInverted = inverted;
        this.isStarted = false;
        isFTDI = footSwitchInterface.equalsIgnoreCase("ftdi");
    }

    public void start() throws NoSuchPortException, PortInUseException {
        if(!isStarted) {
            log.debug("Starting interface "+footSwitchInterface);

            // Setup com port
            serialPort = null;
            if(isFTDI) {
                // Check ftsw library for correct version
                int version = 0;
                try {
                    version = getSwitchVersion();
                } catch(UnsatisfiedLinkError err) { }

                if(version!=FTSW_VERSION)
                    throw new UnsatisfiedLinkError("Outdated version of the ftsw library! Update to a newer version, at least 2.1!");

                setSwitchFunction(isInverted);
            }
            else {
                CommPortIdentifier ci = CommPortIdentifier.getPortIdentifier(footSwitchInterface);
                CommPort port = ci.open("ftsw", 1000);
                serialPort = (SerialPort)port;

                // Ugly hack for making it work properly under linux
                boolean paramsSet = false;
                System.out.println("");

                try {
                    serialPort.setSerialPortParams(9600,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
                    paramsSet = true;
                } catch (UnsupportedCommOperationException ex) {}
                if(!paramsSet) {
                    try {
                        serialPort.setSerialPortParams(9600,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
                        paramsSet = true;
                    } catch (UnsupportedCommOperationException ex) {
                        log.error("Unable to set serial parameters! ", ex);
                    }
                }
            }

            isWaiting = true;
            isStarted = true;
            thread = new Thread(this, "DTfootswitch");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void stop() {
        if(!isStarted&&serialPort!=null) {
            serialPort.close();
            serialPort=null;
        }
        isStarted = false;
    }

    private int stateCOM = SWITCH_STATE_UP;
    private int getSwitchStateCOM() {
        // Change states
        if(stateCOM==SWITCH_STATE_UP) {
            boolean cts = serialPort.isCTS();
            if(isInverted) cts = !cts;
            if(cts)
                stateCOM = SWITCH_STATE_PRESSED;
        }
        else if(stateCOM==SWITCH_STATE_PRESSED) {
            stateCOM = SWITCH_STATE_DOWN;
        }
        else if(stateCOM==SWITCH_STATE_DOWN) {
            boolean icts = !serialPort.isCTS();
            if(isInverted) icts = !icts;
            if(icts)
                stateCOM = SWITCH_STATE_RELEASED;
        }
        else if(stateCOM==SWITCH_STATE_RELEASED) {
            stateCOM = SWITCH_STATE_UP;
        }

        return stateCOM;
    }

    public void run() {
        try {
            boolean isConnected = false;
            while(isStarted) {
                int state = isFTDI?getSwitchState():getSwitchStateCOM();

                // Disconnected
                if(state==SWITCH_STATE_DISCONNECTED) {
                    if(isConnected) {
                        log.info("Disconnected");
                        isConnected = false;
                    }
                    Thread.sleep(Constants.PERIOD_FTSW_DISCONNECTED);
                }

                // Connected
                else {
                    if(!isConnected) {
                        log.info("Connected");
                        isConnected = true;
                    }

                    if(isWaiting) {
                        if(state==SWITCH_STATE_UP) {
                            log.debug("State SWITCH_STATE_UP, connecting foot switch to handler");
                            isWaiting = false;
                        }
                    }
                    else {
                        if(state==SWITCH_STATE_PRESSED) {
                            log.debug("Switch pressed");
                            if(handler!=null)
                                handler.talkButtonPressed(Constants.DEVICE_FTSW);
                        }

                        else if(state==SWITCH_STATE_RELEASED) {
                            log.debug("Switch released");
                            if(handler!=null)
                                handler.talkButtonReleased(Constants.DEVICE_FTSW);
                        }
                    }
                    Thread.sleep(pollTimeMillis);
                }
            }
        } catch (InterruptedException ex) {
            log.warn("Foot switch controller thread was interrupted ", ex);
        }

        if(serialPort!=null) {
            serialPort.close();
            serialPort=null;
        }

        log.debug("Stopped");
    }

    static {
        System.loadLibrary("ftsw");
    }
}
