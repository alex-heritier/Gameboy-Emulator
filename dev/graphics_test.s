; https://rednex.github.io/rgbds/rgbasm.5.html

; LD tests

SECTION "Main", ROM0

start:
  ld A, $91          ; Turn on LCD
	ldh [$FF00+$40], A
  ld A, $1B          ; Setup BG palette
  ldh [$FF00+$47], A
  ld A, $1
  ld HL, $98FF
.set_bg_map:
  ld [HL-], A
  bit 2, H
  jr z, .set_bg_map
.build_the_tiles
  ; white tile
  ld HL, $8000
  ld A, $FF
  ld B, $FF
  call build_tile
  ; grey tile
  ld HL, $8010
  ld A, $FF
  ld B, $00
  call build_tile
  ld A, 0
.hang:
  ldh [$FF00+$42], A
  inc A
  ld B, $8
.delay_1
  ld C, $FF
.delay_2
  dec C
  jr nz, .delay_2
  dec B
  jr nz, .delay_1
  jr .hang
build_tile:
  ld C, 4
  push DE
.tile_loop
  ld [HL+], A
  ld D, A
  ld A, B
  ld [HL+], A
  ld A, D
  dec C
  jr nz, .tile_loop
.loop_done
  pop DE
  ret
