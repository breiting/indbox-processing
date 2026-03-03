package indbox;

import processing.core.PApplet;
import com.fazecast.jSerialComm.SerialPort;

import java.nio.charset.StandardCharsets;

public class INDbox {
  private final PApplet p;

  private SerialPort port;
  private final StringBuilder rx = new StringBuilder(256);

  private int b1 = 0;
  private int b2 = 0;
  private int pot = 0;
  private float dist = 0;

  private String lastLine = "";

  public INDbox(PApplet parent) {
    this(parent, null, 115200);
  }

  // portHint = substring match against system port name or description
  public INDbox(PApplet parent, String portHint, int baud) {
    this.p = parent;

    port = autoFindPort(portHint);
    if (port == null) {
      p.println("[INDbox] No serial ports found.");
      return;
    }

    port.setBaudRate(baud);
    port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

    if (!port.openPort()) {
      p.println("[INDbox] Failed to open port: " + port.getSystemPortName());
      port = null;
      return;
    }

    p.println("[INDbox] Connected: " + port.getSystemPortName() +
              " (" + safe(port.getDescriptivePortName()) + ")");
  }

  public void update() {
    if (port == null) return;

    int available = port.bytesAvailable();
    if (available <= 0) return;

    byte[] buf = new byte[Math.min(available, 4096)];
    int n = port.readBytes(buf, buf.length);
    if (n <= 0) return;

    rx.append(new String(buf, 0, n, StandardCharsets.UTF_8));

    int idx;
    while ((idx = rx.indexOf("\n")) >= 0) {
      String line = rx.substring(0, idx);
      rx.delete(0, idx + 1);

      line = line.trim();
      if (line.length() == 0) continue;

      // ignore optional header/comments
      if (line.startsWith("b1") || line.startsWith("#")) continue;

      if (parseLine(line)) {
        lastLine = line;
      }
    }
  }

  private boolean parseLine(String line) {
    // expected: b1,b2,pot,dist
    String[] parts = PApplet.split(line, ',');
    if (parts == null || parts.length < 4) return false;

    try {
      int nb1 = PApplet.parseInt(parts[0].trim());
      int nb2 = PApplet.parseInt(parts[1].trim());
      int npot = PApplet.parseInt(parts[2].trim());
      float ndist = PApplet.parseFloat(parts[3].trim());

      nb1 = (nb1 != 0) ? 1 : 0;
      nb2 = (nb2 != 0) ? 1 : 0;
      npot = PApplet.constrain(npot, 0, 4095);
      if (Float.isNaN(ndist) || Float.isInfinite(ndist)) return false;

      b1 = nb1;
      b2 = nb2;
      pot = npot;
      dist = ndist;

      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private SerialPort autoFindPort(String hint) {
    SerialPort[] ports = SerialPort.getCommPorts();
    if (ports == null || ports.length == 0) return null;

    if (hint != null && hint.length() > 0) {
      String h = hint.toLowerCase();
      for (SerialPort sp : ports) {
        String sys = safe(sp.getSystemPortName()).toLowerCase();
        String desc = safe(sp.getDescriptivePortName()).toLowerCase();
        if (sys.contains(h) || desc.contains(h)) return sp;
      }
    }

    // heuristic: prefer usb/tty-like names in description or system name
    for (SerialPort sp : ports) {
      String sys = safe(sp.getSystemPortName()).toLowerCase();
      String desc = safe(sp.getDescriptivePortName()).toLowerCase();
      if (sys.contains("usb") || sys.contains("tty") || desc.contains("usb") || desc.contains("serial")) {
        return sp;
      }
    }

    return ports[0];
  }

  private String safe(String s) {
    return (s == null) ? "" : s;
  }

  public void close() {
    if (port != null) {
      try { port.closePort(); } catch (Exception ignored) {}
      port = null;
    }
  }

  // getters
  public int button1() { return b1; }
  public int button2() { return b2; }
  public int potRaw() { return pot; }
  public float distRaw() { return dist; }
  public String lastLine() { return lastLine; }
}
