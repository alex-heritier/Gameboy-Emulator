
import java.io.File;
import java.io.DataInputStream;
import java.io.FileInputStream;

class Cart implements DataSource {
  private short[] bytes;

  public Cart(String filename) {
    File file = new File(filename);
    bytes = new short[(int) file.length()];

    try {
      byte tmp[] = new byte[(int) file.length()];

      DataInputStream dis = new DataInputStream(new FileInputStream(file));
      dis.readFully(tmp);
      dis.close();

      for (int i = 0; i < tmp.length; i++) {
        short val = tmp[i];
        val &= 0xFF;
        bytes[i] = val;
      }
    } catch (Exception e) {e.printStackTrace();}
  }

  public int size() {
    return bytes.length;
  }

  public String toString() {
    String z = "";
    for (int i = 0; i < bytes.length; i++) {
      z += Util.hex(bytes[i]) + " ";
    }

    return z;
  }

  @Override
  public short get(int address) {
    // check for out-of-bounds
    if (address < 0 || address > 0xFFFF) {
      Util.errn("Cart.get - out of bounds memory access: " + Util.hex(address));
      return 0;
    }

    return bytes[address];
  }

  @Override
  public void set(int address, short data) {
    // check for out-of-bounds
    if (address < 0 || address > 0xFFFF) {
      Util.errn("Cart.set - out of bounds memory access: " + Util.hex(address));
      return;
    }

    bytes[address] = data;
  }
}
