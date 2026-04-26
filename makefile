.PHONY: build run test download-tests clean help

JAVAC ?= javac
JAVA ?= java
SRC_DIR := src
TEST_ROM_DIR := roms/tests/blargg

help:
	@echo "Game Boy emulator - available targets:"
	@echo "  make build            Compile every .java under $(SRC_DIR)/"
	@echo "  make run              Launch the emulator (default ROM roms/dr_mario.gb)"
	@echo "  make run ROM=<path>   Launch a specific ROM"
	@echo "  make test             Run headless Blargg test suite (default $(TEST_ROM_DIR))"
	@echo "  make test TEST=<dir>  Run headless tests under a specific directory"
	@echo "  make download-tests   Fetch / unpack Blargg test ROMs (cpu_instrs, instr_timing, mem_timing, halt_bug)"
	@echo "  make clean            Remove compiled .class files"

build:
	$(JAVAC) $(SRC_DIR)/*.java

run: build
ifdef ROM
	$(JAVA) -cp $(SRC_DIR) Tester "$(ROM)"
else
	$(JAVA) -cp $(SRC_DIR) Tester
endif

test: build
ifdef TEST
	$(JAVA) -cp $(SRC_DIR) Tester --test "$(TEST)"
else
	$(JAVA) -cp $(SRC_DIR) Tester --test $(TEST_ROM_DIR)
endif

# Downloads the Blargg test ROM suites and extracts the individual .gb
# files into roms/tests/blargg/. These suites are tiny (< 1 MB) but the
# user shouldn't have to fetch them manually before running `make test`.
download-tests:
	mkdir -p $(TEST_ROM_DIR)
	cd $(TEST_ROM_DIR) && \
	  for pkg in cpu_instrs instr_timing mem_timing halt_bug; do \
	    if [ ! -f "$$pkg.zip" ]; then \
	      echo "downloading $$pkg..."; \
	      curl -L -o "$$pkg.zip" "https://github.com/retrio/gb-test-roms/raw/master/$$pkg/$$pkg.zip" \
	        || curl -L -o "$$pkg.zip" "https://gbdev.gg8.se/files/roms/blargg-gb-tests/$$pkg.zip"; \
	    fi; \
	    if [ "$$pkg" = "halt_bug" ]; then \
	      unzip -o -j "$$pkg.zip" -d . > /dev/null || true; \
	    else \
	      unzip -o "$$pkg.zip" -d . > /dev/null || true; \
	    fi; \
	  done
	@echo "Blargg ROMs ready under $(TEST_ROM_DIR)"

clean:
	rm -f $(SRC_DIR)/*.class
