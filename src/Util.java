
class Util {
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

  public static void log(String z) {
    System.out.println(z);
  }
}
