; https://rednex.github.io/rgbds/rgbasm.5.html

SECTION "Jump",ROM0[$100]
  jp start

SECTION "Main",ROM0[$150]
start:
; setup display
  ld A, $92          ; Turn on LCD and sprites
	ldh [$FF00+$40], A
  ld A, $1B          ; Setup OAM palette
  ldh [$FF00+$48], A
  ld HL, $FE9F
  ld B, 0
; setup OAM
.set_bg_map:
  ; attributes
  ld A, $00
  ld [HL-], A
  ; tile index
  ld A, $01
  ld [HL-], A
  ; x position
  ld A, B
  ld [HL-], A
  ; y position
  ld A, B
  ld [HL-], A
  inc B
  inc B
  inc B
  inc B
  inc B
  inc B
  inc B
  inc B
  ld A, L
  cp $FF
  jr nz, .set_bg_map
; setup VRAM
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
  ld B, $20
.delay_1
  call move_sprite_y
  call move_sprite_x
  ld C, $FF
.delay_2
  dec C
  jr nz, .delay_2
  dec B
  jr nz, .delay_1
  jr .hang
build_tile:
  ld C, 8
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
move_sprite_y:
  ld HL, $FE10
  inc [HL]
  ret
move_sprite_x:
  ld HL, $FE11
  dec [HL]
  dec [HL]
  ret
