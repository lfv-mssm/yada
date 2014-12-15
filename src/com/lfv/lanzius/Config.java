package com.lfv.lanzius;

/**
 * <p>
 * Config
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
public interface Config {

        //HD Run Mode, all windows maximized
        public static final String  VERSION = "YADA 2.9";
        public static final String  TITLE   = "YADA 2.9";

        // -- COMMON --
        public static final boolean DEBUG                         = false;

        public static final boolean SERVER_SIZE_100P_WINDOW       = false;
    public static final boolean SERVER_SIZE_75P_WINDOW        = true;
    public static final boolean SERVER_SIZE_50P_WINDOW        = false;
    //HD 1
    public static final boolean SERVER_SIZE_FULLSCREEN        = false;
    public static final String  SERVER_AUTOLOAD_CONFIGURATION = null;
    public static final boolean SERVER_AUTOSTART_SERVER       = false;
    public static final String  SERVER_AUTOLOAD_EXERCISE      = null;
    public static final int     SERVER_AUTOSTART_GROUP        = 0;
    public static final boolean SERVER_EXIT_DIALOG            = true;

    public static final boolean SERVER_WRITE_PLAYERSETUP      = false;

    // -- CLIENT --
    public static final boolean CLIENT_SIZE_FULLSCREEN        = true;
    public static final boolean CLIENT_SIZE_100P_WINDOW       = false;
    public static final boolean CLIENT_SIZE_50P_WINDOW        = false;
    public static final boolean CLIENT_SIZE_800X600_WINDOW    = false;

    public static final boolean CLIENT_SERVERLESS             = false;
    public static final boolean CLIENT_EXIT_DIALOG            = true;


        // DEVELOPMENT MODE,
//    public static final String  VERSION = "YADA 2.2  developement";
//    public static final String  TITLE   = "YADA 2.2";
//
//    // -- COMMON --
//    public static final boolean DEBUG                         = true;
//
//    // -- SERVER --
//    public static final boolean SERVER_SIZE_FULLSCREEN        = false;
//    public static final boolean SERVER_SIZE_100P_WINDOW       = false;
//    public static final boolean SERVER_SIZE_75P_WINDOW        = false;
//    public static final boolean SERVER_SIZE_50P_WINDOW        = true;
//
////    public static final String  SERVER_AUTOLOAD_CONFIGURATION = "/home/mikael/Develop/yada2.2/yada/data/configurations/configuration-example-advanced.xml";
//    //public static final String  SERVER_AUTOLOAD_CONFIGURATION = "/home/mikael/Develop/yada2.2/yada/data/configurations/configuration-example-simple.xml";
//    public static final String  SERVER_AUTOLOAD_CONFIGURATION = "./data/configurations/configuration-example-simple.xml";
//    public static final boolean SERVER_AUTOSTART_SERVER       = true;
////    public static final String  SERVER_AUTOLOAD_EXERCISE      = "/home/mikael/Develop/yada2.2/yada/data/exercises/exercise-example-advanced.xml";
//    //public static final String  SERVER_AUTOLOAD_EXERCISE      = "/home/mikael/Develop/yada2.2/yada/data/exercises/exercise-example-simple.xml";
//    public static final String  SERVER_AUTOLOAD_EXERCISE      = "./data/exercises/exercise-example-simple.xml";
//    public static final int     SERVER_AUTOSTART_GROUP        = 1;
//    public static final boolean SERVER_EXIT_DIALOG            = false;
//
//    public static final boolean SERVER_WRITE_PLAYERSETUP      = false;
//
//    // -- CLIENT --
//    public static final boolean CLIENT_SIZE_FULLSCREEN        = false;
//    public static final boolean CLIENT_SIZE_100P_WINDOW       = false;
//    public static final boolean CLIENT_SIZE_50P_WINDOW        = true;
//    public static final boolean CLIENT_SIZE_800X600_WINDOW    = false;
//
//    public static final boolean CLIENT_SERVERLESS             = false;
//    public static final boolean CLIENT_EXIT_DIALOG            = false;

}
