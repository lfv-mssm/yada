/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_lfv_lanzius_application_FootSwitchController */

#ifndef _Included_com_lfv_lanzius_application_FootSwitchController
#define _Included_com_lfv_lanzius_application_FootSwitchController
#ifdef __cplusplus
extern "C" {
#endif
#undef SWITCH_STATE_DISCONNECTED
#define SWITCH_STATE_DISCONNECTED 0L
#undef SWITCH_STATE_UP
#define SWITCH_STATE_UP 1L
#undef SWITCH_STATE_PRESSED
#define SWITCH_STATE_PRESSED 2L
#undef SWITCH_STATE_DOWN
#define SWITCH_STATE_DOWN 3L
#undef SWITCH_STATE_RELEASED
#define SWITCH_STATE_RELEASED 4L
#undef FTSW_VERSION
#define FTSW_VERSION 530L
/*
 * Class:     com_lfv_lanzius_application_FootSwitchController
 * Method:    setSwitchFunction
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_com_lfv_lanzius_application_FootSwitchController_setSwitchFunction
  (JNIEnv *, jclass, jboolean);

/*
 * Class:     com_lfv_lanzius_application_FootSwitchController
 * Method:    getSwitchVersion
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_lfv_lanzius_application_FootSwitchController_getSwitchVersion
  (JNIEnv *, jclass);

/*
 * Class:     com_lfv_lanzius_application_FootSwitchController
 * Method:    getSwitchState
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_lfv_lanzius_application_FootSwitchController_getSwitchState
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif
