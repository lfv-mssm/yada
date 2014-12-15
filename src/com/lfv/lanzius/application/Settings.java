package com.lfv.lanzius.application;

/**
 * <p>
 * Settings
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
public class Settings {

    public static final int NBR_VOLUME_STEPS  = 26;

    public static final int CHPRIO_RADIO      = 0;
    public static final int CHPRIO_OFF        = 1;
    public static final int CHPRIO_PHONE      = 2;

    public static final int WATONE_OFF        = 0;
    public static final int WATONE_ON         = 1;

    public static final int ID_MASTER_VOLUME  = 1;
    public static final int ID_SIGNAL_VOLUME  = 2;
    public static final int ID_CHPRIO_VOLUME  = 3;
    public static final int ID_RAPASS_VOLUME  = 4;
    public static final int ID_CHPRIO_CHOICE  = 5;
    public static final int ID_WATONE_CHOICE  = 6;

    public static final int DEF_MASTER_VOLUME = 15;
    public static final int DEF_SIGNAL_VOLUME = 8;
    public static final int DEF_CHPRIO_VOLUME = 5;
    public static final int DEF_RAPASS_VOLUME = 2;
    public static final int DEF_CHPRIO_CHOICE = CHPRIO_PHONE;
    public static final int DEF_WATONE_CHOICE = WATONE_OFF;

    private int masterVolume = DEF_MASTER_VOLUME;
    private int signalVolume = DEF_SIGNAL_VOLUME;
    private int chprioVolume = DEF_CHPRIO_VOLUME;
    private int rapassVolume = DEF_RAPASS_VOLUME;
    private int chprioChoice = DEF_CHPRIO_CHOICE;
    private int watoneChoice = DEF_WATONE_CHOICE;

    private static Settings settings;
    public static Settings getInstance() {
        if(settings==null)
            settings = new Settings();
        return settings;
    }

    public synchronized int getMasterVolume() {
        return masterVolume;
    }
    public synchronized int getSignalVolume() {
        return signalVolume;
    }
    public synchronized int getChprioVolume() {
        return chprioVolume;
    }
    public synchronized int getRapassVolume() {
        return rapassVolume;
    }
    public synchronized int getChprioChoice() {
        return chprioChoice;
    }
    public synchronized int getWatoneChoice() {
        return watoneChoice;
    }

    public synchronized void setMasterVolume(int masterVolume) {
        this.masterVolume = masterVolume;
    }
    public synchronized void setSignalVolume(int signalVolume) {
        this.signalVolume = signalVolume;
    }
    public synchronized void setChprioVolume(int chprioVolume) {
        this.chprioVolume = chprioVolume;
    }
    public synchronized void setRapassVolume(int rapassVolume) {
        this.rapassVolume = rapassVolume;
    }
    public synchronized void setChprioChoice(int chprioChoice) {
        this.chprioChoice = chprioChoice;
    }
    public synchronized void setWatoneChoice(int watoneChoice) {
        this.watoneChoice = watoneChoice;
    }
}
