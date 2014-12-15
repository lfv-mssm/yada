
# Modify include paths here (pointing to your jdk directory)
INC = -I/usr/lib/jvm/java-6-sun/include -I/usr/lib/jvm/java-6-sun/include/linux -I/net/narsnfs/local/5ses4/workdir_integr/tools/yada/footswitch/libftdi-0.20/src
LIB = -lftdi -L/net/narsnfs/local/5ses4/workdir_integr/tools/yada/footswitch/libftdi-0.20/src/.libs

CC = gcc
CFLAGS = -O2 -g $(INC) $(DEF)
LDFLAGS = -shared -Wall -fPIC
ARCH=`arch`
ARCH_DIR=_$(ARCH)

all:
	mkdir $(ARCH_DIR)
	gcc $(CFLAGS) $(LDFLAGS) os-ftsw.c -o $(ARCH_DIR)/libftsw.so $(LIB)

init:

clean:
	rm -f $(ARCH_DIR)/libftsw.so
	rmdir $(ARCH_DIR)


