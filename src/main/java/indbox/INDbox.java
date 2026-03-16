package indbox;

import processing.core.PApplet;
import processing.event.KeyEvent;
import com.fazecast.jSerialComm.SerialPort;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * INDbox provides access to a standardized four-channel input device for Processing.
 *
 * <p>The device exposes:
 * <ul>
 *   <li>button 1</li>
 *   <li>button 2</li>
 *   <li>one potentiometer value</li>
 *   <li>one distance sensor value</li>
 * </ul>
 *
 * <p>The class supports two input modes:
 * <ul>
 *   <li><b>Serial mode</b>: reads values from a physical INDbox over USB serial</li>
 *   <li><b>Simulation mode</b>: emulates the same inputs from the keyboard</li>
 * </ul>
 *
 * <p>Expected serial line format:
 * <pre>btn1,btn2,pot,dist\n</pre>
 *
 * <p>Example:
 * <pre>1,0,2048,123.4</pre>
 *
 * <p>Typical usage:
 * <pre>
 * INDbox box;
 *
 * void setup() {
 *   size(800, 600);
 *   box = new INDbox(this);
 * }
 *
 * void draw() {
 *   box.update();
 *   println(box.button1(), box.button2(), box.potRaw(), box.distRaw());
 * }
 * </pre>
 *
 * <p>Simulation mode:
 * <pre>
 * box = new INDbox(this, true);
 * </pre>
 *
 * <p>Simulation controls:
 * <ul>
 *   <li>1 = button 1</li>
 *   <li>2 = button 2</li>
 *   <li>A = increase potentiometer</li>
 *   <li>D = decrease potentiometer</li>
 *   <li>W = increase distance</li>
 *   <li>S = decrease distance</li>
 * </ul>
 */
public class INDbox {
  private final PApplet p;
  private final InputSource source;
  private final State state = new State();

  /**
   * Creates a new INDbox instance in serial mode.
   *
   * <p>This constructor attempts to automatically find and open a suitable serial port
   * using a baud rate of 115200.
   *
   * @param parent the Processing sketch instance
   */
  public INDbox(PApplet parent) {
    this(parent, null, 115200, false, 40, 2.5f);
  }

  /**
   * Creates a new INDbox instance in either serial or simulation mode.
   *
   * <p>If {@code simulate} is {@code true}, the class uses keyboard input instead of serial input.
   * If {@code simulate} is {@code false}, it attempts to connect to a physical device over serial.
   *
   * @param parent the Processing sketch instance
   * @param simulate if true, enable keyboard simulation mode
   */
  public INDbox(PApplet parent, boolean simulate) {
    this(parent, null, 115200, simulate, 10, 10.0f);
  }

  /**
   * Creates a new INDbox instance in simulation mode with custom step sizes.
   *
   * <p>The step sizes define how fast the simulated potentiometer and distance values change
   * while the corresponding keys are held down.
   *
   * @param parent the Processing sketch instance
   * @param simulate if true, enable keyboard simulation mode
   * @param potStep change per update step for the potentiometer
   * @param distStep change per update step for the distance value
   */
  public INDbox(PApplet parent, boolean simulate, int potStep, float distStep) {
    this(parent, null, 115200, simulate, potStep, distStep);
  }

  /**
   * Creates a new INDbox instance in serial mode with a custom port hint and baud rate.
   *
   * <p>The {@code portHint} is matched against the serial system port name and descriptive name.
   * If no hint is given, the class uses a simple heuristic to choose a likely USB serial device.
   *
   * @param parent the Processing sketch instance
   * @param portHint optional substring used to match a preferred serial port
   * @param baud the baud rate used to open the serial port
   */
  public INDbox(PApplet parent, String portHint, int baud) {
    this(parent, portHint, baud, false, 40, 2.5f);
  }

  /**
   * Creates a new INDbox instance with full configuration.
   *
   * <p>This is the most flexible constructor and allows switching between serial mode
   * and simulation mode while also setting custom simulation speeds.
   *
   * @param parent the Processing sketch instance
   * @param portHint optional substring used to match a preferred serial port
   * @param baud the baud rate used in serial mode
   * @param simulate if true, use simulation mode instead of serial mode
   * @param potStep change per update step for the simulated potentiometer
   * @param distStep change per update step for the simulated distance sensor
   */
  public INDbox(PApplet parent, String portHint, int baud, boolean simulate, int potStep, float distStep) {
    this.p = parent;

    if (simulate) {
      source = new SimulatedInputSource(parent, state, potStep, distStep);
      p.println("[INDbox] Simulation mode enabled.");
      p.println("[INDbox] Controls: 1/2 = buttons, A/D = pot +/-, W/S = dist +/-");
    } else {
      source = new SerialInputSource(parent, state, portHint, baud);
    }
  }

  /**
   * Updates the internal input state.
   *
   * <p>This method should typically be called once per frame inside {@code draw()}.
   * In serial mode it reads and parses new serial data. In simulation mode it updates
   * the values based on the current keyboard state.
   */
  public void update() {
    source.update();
  }

  /**
   * Closes the active input source.
   *
   * <p>In serial mode this closes the serial port. In simulation mode this unregisters
   * the keyboard event handler.
   */
  public void close() {
    source.close();
  }

  /**
   * Prints all currently available serial ports to the Processing console.
   *
   * <p>In simulation mode this prints a short informational message instead.
   */
  public void printPorts() {
    source.printPorts();
  }

  /**
   * Returns whether this instance is currently using simulation mode.
   *
   * @return true if simulation mode is active, false if serial mode is active
   */
  public boolean isSimulation() {
    return source.isSimulation();
  }

  /**
   * Returns the current state of button 1.
   *
   * @return 1 if pressed, 0 if not pressed
   */
  public int button1() { return state.b1; }

  /**
   * Returns the current state of button 2.
   *
   * @return 1 if pressed, 0 if not pressed
   */
  public int button2() { return state.b2; }

  /**
   * Returns the current raw potentiometer value.
   *
   * <p>The value is constrained to the range 0..4095.
   *
   * @return the raw potentiometer value
   */
  public int potRaw() { return state.pot; }

  /**
   * Returns the current raw distance value.
   *
   * <p>In serial mode this is the parsed sensor value from the incoming data stream.
   * In simulation mode this is controlled with the W and S keys.
   *
   * @return the raw distance value
   */
  public float distRaw() { return state.dist; }

  /**
   * Returns the last successfully parsed input line.
   *
   * <p>This can be useful for debugging and diagnostic overlays.
   *
   * @return the last valid input line
   */
  public String lastLine() { return state.lastLine; }

  /**
   * Returns whether the input source is currently connected.
   *
   * <p>In simulation mode this is always true after initialization.
   *
   * @return true if connected or active, false otherwise
   */
  public boolean connected() { return state.connected; }

  /**
   * Returns the selected serial port system name.
   *
   * <p>In simulation mode this returns {@code "SIM"}.
   *
   * @return the selected port system name
   */
  public String selectedPortSystemName() { return state.selectedPortSystemName; }

  /**
   * Returns the selected serial port description.
   *
   * <p>In simulation mode this returns a short simulation description.
   *
   * @return the selected port description
   */
  public String selectedPortDescription() { return state.selectedPortDescription; }

  /**
   * Returns the number of successfully parsed input lines.
   *
   * @return the number of valid lines
   */
  public int linesOk() { return state.linesOk; }

  /**
   * Returns the number of invalid or malformed input lines.
   *
   * @return the number of invalid lines
   */
  public int linesBad() { return state.linesBad; }

  /**
   * Returns the timestamp of the last valid line in milliseconds.
   *
   * <p>The timestamp uses {@code millis()} from the parent Processing sketch.
   *
   * @return timestamp of the last valid line
   */
  public long lastLineMillis() { return state.lastLineMillis; }

  /**
   * Internal shared state used by both serial and simulation input sources.
   */
  private static class State {
    boolean connected = false;
    String selectedPortSystemName = "";
    String selectedPortDescription = "";

    int lastBytesAvailable = 0;
    long lastLineMillis = 0;
    int linesOk = 0;
    int linesBad = 0;

    int b1 = 0;
    int b2 = 0;
    int pot = 0;
    float dist = 0;

    String lastLine = "";
  }

  /**
   * Internal abstraction for different input backends.
   */
  private interface InputSource {
    void update();
    void close();
    void printPorts();
    boolean isSimulation();
  }

  /**
   * Serial input backend for physical INDbox hardware.
   */
  private static class SerialInputSource implements InputSource {
    private final PApplet p;
    private final State state;

    private SerialPort port;
    private final StringBuilder rx = new StringBuilder(256);

    /**
     * Creates a serial input backend and tries to open a suitable serial port.
     *
     * @param parent the Processing sketch instance
     * @param state shared state object
     * @param portHint optional substring used to match a preferred serial port
     * @param baud the baud rate used to open the port
     */
    SerialInputSource(PApplet parent, State state, String portHint, int baud) {
      this.p = parent;
      this.state = state;

      printPorts();

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

      state.connected = true;
      state.selectedPortSystemName = port.getSystemPortName();
      state.selectedPortDescription = safe(port.getDescriptivePortName());

      p.println("[INDbox] Connected: " + state.selectedPortSystemName + " (" + state.selectedPortDescription + ")");
      p.println("[INDbox] Baud: " + baud);
    }

    @Override
    public void update() {
      if (port == null) return;

      int available = port.bytesAvailable();
      state.lastBytesAvailable = available;
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

        if (line.startsWith("b1") || line.startsWith("#")) continue;

        if (parseLine(line)) {
          state.lastLine = line;
          state.lastLineMillis = p.millis();
          state.linesOk++;
        } else {
          state.linesBad++;
        }
      }
    }

    /**
     * Parses one serial line in the format {@code btn1,btn2,pot,dist}.
     *
     * @param line the input line to parse
     * @return true if parsing was successful, false otherwise
     */
    private boolean parseLine(String line) {
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

        state.b1 = nb1;
        state.b2 = nb2;
        state.pot = npot;
        state.dist = ndist;

        return true;
      } catch (Exception e) {
        return false;
      }
    }

    /**
     * Attempts to find a suitable serial port.
     *
     * <p>If a hint is provided, it is matched first. Otherwise a simple heuristic is used
     * to prefer likely USB serial devices.
     *
     * @param hint optional substring used to match a preferred port
     * @return the selected serial port, or null if none was found
     */
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

      SerialPort bestCu = null;
      SerialPort bestTty = null;

      for (SerialPort sp : ports) {
        String sys = safe(sp.getSystemPortName()).toLowerCase();
        String desc = safe(sp.getDescriptivePortName()).toLowerCase();

        boolean looksLikeDevice =
          sys.contains("usbserial") || sys.contains("usbmodem") ||
          desc.contains("cp210") || desc.contains("silabs") ||
          desc.contains("ch340") || desc.contains("usb to uart") ||
          desc.contains("usb serial") || desc.contains("usbmodem");

        if (!looksLikeDevice) continue;

        if (sys.startsWith("cu.")) {
          if (bestCu == null) bestCu = sp;
        } else if (sys.startsWith("tty.")) {
          if (bestTty == null) bestTty = sp;
        }
      }

      if (bestCu != null) return bestCu;
      if (bestTty != null) return bestTty;

      return ports[0];
    }

    @Override
    public void close() {
      if (port != null) {
        try {
          port.closePort();
        } catch (Exception ignored) {
        }
        port = null;
      }
      state.connected = false;
    }

    @Override
    public void printPorts() {
      SerialPort[] ports = SerialPort.getCommPorts();
      p.println("[INDbox] Available ports (" + (ports == null ? 0 : ports.length) + "):");
      if (ports == null) return;

      for (int i = 0; i < ports.length; i++) {
        SerialPort sp = ports[i];
        p.println("  [" + i + "] " + safe(sp.getSystemPortName()) + "  |  " + safe(sp.getDescriptivePortName()));
      }
    }

    @Override
    public boolean isSimulation() {
      return false;
    }

    /**
     * Returns a non-null version of the given string.
     *
     * @param s input string
     * @return the original string, or an empty string if null
     */
    private String safe(String s) {
      return (s == null) ? "" : s;
    }
  }

  /**
   * Simulation backend using keyboard input instead of serial data.
   */
  public static class SimulatedInputSource implements InputSource {
    private final PApplet p;
    private final State state;

    private final Set<Integer> keysDown = new HashSet<Integer>();

    private final int potStep;
    private final float distStep;

    /**
     * Creates a new simulation backend.
     *
     * @param parent the Processing sketch instance
     * @param state shared state object
     * @param potStep change per update step for the potentiometer
     * @param distStep change per update step for the distance value
     */
    SimulatedInputSource(PApplet parent, State state, int potStep, float distStep) {
      this.p = parent;
      this.state = state;
      this.potStep = Math.max(1, potStep);
      this.distStep = Math.max(0.01f, distStep);

      state.connected = true;
      state.selectedPortSystemName = "SIM";
      state.selectedPortDescription = "Keyboard simulation";

      p.registerMethod("keyEvent", this);
    }

    @Override
    public void update() {
      state.b1 = isDown('1') ? 1 : 0;
      state.b2 = isDown('2') ? 1 : 0;

      if (isDown('a')) state.pot += potStep;
      if (isDown('d')) state.pot -= potStep;

      if (isDown('w')) state.dist += distStep;
      if (isDown('s')) state.dist -= distStep;

      state.pot = PApplet.constrain(state.pot, 0, 4095);
      state.dist = PApplet.constrain(state.dist, 0, 500);

      state.lastLine =
        state.b1 + "," +
        state.b2 + "," +
        state.pot + "," +
        PApplet.nf(state.dist, 0, 2);

      state.lastLineMillis = p.millis();
      state.linesOk++;
    }

    /**
     * Receives Processing key events and tracks which keys are currently held down.
     *
     * @param e the Processing key event
     */
    public void keyEvent(KeyEvent e) {
      int action = e.getAction();
      char k = Character.toLowerCase(e.getKey());

      if (action == KeyEvent.PRESS) {
        keysDown.add((int) k);
      } else if (action == KeyEvent.RELEASE) {
        keysDown.remove((int) k);
      }
    }

    /**
     * Returns whether a given key is currently pressed.
     *
     * @param c the key to test
     * @return true if the key is held down
     */
    private boolean isDown(char c) {
      return keysDown.contains((int) Character.toLowerCase(c));
    }

    @Override
    public void close() {
      try {
        p.unregisterMethod("keyEvent", this);
      } catch (Exception ignored) {
      }
      keysDown.clear();
      state.connected = false;
    }

    @Override
    public void printPorts() {
      p.println("[INDbox] Simulation mode: no serial ports needed.");
    }

    @Override
    public boolean isSimulation() {
      return true;
    }
  }
}
