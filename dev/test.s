; https://rednex.github.io/rgbds/rgbasm.5.html

; LD tests

SECTION "Interrupt",ROM0[$50]
interrupt:
  ld A, $FF
  ld B, $EE

SECTION "Jump",ROM0[$100]
jp start

SECTION "Main",ROM0[$150]
start:
; DAA
.hang
  jr .hang
