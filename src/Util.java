
class Util {

  public static boolean debug = true;

  public static String hex(byte z) {
    return String.format("%02X", z);
  }

  public static String hex(short z) {
    return String.format("%02X", z);
  }

  public static String hex(int z) {
    return String.format("%04X", z);
  }

  public static String hex(CPUMath.Result z) {
    return hex(z.get16());
  }

  public static String bin(short z) {
    String s1 = String.format("%8s", Integer.toBinaryString(z & 0xFF)).replace(' ', '0');
    return s1;
  }

  public static String bin(int z) {
    return Integer.toBinaryString(z);
  }


  public static void errn(String z) {
    System.err.println("ERROR: " + z);
  }

  public static void debug(String z) {
      if (!debug) return;
      log(z);
  }

  public static void log(String z) {
    System.out.println(z);
  }
  public static void log() { log(""); }


  /*
   * Converts an opcode and it's next 2 bytes to a mnemonic representation
   * byte1 and byte2 are passed in case the instruction is multi-byte
   */
  public static String mnemonic(short opcode, short byte1, short byte2) {
    String z = "";
    switch (opcode) {
      case 0x00: z = "nop"; break;
      case 0x01: z = "ld BC, " + hex(byte2) + hex(byte1); break;
      case 0x02: z = "ld [BC], A"; break;
      case 0x03: z = "inc BC"; break;
      case 0x04: z = "inc B"; break;
      case 0x05: z = "dec B"; break;
      case 0x06: z = "ld B, " + hex(byte1); break;
      case 0x07: z = "rlca"; break;
      case 0x08: z = "ld [HL], SP"; break;
      case 0x09: z = "add HL, BC"; break;
      case 0x0A: z = "ld A, [BC]"; break;
      case 0x0B: z = "dec BC"; break;
      case 0x0C: z = "inc C"; break;
      case 0x0D: z = "dec C"; break;
      case 0x0E: z = "ld C, " + hex(byte1); break;
      case 0x0F: z = "rrca"; break;
      case 0x10: z = "stop"; break;
      case 0x11: z = "ld DE, " + hex(byte2) + hex(byte1); break;
      case 0x12: z = "ld [DE], A"; break;
      case 0x13: z = "inc DE"; break;
      case 0x14: z = "inc D"; break;
      case 0x15: z = "dec D"; break;
      case 0x16: z = "ld D, " + hex(byte1); break;
      case 0x17: z = "rla"; break;
      case 0x18: z = "jr " + hex(byte1); break;
      case 0x19: z = "ld HL, DE"; break;
      case 0x1A: z = "ld A, [DE]"; break;
      case 0x1B: z = "dec DE"; break;
      case 0x1C: z = "inc E"; break;
      case 0x1D: z = "dec E"; break;
      case 0x1E: z = "ld E, " + hex(byte1); break;
      case 0x1F: z = "rra"; break;
      case 0x20: z = "jr NZ, " + hex(byte1); break;
      case 0x21: z = "ld HL, " + hex(byte2) + hex(byte1); break;
      case 0x22: z = "ld [HL+], A"; break;
      case 0x23: z = "inc HL"; break;
      case 0x24: z = "inc H"; break;
      case 0x25: z = "dec H"; break;
      case 0x26: z = "ld H, " + hex(byte1); break;
      case 0x27: z = "daa"; break;
      case 0x28: z = "jr Z, " + hex(byte1); break;
      case 0x29: z = "ld HL, HL"; break;
      case 0x2A: z = "ld A, [HL+]"; break;
      case 0x2B: z = "dec HL"; break;
      case 0x2C: z = "inc L"; break;
      case 0x2D: z = "dec L"; break;
      case 0x2E: z = "ld L, " + hex(byte1); break;
      case 0x2F: z = "cpl"; break;
      case 0x30: z = "jr NC, " + hex(byte1); break;
      case 0x31: z = "ld SP, " + hex(byte2) + hex(byte1); break;
      case 0x32: z = "ld [HL-], A"; break;
      case 0x33: z = "inc SP"; break;
      case 0x34: z = "inc [HL]"; break;
      case 0x35: z = "dec [HL]"; break;
      case 0x36: z = "ld [HL], " + hex(byte1); break;
      case 0x37: z = "scf"; break;
      case 0x38: z = "jr C, " + hex(byte1); break;
      case 0x39: z = "ld HL, SP"; break;
      case 0x3A: z = "ld A, [HL-]"; break;
      case 0x3B: z = "dec SP"; break;
      case 0x3C: z = "inc A"; break;
      case 0x3D: z = "dec A"; break;
      case 0x3E: z = "ld A, " + hex(byte1); break;
      case 0x3F: z = "ccf"; break;
      case 0x40: z = "ld B, B"; break;
      case 0x41: z = "ld B, C"; break;
      case 0x42: z = "ld B, D"; break;
      case 0x43: z = "ld B, E"; break;
      case 0x44: z = "ld B, H"; break;
      case 0x45: z = "ld B, L"; break;
      case 0x46: z = "ld B, [HL]"; break;
      case 0x47: z = "ld B, A"; break;
      case 0x48: z = "ld C, B"; break;
      case 0x49: z = "ld C, C"; break;
      case 0x4A: z = "ld C, D"; break;
      case 0x4B: z = "ld C, E"; break;
      case 0x4C: z = "ld C, H"; break;
      case 0x4D: z = "ld C, L"; break;
      case 0x4E: z = "ld C, [HL]"; break;
      case 0x4F: z = "ld C, A"; break;
      case 0x50: z = "ld D, B"; break;
      case 0x51: z = "ld D, C"; break;
      case 0x52: z = "ld D, D"; break;
      case 0x53: z = "ld D, E"; break;
      case 0x54: z = "ld D, H"; break;
      case 0x55: z = "ld D, L"; break;
      case 0x56: z = "ld D, [HL]"; break;
      case 0x57: z = "ld D, A"; break;
      case 0x58: z = "ld E, B"; break;
      case 0x59: z = "ld E, C"; break;
      case 0x5A: z = "ld E, D"; break;
      case 0x5B: z = "ld E, E"; break;
      case 0x5C: z = "ld E, H"; break;
      case 0x5D: z = "ld E, L"; break;
      case 0x5E: z = "ld E, [HL]"; break;
      case 0x5F: z = "ld E, A"; break;
      case 0x60: z = "ld H, B"; break;
      case 0x61: z = "ld H, C"; break;
      case 0x62: z = "ld H, D"; break;
      case 0x63: z = "ld H, E"; break;
      case 0x64: z = "ld H, H"; break;
      case 0x65: z = "ld H, L"; break;
      case 0x66: z = "ld H, [HL]"; break;
      case 0x67: z = "ld H, A"; break;
      case 0x68: z = "ld L, B"; break;
      case 0x69: z = "ld L, C"; break;
      case 0x6A: z = "ld L, D"; break;
      case 0x6B: z = "ld L, E"; break;
      case 0x6C: z = "ld L, H"; break;
      case 0x6D: z = "ld L, L"; break;
      case 0x6E: z = "ld L, [HL]"; break;
      case 0x6F: z = "ld L, A"; break;
      case 0x70: z = "ld [HL], L"; break;
      case 0x71: z = "ld [HL], L"; break;
      case 0x72: z = "ld [HL], L"; break;
      case 0x73: z = "ld [HL], L"; break;
      case 0x74: z = "ld [HL], L"; break;
      case 0x75: z = "ld [HL], L"; break;
      case 0x76: z = "halt"; break;
      case 0x77: z = "ld [HL], L"; break;
      case 0x78: z = "ld A, B"; break;
      case 0x79: z = "ld A, C"; break;
      case 0x7A: z = "ld A, D"; break;
      case 0x7B: z = "ld A, E"; break;
      case 0x7C: z = "ld A, H"; break;
      case 0x7D: z = "ld A, L"; break;
      case 0x7E: z = "ld A, [HL]"; break;
      case 0x7F: z = "ld A, A"; break;
      case 0x80: z = "add A, B"; break;
      case 0x81: z = "add A, C"; break;
      case 0x82: z = "add A, D"; break;
      case 0x83: z = "add A, E"; break;
      case 0x84: z = "add A, H"; break;
      case 0x85: z = "add A, L"; break;
      case 0x86: z = "add A, [HL]"; break;
      case 0x87: z = "add A, A"; break;
      case 0x88: z = "adc A, B"; break;
      case 0x89: z = "adc A, C"; break;
      case 0x8A: z = "adc A, D"; break;
      case 0x8B: z = "adc A, E"; break;
      case 0x8C: z = "adc A, H"; break;
      case 0x8D: z = "adc A, L"; break;
      case 0x8E: z = "adc A, [HL]"; break;
      case 0x8F: z = "adc A, A"; break;
      case 0x90: z = "sub B"; break;
      case 0x91: z = "sub C"; break;
      case 0x92: z = "sub D"; break;
      case 0x93: z = "sub E"; break;
      case 0x94: z = "sub H"; break;
      case 0x95: z = "sub L"; break;
      case 0x96: z = "sub [HL]"; break;
      case 0x97: z = "sub A"; break;
      case 0x98: z = "sbc A, B"; break;
      case 0x99: z = "sbc A, C"; break;
      case 0x9A: z = "sbc A, D"; break;
      case 0x9B: z = "sbc A, E"; break;
      case 0x9C: z = "sbc A, H"; break;
      case 0x9D: z = "sbc A, L"; break;
      case 0x9E: z = "sbc A, [HL]"; break;
      case 0x9F: z = "sbc A, A"; break;
      case 0xA0: z = "and B"; break;
      case 0xA1: z = "and C"; break;
      case 0xA2: z = "and D"; break;
      case 0xA3: z = "and E"; break;
      case 0xA4: z = "and H"; break;
      case 0xA5: z = "and L"; break;
      case 0xA6: z = "and [HL]"; break;
      case 0xA7: z = "and A"; break;
      case 0xA8: z = "xor B"; break;
      case 0xA9: z = "xor C"; break;
      case 0xAA: z = "xor D"; break;
      case 0xAB: z = "xor E"; break;
      case 0xAC: z = "xor H"; break;
      case 0xAD: z = "xor L"; break;
      case 0xAE: z = "xor [HL]"; break;
      case 0xAF: z = "xor A"; break;
      case 0xB0: z = "or B"; break;
      case 0xB1: z = "or C"; break;
      case 0xB2: z = "or D"; break;
      case 0xB3: z = "or E"; break;
      case 0xB4: z = "or H"; break;
      case 0xB5: z = "or L"; break;
      case 0xB6: z = "or [HL]"; break;
      case 0xB7: z = "or A"; break;
      case 0xB8: z = "cp B"; break;
      case 0xB9: z = "cp C"; break;
      case 0xBA: z = "cp D"; break;
      case 0xBB: z = "cp E"; break;
      case 0xBC: z = "cp H"; break;
      case 0xBD: z = "cp L"; break;
      case 0xBE: z = "cp [HL]"; break;
      case 0xBF: z = "cp A"; break;
      case 0xC0: z = "ret NZ"; break;
      case 0xC1: z = "pop BC"; break;
      case 0xC2: z = "jp NZ, " + hex(byte2) + hex(byte1); break;
      case 0xC3: z = "jp " + hex(byte2) + hex(byte1); break;
      case 0xC4: z = "call NZ, " + hex(byte2) + hex(byte1); break;
      case 0xC5: z = "push BC"; break;
      case 0xC6: z = "add A, " + hex(byte1); break;
      case 0xC7: z = "rst 0x00"; break;
      case 0xC8: z = "ret Z"; break;
      case 0xC9: z = "ret"; break;
      case 0xCA: z = "jp Z, " + hex(byte2) + hex(byte1); break;
      case 0xCB: z = "CB - " + cb_mnemonic(byte1); break;
      case 0xCC: z = "call Z, " + hex(byte2) + hex(byte1); break;
      case 0xCD: z = "call " + hex(byte2) + hex(byte1); break;
      case 0xCE: z = "adc A, " + hex(byte1); break;
      case 0xCF: z = "rst 0x08"; break;
      case 0xD0: z = "ret NC"; break;
      case 0xD1: z = "pop DE"; break;
      case 0xD2: z = "jp NC, " + hex(byte2) + hex(byte1);; break;
      case 0xD3: z = "N/A"; break;
      case 0xD4: z = "call NC, " + hex(byte2) + hex(byte1);; break;
      case 0xD5: z = "push DE"; break;
      case 0xD6: z = "sub " + hex(byte1); break;
      case 0xD7: z = "rst 0x10"; break;
      case 0xD8: z = "ret C"; break;
      case 0xD9: z = "reti"; break;
      case 0xDA: z = "jp C, " + hex(byte2) + hex(byte1);; break;
      case 0xDB: z = "N/A"; break;
      case 0xDC: z = "call C, " + hex(byte2) + hex(byte1); break;
      case 0xDD: z = "N/A"; break;
      case 0xDE: z = "sbc A, " + hex(byte1); break;
      case 0xDF: z = "rst 0x18"; break;
      case 0xE0: z = "ldh [0xFF00+" + hex(byte1) + "], A"; break;
      case 0xE1: z = "pop HL"; break;
      case 0xE2: z = "ld (C), A"; break;
      case 0xE3: z = "N/A"; break;
      case 0xE4: z = "N/A"; break;
      case 0xE5: z = "push HL"; break;
      case 0xE6: z = "and " + hex(byte1); break;
      case 0xE7: z = "rst 0x20"; break;
      case 0xE8: z = "add SP, " + hex(byte1); break;
      case 0xE9: z = "jp HL"; break;
      case 0xEA: z = "ld [HL], A"; break;
      case 0xEB: z = "N/A"; break;
      case 0xEC: z = "N/A"; break;
      case 0xED: z = "N/A"; break;
      case 0xEE: z = "xor " + hex(byte1); break;
      case 0xEF: z = "rst 0x28"; break;
      case 0xF0: z = "ldh A, [0xFF00+" + hex(byte1) + "]"; break;
      case 0xF1: z = "pop AF"; break;
      case 0xF2: z = "ld A, (C)"; break;
      case 0xF3: z = "di"; break;
      case 0xF4: z = "N/A"; break;
      case 0xF5: z = "push AF"; break;
      case 0xF6: z = "or " + hex(byte1); break;
      case 0xF7: z = "rst 0x30"; break;
      case 0xF8: z = "ld HL, SP + " + hex(byte1); break;
      case 0xF9: z = "ld SP, HL"; break;
      case 0xFA: z = "ld A, [HL]"; break;
      case 0xFB: z = "ei"; break;
      case 0xFC: z = "N/A"; break;
      case 0xFD: z = "N/A"; break;
      case 0xFE: z = "cp " + hex(byte1); break;
      case 0xFF: z = "rst 0x38"; break;
      default:   z = hex(opcode); break;
    }
    return z;
  }

  private static String cb_mnemonic(short instruction) {
    String z = "";
    switch (instruction) {
      case 0x11: z = "rl C"; break;
      default:   z = hex(instruction);
    }
    return z;
  }
}
