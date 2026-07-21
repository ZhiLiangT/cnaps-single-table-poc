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
SERVICES := SYSHEALTH DICTQRY BANKQRY CNAPS5701E CNAPS5701U CNAPS5701D CNAPS4609Q CNAPS5702I CNAPS5702A CNAPS5702R
SERVICE_FLAGS := $(foreach service,$(SERVICES),-s $(service))

.PHONY: all clean dirs

all: dirs $(SERVER)

dirs:
	mkdir -p bin obj

$(SERVER): $(SOURCES)
	buildserver $(SERVICE_FLAGS) -o bin/cnapspocsvr \
	  -f "$(CFLAGS) $(SOURCES)" \
	  -l "-L$(ORACLE_HOME) -lclntsh"

clean:
	rm -rf bin obj
