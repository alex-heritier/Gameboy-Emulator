; https://rednex.github.io/rgbds/rgbasm.5.html

SECTION "Jump",ROM0[$100]
  jp start

SECTION "Main",ROM0[$150]
start:
  ld A, $FE
  ld [$FF00+$06], A ; setup timer modulo
  ld A, 5
  ld [$FF00+$07], A ; turn on timer
.hang
  jr .hang
