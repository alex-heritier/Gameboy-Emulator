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
.set_bg_map
  ld [HL-], A
  bit 2, H
  jr z, .set_bg_map
.build_the_tiles
  ; white tile
  ld HL, $8000
  ld A, $FF
  ld B, $FF
  call .build_tile
  ; grey tile
  ld HL, $8010
  ld A, $FF
  ld B, $00
  call .build_tile
  ld A, 0
.hang
  ldh [$FF00+$43], A
  inc A
  inc A
  jr .hang
.build_tile
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ld A, B
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ld [HL+], A
  ret
