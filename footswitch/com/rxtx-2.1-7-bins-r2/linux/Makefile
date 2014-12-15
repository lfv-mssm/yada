#-----------------------------------------------------------------------------
#    Name      : Makefile
#    Purpose   : Generate the YADA jar file
#    Copyright : National Aerospace Laboratory NLR, NARSIM
#    Change history
#       Roalt Aalmoes, November 2009
#          Initial version
#-----------------------------------------------------------------------------
#
# --- Macro's ---
#
MACROS_MK	= $(OWN_ROOT)/etc/macro.mk
include $(MACROS_MK)

RECURSE_PATH	= $(OWN_ROOT)/tools/yada/footswitch/ftdi/ftdi-d2xx/linux
RECURSE_DIRS	= 
#
# --- Macro's ---
#

RXTX_DIR = $(OWN_ROOT)/tools/yada/footswitch/com/rxtx-2.1-7-bins-r2/linux/

all:     init


init:	
	$(O2)$(MKDIR) -p $(RXTX_DIR)/_$(ARCH)
	$(O2)$(CP)    $(RXTX_DIR)/$(ARCH)-unknown-linux-gnu/librxtxSerial.so $(RXTX_DIR)/_$(ARCH)
	$(O2)$(LN2CS) tools/yada/footswitch/com/rxtx-2.1-7-bins-r2/linux/_$(ARCH)/librxtxSerial.so lib/_$(ARCH)

clean:  
	$(O2)$(RM) $(ARCH_LIB_DIR)/librxtxSerial.so

veryclean: 
	$(O2)$(RM) -rf $(RXTX_DIR)/_$(ARCH)
	$(O2)$(RM) $(ARCH_LIB_DIR)/librxtxSerial.so


include $(RULES_DOC)
