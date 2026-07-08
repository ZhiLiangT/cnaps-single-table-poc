APP_HOME ?= /opt/ruisui-bank-sim
TUXDIR ?= /opt/tuxedo
ORACLE_HOME ?= /opt/oracle/instantclient
CC ?= gcc

INCLUDES := -Iinclude -I$(TUXDIR)/include -I$(ORACLE_HOME)/sdk/include
CFLAGS ?= -Wall -Wextra -O2 $(INCLUDES)
SERVER := bin/cnapspocsvr
SOURCES := src/cnapspocsvr.c \
	$(wildcard src/services/*.c) \
	$(wildcard src/common/*.c)

.PHONY: all clean dirs

all: dirs $(SERVER)

dirs:
	mkdir -p bin obj

$(SERVER): $(SOURCES)
	buildserver -o bin/cnapspocsvr \
	  -f "$(SOURCES)" \
	  -l "-L$(ORACLE_HOME) -lclntsh"

clean:
	rm -rf bin obj
