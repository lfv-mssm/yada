package com.lfv.lanzius;

/**
 * <p>
 * Constants
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
public interface Constants {

    // Full view
    public static final int FULL_RADIO_MAXHEIGHT    = 100;
    public static final int FULL_RADIO_MINHEIGHT    = 35;
    public static final int FULL_RADIO_NROWS        = 5;

    public static final int FULL_PHONE_MAXHEIGHT    = 75;
    public static final int FULL_PHONE_MINHEIGHT    = 25;
    public static final int FULL_PHONE_NROWS        = 6;

    // Server view
    public static final int SERVERVIEW_TERMINAL_BORDER  = 6;
    public static final int SERVERVIEW_TERMINAL_WIDTH   = 140;
    public static final int SERVERVIEW_TERMINAL_HEIGHT  = 120;
    public static final int SERVERVIEW_GROUP_WIDTH      = 140;
    public static final int SERVERVIEW_SELECTION_BORDER = 3;

    // Times
    public static final int TIMEOUT_ACQUIRY          = 10000;
    public static final int TIMEOUT_LEAVE            = 10000;
    public static final int TIMEOUT_SHUTDOWN         =  5000;
    public static final int TIMEOUT_STARTSTOP        =  3000;

    public static final int DELAY_START              =  1500;
    public static final int DELAY_START_VARIANCE     =   500;
    public static final int DELAY_STOP               =   200;
    public static final int DELAY_RECONNECT          = 10000;
    public static final int DELAY_STARTSTOP          =  2000;
    public static final int DELAY_SWAP               =  2000;

    public static final int PERIOD_REACQUIRE         =  1000;
    public static final int PERIOD_FTSW_CONNECTED    =   200;
    public static final int PERIOD_FTSW_DISCONNECTED =  1200;

    // Control
    public static final int ID_BITSHIFT             = 12;
    public static final int ID_MASK_GROUP           = 0x00FFF000;
    public static final int ID_MASK_CHANNEL         = 0x00000FFF;
    public static final int ID_MASK_ROLE            = ID_MASK_CHANNEL;
    public static final int ID_MASK_PLAYER          = ID_MASK_CHANNEL;


    public static final int CHANNEL_COMMON          = 1;
    public static final int CHANNEL_PHONE           = 2;
    public static final int CHANNEL_FORWARD         = 3;
    public static final int CHANNEL_RADIO_START     = (1<<ID_BITSHIFT);

    public static final int SOUND_NONE              = 0;
    public static final int SOUND_RINGTONE          = 1;
    public static final int SOUND_RINGBACK          = 2;
    public static final int SOUND_RINGBUSY          = 3;
    public static final int SOUND_NOTIFY                        = 4;

    public static final int DEVICE_MOUSE            = 0;
    public static final int DEVICE_FTSW             = 1;

    public static final int RADIO_STATE_IDLE        = 0;
    public static final int RADIO_STATE_TALK_MOUSE  = 1;
    public static final int RADIO_STATE_TALK_FTSW   = 2;
    public static final int RADIO_STATE_CHANNEL     = 3;

    public static final int PHONE_STATE_IDLE        = 0;
    public static final int PHONE_STATE_DIALING_REQ = 1;
    public static final int PHONE_STATE_DIALING     = 2;
    public static final int PHONE_STATE_BUSY        = 3;
    public static final int PHONE_STATE_IN_CALL     = 4;
    public static final int PHONE_STATE_HANGUP_REQ  = 5;
    public static final int PHONE_STATE_RINGING     = 6;
    public static final int PHONE_STATE_ANSWER_REQ  = 7;

    public static final int CLIENT_STATE_UNINITIALIZED = 0;
    public static final int CLIENT_STATE_DISCONNECTED  = 1;
    public static final int CLIENT_STATE_CONNECTED     = 2;
    public static final int CLIENT_STATE_STARTED       = 3;
}
