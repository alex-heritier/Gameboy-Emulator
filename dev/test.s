; https://rednex.github.io/rgbds/rgbasm.5.html

SECTION "Jump",ROM0[$100]
  jp start

SECTION "Main",ROM0[$150]
start:
  ld A, $00
  ld B, $99
.test_loop
  rrca
  jr nz, .test_loop
.hang
  jr .hang
