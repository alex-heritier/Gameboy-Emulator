# Game Boy Emulator

A Nintendo Game Boy (DMG) emulator written in Java. The CPU, timer, PPU,
joypad and APU are all implemented, and the emulator passes every
Blargg CPU / timing / halt-bug test in the standard suite.

```
$ make test
Game Boy test suite
###################
ROM dir: roms/tests/blargg
Max ticks/ROM: 50000000

[PASS] .../cpu_instrs/individual/01-special.gb
[PASS] .../cpu_instrs/individual/02-interrupts.gb
[PASS] .../cpu_instrs/individual/03-op sp,hl.gb
[PASS] .../cpu_instrs/individual/04-op r,imm.gb
[PASS] .../cpu_instrs/individual/05-op rp.gb
[PASS] .../cpu_instrs/individual/06-ld r,r.gb
[PASS] .../cpu_instrs/individual/07-jr,jp,call,ret,rst.gb
[PASS] .../cpu_instrs/individual/08-misc instrs.gb
[PASS] .../cpu_instrs/individual/09-op r,r.gb
[PASS] .../cpu_instrs/individual/10-bit ops.gb
[PASS] .../cpu_instrs/individual/11-op a,(hl).gb
[PASS] .../cpu_instrs/cpu_instrs.gb
[PASS] .../halt_bug.gb
[PASS] .../instr_timing/instr_timing.gb
[PASS] .../mem_timing/individual/01-read_timing.gb
[PASS] .../mem_timing/individual/02-write_timing.gb
[PASS] .../mem_timing/individual/03-modify_timing.gb
[PASS] .../mem_timing/mem_timing.gb

Summary: 18 passed, 0 failed, 0 timed out
```

## Quick start

```bash
make build           # compile everything under src/
make download-tests  # fetch & unpack Blargg test ROMs into roms/tests/blargg/
make test            # run the whole test suite headless
make run             # run the default ROM (roms/dr_mario.gb)
make run ROM=<path>  # run any .gb file
```

## Controls

| Key        | Game Boy button |
|------------|-----------------|
| W / A / S / D | Up / Left / Down / Right |
| J          | A               |
| K          | B               |
| Enter      | Start           |
| Shift      | Select          |
| M          | Toggle audio    |

## Features

- **CPU**: Full LR35902 instruction set (including CB-prefixed opcodes)
  with accurate cycle timing, the HALT bug, and the one-instruction EI
  delay. Services VBlank / LCD STAT / Timer / Serial / Joypad
  interrupts.
- **Memory**: 64 KiB address space with correct echo-RAM mirroring,
  OAM DMA, and read-only protections.
- **MBCs**: NoMBC, MBC1, MBC2, MBC3 (with optional RTC stub), and MBC5;
  battery-backed cartridge RAM is persisted to `<rom>.sav`.
- **PPU**: Mode 2 / 3 / 0 / 1 state machine at M-cycle granularity, the
  LCDC/LY/LYC/STAT register quirks, the window layer, and 10-sprite
  per scanline priority.
- **Timer**: 16-bit internal counter driven DIV/TIMA with falling-edge
  detection, TMA reload delay, and correct TAC handling.
- **Joypad**: Column-select with 0-on-press semantics and rising-edge
  joypad interrupt generation.
- **APU**: All four channels (two squares, wave, noise), frame
  sequencer for length/envelope/sweep, and master mixer via
  `javax.sound.sampled`.
- **Debugger**: `set`/`unset` breakpoints, memory dumping, single-step
  via the interactive console prompt when a breakpoint is hit.

## Testing

The test runner (`src/Tester.java`) recursively discovers every `.gb`
under `roms/tests/blargg` (or a user-specified directory), runs each
headless, and reports a pass / fail / timeout using the serial
output (`FF01 / FF02`). For ROMs that only print to the LCD
(`halt_bug.gb`), the runner also scans the BG tile map for
"Passed"/"Failed" text.

Useful commands:

```bash
make test                       # run all Blargg tests
make test TEST=roms/tests/blargg/cpu_instrs           # only cpu_instrs
make test TEST=roms/tests/blargg/mem_timing/individual # only mem_timing subtests
```

You can bump the per-ROM tick budget via:

```bash
java -cp src/ Tester --test roms/tests/blargg 80000000
```

## Repository layout

```
src/          Java sources (no package; default package)
roms/         Retail ROMs (not checked in) and tests/ for Blargg tests
dev/          Toolchain shims for building small RGBDS test ROMs
Makefile      Thin wrappers around javac / java
AGENTS.md     Code-style & architecture notes for AI agents
```

## References

- Pan Docs: https://gbdev.io/pandocs/
- Opcode table: http://www.pastraiser.com/cpu/gameboy/gameboy_opcodes.html
- Blargg test suite: https://github.com/retrio/gb-test-roms
- TCAGBD timing manual: https://github.com/AntonioND/giibiiadvance/blob/master/docs/TCAGBD.pdf
