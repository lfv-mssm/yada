package com.lfv.lanzius.application;

import java.awt.Color;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import com.arkatay.yada.codec.AudioCodecConstants;
import com.lfv.lanzius.Constants;

/**
 * <p>
 * TerminalProperties
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
public class TerminalProperties {

    private int     terminalId;
    private String  userInterfaceStyle;
    private String  maxUserInterfaceSize;
    private boolean hiddenMouseCursor;

    private String  pushToTalkInterface;

    private String  eventDeviceName;

    private String  footSwitchInterface;
    private boolean footSwitchInverted;
    private int     footSwitchPollTimeMillis;

    private int     soundOutputDevice;
    private int     soundInputDevice;
    private int     jitterBufferSize;
    private float   outputBufferSize;
    private float   inputBufferSize;
    private float   signalVolumeAdjustment;
    private int     overPowerOtherStations;
    private int     automaticISA;

    private String  multicastAddress;
    private int     multicastPort;
    private int     multicastTTL;
    private String  localBindAddress;
    private int     localBindPort;
    private int     peripheralLinkPort;

    private String  serverAddress;
    private int     serverHttpPort;
    private int     serverUdpPort;

    private Color   colorRadioRxTx;
    private Color   colorRadioRx;
    private Color   colorRadioOff;
    private Color   colorRadioIndicatorIdle;
    private Color   colorRadioIndicatorBusy;
    private Color   colorRadioIndicatorFail;

    private Color   colorPhoneButtonActive;
    private Color   colorPhoneButtonInactive;
    private Color   colorPhoneHookButtonCalling;
    private Color   colorPhoneHookButtonBusy;

    private Color   colorGenericButton;

    public TerminalProperties(int id) throws IOException {
        String conf_file = "data/properties/terminalproperties.xml";
        if (id != 0) {
             conf_file = "data/properties/terminalproperties-" + id + ".xml";
        }

        Properties properties = new Properties();
        properties.loadFromXML(new FileInputStream(conf_file));

        terminalId            = Integer.parseInt(properties.getProperty("TerminalId"));
        userInterfaceStyle    =                  properties.getProperty("UserInterfaceStyle", "full");
        maxUserInterfaceSize  =                  properties.getProperty("MaxUserInterfaceSize", "full");
        hiddenMouseCursor     =                  properties.getProperty("HiddenMouseCursor", "false").equalsIgnoreCase("true");

        try {
            soundOutputDevice = Integer.parseInt(properties.getProperty("SoundOutputDevice"));
        } catch(Exception ex) {
            soundOutputDevice = 0;
        }
        try {
            soundInputDevice = Integer.parseInt(properties.getProperty("SoundInputDevice"));
        } catch(Exception ex) {
            soundInputDevice = 0;
        }

        try {
            jitterBufferSize = Integer.parseInt(properties.getProperty("JitterBufferSize"));
        } catch(Exception ex) {
            jitterBufferSize = AudioCodecConstants.jitterBufferSizeDef;
        }
        try {
            outputBufferSize = Float.parseFloat(properties.getProperty("OutputBufferSize"));
        } catch(Exception ex) {
            outputBufferSize = AudioCodecConstants.outputBufferSizeDef;
        }
        try {
            inputBufferSize = Float.parseFloat(properties.getProperty("InputBufferSize"));
        } catch(Exception ex) {
            inputBufferSize = AudioCodecConstants.inputBufferSizeDef;
        }

        try {
            signalVolumeAdjustment = Float.parseFloat(properties.getProperty("SignalVolumeAdjustment"));
        } catch(Exception ex) {
            signalVolumeAdjustment = 1;
        }
        try {
            overPowerOtherStations = Integer.parseInt(properties.getProperty("OverPowerOtherStations"));
        } catch(Exception ex) {
                overPowerOtherStations = 0;
        }

        pushToTalkInterface = properties.getProperty("PushToTalkInterface", "event");

        eventDeviceName = properties.getProperty("EventDeviceName", "");

        footSwitchInterface = properties.getProperty("FootSwitchInterface", "");
        footSwitchInverted  = properties.getProperty("FootSwitchInvertCTS", "false").equalsIgnoreCase("true");
        try {
            footSwitchPollTimeMillis = Integer.parseInt(properties.getProperty("FootSwitchPollTime"));
        } catch(Exception ex) {
            footSwitchPollTimeMillis = Constants.PERIOD_FTSW_CONNECTED;
        }

        multicastAddress = properties.getProperty("MulticastAddress", "224.0.23.61");
        try {
            multicastPort = Integer.parseInt(properties.getProperty("MulticastPort"));
        } catch(Exception ex) {
            multicastPort = 36608;
        }
        try {
            multicastTTL = Integer.parseInt(properties.getProperty("MulticastTTL"));
        } catch(Exception ex) {
            multicastTTL = 3;
        }
        if(multicastAddress.equals("null")||(multicastPort==0)) {
            multicastAddress = null;
            multicastPort = 0;
            multicastTTL = 3;
        }

        localBindAddress = properties.getProperty("LocalBindAddress", "null");
        try {
            localBindPort = Integer.parseInt(properties.getProperty("LocalBindPort"));
        } catch(Exception ex) {
            localBindPort = 0;
        }
        if(localBindAddress.equals("null")||(localBindPort==0)) {
            localBindAddress = null;
            localBindPort = 0;
        }

        try {
            peripheralLinkPort = Integer.parseInt(properties.getProperty("PeripheralLinkPort"));
        } catch(Exception ex) {
            peripheralLinkPort = 0;
        }

        serverAddress = properties.getProperty("ServerAddress");
        if(serverAddress==null) throw new IOException("Property value ServerAddress not found in file.");
        try {
            serverHttpPort = Integer.parseInt(properties.getProperty("ServerHttpPort"));
        } catch(Exception ex) {
            serverHttpPort = 36600;
        }
        try {
            serverUdpPort = Integer.parseInt(properties.getProperty("ServerUdpPort"));
        } catch(Exception ex) {
            serverUdpPort = 36604;
        }

        colorRadioRxTx              = getColor(properties, "ColorRadioRxTx");
        colorRadioRx                = getColor(properties, "ColorRadioRx");
        colorRadioOff               = getColor(properties, "ColorRadioOff");
        colorRadioIndicatorIdle     = getColor(properties, "ColorRadioIndicatorIdle");
        colorRadioIndicatorBusy     = getColor(properties, "ColorRadioIndicatorBusy");
        colorRadioIndicatorFail     = getColor(properties, "ColorRadioIndicatorFail");

        colorPhoneButtonActive      = getColor(properties, "ColorPhoneButtonActive");
        colorPhoneButtonInactive    = getColor(properties, "ColorPhoneButtonInactive");
        colorPhoneHookButtonCalling = getColor(properties, "ColorPhoneHookButtonCalling");
        colorPhoneHookButtonBusy    = getColor(properties, "ColorPhoneHookButtonBusy");

        colorGenericButton          = getColor(properties, "ColorGenericButton");
    }

    private Color getColor(Properties properties, String propKey) {
        Color color = Color.lightGray;
        try {
            color = Color.decode(properties.getProperty(propKey));
        } catch(Exception ex) {}
        return color;
    }

    public void setTerminalId(int terminalId) {
        this.terminalId = terminalId;
    }

    public int getTerminalId() {
        return terminalId;
    }

    public int getOverPowerOtherStations() {
        return overPowerOtherStations;
    }
    
    public int getAutomaticISA() {
    	return automaticISA;
    }

    public void setUserInterfaceStyle(String userInterfaceStyle) {
        this.userInterfaceStyle = userInterfaceStyle;
    }

    public String getUserInterfaceStyle() {
        return userInterfaceStyle;
    }

    public void setMaxUserInterfaceSize(String maxUserInterfaceSize) {
        this.maxUserInterfaceSize = maxUserInterfaceSize;
    }

    public String getMaxUserInterfaceSize() {
        return maxUserInterfaceSize;
    }

    public boolean isMouseCursorHidden() {
        return hiddenMouseCursor;
    }

    public int getSoundOutputDevice() {
        return soundOutputDevice;
    }

    public int getSoundInputDevice() {
        return soundInputDevice;
    }

    public int getJitterBufferSize() {
        return jitterBufferSize;
    }

    public float getOutputBufferSize() {
        return outputBufferSize;
    }

    public float getInputBufferSize() {
        return inputBufferSize;
    }

    public float getSignalVolumeAdjustment() {
        return signalVolumeAdjustment;
    }

    public String getPushToTalkInterface() {
        return pushToTalkInterface;
    }

    public String getEventDeviceName() {
        return eventDeviceName;
    }

    public String getFootSwitchInterface() {
        return footSwitchInterface;
    }

    public boolean isFootSwitchInverted() {
        return footSwitchInverted;
    }

    public int getFootSwitchPollTimeMillis() {
        return footSwitchPollTimeMillis;
    }

    public String getMulticastAddress() {
        return multicastAddress;
    }

    public int getMulticastPort() {
        return multicastPort;
    }

    public int getMulticastTTL() {
        return multicastTTL;
    }

    public String getLocalBindAddress() {
        return localBindAddress;
    }

    public int getLocalBindPort() {
        return localBindPort;
    }

    public int getPeripheralLinkPort() {
        return peripheralLinkPort;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public int getServerHttpPort() {
        return serverHttpPort;
    }

    public int getServerUdpPort() {
        return serverUdpPort;
    }

    public Color getColorRadioRxTx() {
        return colorRadioRxTx;
    }

    public Color getColorRadioRx() {
        return colorRadioRx;
    }

    public Color getColorRadioOff() {
        return colorRadioOff;
    }

    public Color getColorRadioIndicatorIdle() {
        return colorRadioIndicatorIdle;
    }

    public Color getColorRadioIndicatorBusy() {
        return colorRadioIndicatorBusy;
    }

    public Color getColorRadioIndicatorFail() {
        return colorRadioIndicatorFail;
    }

    public Color getColorPhoneButtonActive() {
        return colorPhoneButtonActive;
    }

    public Color getColorPhoneButtonInactive() {
        return colorPhoneButtonInactive;
    }

    public Color getColorPhoneHookButtonCalling() {
        return colorPhoneHookButtonCalling;
    }

    public Color getColorPhoneHookButtonBusy() {
        return colorPhoneHookButtonBusy;
    }

    public Color getColorGenericButton() {
        return colorGenericButton;
    }
}
