
#include "os-ftsw.h"
#include <ftdi.h>

//#define HANDLE_INVALID (FT_HANDLE)(-1)
#define OFTSW_CTS_ON 0x0010

struct ftdi_context ftdic;
static int state = SWITCH_STATE_DISCONNECTED;
static unsigned long cts_status = OFTSW_CTS_ON;
int init_ok=0;
int open_ok=0;

JNIEXPORT jint JNICALL Java_com_lfv_lanzius_application_FootSwitchController_getSwitchVersion(JNIEnv *env, jclass cls) {
        if (ftdi_init(&ftdic) == 0) {
                init_ok=1;
                
        }
        return FTSW_VERSION;
}

JNIEXPORT void JNICALL Java_com_lfv_lanzius_application_FootSwitchController_setSwitchFunction(JNIEnv *env, jclass cls, jboolean inv) {
        if(inv) {
                cts_status = 0;
        } else {
                cts_status = OFTSW_CTS_ON;
        }
}

JNIEXPORT jint JNICALL Java_com_lfv_lanzius_application_FootSwitchController_getSwitchState(JNIEnv *env, jclass cls) {
        if(state == SWITCH_STATE_DISCONNECTED) {
                if(init_ok) {
                        if (ftdi_usb_open(&ftdic, 0x0403, 0x6001) < 0) {
                                open_ok=1;
                        } else {
                                state = SWITCH_STATE_UP;
                        }
                } else {
                        if (ftdi_init(&ftdic) == 0) {
                                init_ok=1;
                
                        }
                }
        } else {
                int is_disconnected = 0;
                unsigned short status = 0;
                if(ftdi_poll_modem_status(&ftdic, &status) < 0) {
                        is_disconnected=1;
                        if(open_ok) {
                                ftdi_usb_close(&ftdic);
                                open_ok=0;
                        }
                }

                if(state==SWITCH_STATE_UP) {
                        if(is_disconnected) {
                                state = SWITCH_STATE_DISCONNECTED;
                        } else if((status&OFTSW_CTS_ON)==cts_status) {
                                state = SWITCH_STATE_PRESSED;
                        }
                } else if(state==SWITCH_STATE_PRESSED) {
                        state = SWITCH_STATE_DOWN;
                } else if(state==SWITCH_STATE_DOWN) {
                        if(is_disconnected||((status&OFTSW_CTS_ON)!=cts_status)) {
                                state = SWITCH_STATE_RELEASED;
                        }
                } else if(state==SWITCH_STATE_RELEASED) {
                        state = SWITCH_STATE_UP;
                }
        }
        return state;
}

