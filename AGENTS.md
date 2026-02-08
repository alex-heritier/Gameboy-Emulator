# Gameboy Emulator - Agent Guidelines

## Build Commands

```bash
# Build all Java files
make build

# Build and run the emulator
make run

# Quick compile + run (clears terminal first)
./test.sh

# Compile single file (if needed)
javac src/Filename.java

# Run with specific classpath
java -cp src/ Tester
```

## Testing

This project uses ROM-based integration tests (no JUnit):
- Test ROMs located in `roms/tests/`
- Edit `Tester.java` to load specific test ROM
- Tests output pass/fail to console via serial link emulation (0xFF01-0xFF02)
- Check README.md for current test progress

## Code Style

### Formatting
- **Indent**: 2 spaces (no tabs)
- **Line length**: ~100 chars soft limit
- **Braces**: Same-line opening brace (K&R style)
- **Empty lines**: One blank line between methods, two between classes

### Naming
- **Classes**: PascalCase (CPU, MMU, Gameboy, CPUState)
- **Methods**: camelCase (getReg, handleInterrupts, tick)
- **Fields**: camelCase (private fields often lowercase: state, mmu, cart)
- **Constants**: UPPER_CASE for static finals
- **Enums**: PascalCase members (Flag.Z, R.A, R.B)

### Types
- Use `short` for 8-bit values (0-255 / 0x00-0xFF)
- Use `int` for 16-bit addresses (0-65535 / 0x0000-0xFFFF)
- Return `short` from 8-bit arithmetic via CPUMath.Result.get8()

### Imports
- Group java.* imports at top
- No wildcard imports (explicit only)
- No package declarations (default package)

### Error Handling
- Use `Util.errn(String)` for errors (prints to stderr)
- Use `Util.debug(String)` for debug output (toggle via Util.debug = true/false)
- Validate bounds before memory access (MMU.get/set check 0-0xFFFF)
- Return early on error conditions rather than deep nesting

### Documentation
- Single-line comments: `// Description`
- Multi-line comments: `/* ... */` for section headers
- Instruction comments: `// OPCODE - Mnemonic description`
- No Javadoc required

## Architecture

### Key Components
- **CPU**: Instruction decode/execute, interrupt handling
- **CPUState**: Register state, flags, halt/IME status
- **MMU**: Memory management unit, bank switching, I/O handling
- **PPU**: Picture processing unit (graphics)
- **Cart**: ROM cartridge loading
- **TimerHandler**: DIV/TIMA timer emulation
- **Instructions**: CPU instruction implementations
- **CPUMath**: 8/16-bit arithmetic with flag calculations

### Memory Map (Gameboy)
- 0x0000-0x3FFF: ROM Bank 00
- 0x4000-0x7FFF: ROM Bank 01 (switchable)
- 0x8000-0x9FFF: VRAM
- 0xA000-0xBFFF: External RAM
- 0xC000-0xDFFF: Work RAM
- 0xE000-0xFDFF: Echo RAM (mirror of C000-DDFF)
- 0xFE00-0xFE9F: OAM (sprite attributes)
- 0xFF00-0xFF7F: I/O Registers
- 0xFF80-0xFFFE: High RAM
- 0xFFFF: Interrupt Enable Register

## Conventions

### Flags (F register bits)
- Bit 7: Z (Zero)
- Bit 6: N (Subtract)
- Bit 5: H (Half Carry)
- Bit 4: C (Carry)

### Development Workflow
- Keep files under ~500 LOC
- Add instruction implementations to Instructions.java
- Update decode switch in CPU.java for new opcodes
- Update Util.mnemonic() for disassembly output
- Test with blargg test ROMs when modifying CPU

### ROM Development
- Assembly files in `dev/`
- Build script: `dev/build.sh <basename>` (requires rgbasm/rgblink)
- Output: `<basename>.gb` for testing

## References
- Pan Docs: http://bgb.bircd.org/pandocs.htm
- Opcode Table: http://www.pastraiser.com/cpu/gameboy/gameboy_opcodes.html
- Instruction Reference: http://www.devrs.com/gb/files/GBCPU_Instr.html
