; https://rednex.github.io/rgbds/rgbasm.5.html

; LD tests

SECTION "Main", ROM0

;    - ld SP, HL
;    - ld BC, d16
;    - ld [d16], SP
;    - ld HL, SP + 8
;    - pop BC
;    - push BC

; 16 bit tests
start:
