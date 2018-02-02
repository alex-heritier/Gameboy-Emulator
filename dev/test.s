; https://rednex.github.io/rgbds/rgbasm.5.html

SECTION "Main", ROM0

start:
di
ld sp, $ffee
ld bc, $dead
push bc
ld de, $beef
push de
pop bc
pop de
