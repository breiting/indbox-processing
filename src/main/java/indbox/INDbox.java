package indbox;

import com.fazecast.jSerialComm.SerialPort;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import processing.core.PApplet;
import processing.event.KeyEvent;

/**
 * INDbox provides access to a standardized four-channel input device for Processing.
 *
 * <p>The device exposes:
 *
 * <ul>
 *   <li>button 1
 *   <li>button 2
 *   <li>one potentiometer value
 *   <li>one distance sensor value
 * </ul>
 *
 * <p>The class supports two input modes:
 *
 * <ul>
 *   <li><b>Serial mode</b>: reads values from a physical INDbox over USB serial
 *   <li><b>Simulation mode</b>: emulates the same inputs from the keyboard
 * </ul>
 *
 * <p>Expected serial line format:
 *
 * <pre>btn1,btn2,pot,dist\n</pre>
 *
 * <p>Example:
 *
 * <pre>1,0,2048,123.4</pre>
 *
 * <p>The API intentionally exposes both raw and filtered values:
 *
 * <ul>
 *   <li>{@code potRaw()}, {@code distRaw()} = raw values
 *   <li>{@code pot()}, {@code dist()} = stable values (default: MEDIAN)
 *   <li>{@code pot(INDbox.SLEW)}, {@code dist(INDbox.SLEW)} = alternative filtered values
 * </ul>
 *
 * <p>Typical usage:
 *
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
 *   println(box.pot(), box.dist());
 * }
 * </pre>
 *
 * <p>Simulation mode:
 *
 * <pre>
 * box = new INDbox(this, true);
 * </pre>
 *
 * <p>Simulation controls:
 *
 * <ul>
 *   <li>1 = button 1
 *   <li>2 = button 2
 *   <li>A = increase potentiometer
 *   <li>D = decrease potentiometer
 *   <li>W = increase distance
 *   <li>S = decrease distance
 * </ul>
 */
public class INDbox {
  public static final int MEDIAN = 0;
  public static final int SLEW = 1;

  private final PApplet p;
  private final InputSource source;
  private final State state = new State();

  /**
   * Creates a new INDbox instance in serial mode.
   *
   * <p>This constructor attempts to automatically find and open a suitable serial port using a baud
   * rate of 115200.
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
   * @param parent the Processing sketch instance
   * @param portHint optional substring used to match a preferred serial port
   * @param baud the baud rate used in serial mode
   * @param simulate if true, use simulation mode instead of serial mode
   * @param potStep change per update step for the simulated potentiometer
   * @param distStep change per update step for the simulated distance sensor
   */
  public INDbox(
      PApplet parent, String portHint, int baud, boolean simulate, int potStep, float distStep) {
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
   * <p>This method should typically be called once per frame inside {@code draw()}. In serial mode
   * it reads and parses new serial data. In simulation mode it updates the values based on the
   * current keyboard state.
   *
   * <p>Button press/release events are also updated here.
   */
  public void update() {
    source.update();
    updateButtonEvents();
    updateFilters();
  }

  /** Closes the active input source. */
  public void close() {
    source.close();
  }

  /** Prints all currently available serial ports to the Processing console. */
  public void printPorts() {
    source.printPorts();
  }

  /**
   * Sends a raw text command to the device over serial.
   *
   * <p>A newline is appended automatically. In simulation mode this command is ignored.
   *
   * @param command the command string to send
   */
  public void sendCommand(String command) {
    source.sendCommand(command);
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
  public int button1() {
    return state.b1;
  }

  /**
   * Returns the current state of button 2.
   *
   * @return 1 if pressed, 0 if not pressed
   */
  public int button2() {
    return state.b2;
  }

  /**
   * Returns true only on the frame button 1 was pressed.
   *
   * @return true on press event, otherwise false
   */
  public boolean button1Pressed() {
    return state.b1Pressed;
  }

  /**
   * Returns true only on the frame button 1 was released.
   *
   * @return true on release event, otherwise false
   */
  public boolean button1Released() {
    return state.b1Released;
  }

  /**
   * Returns true only on the frame button 2 was pressed.
   *
   * @return true on press event, otherwise false
   */
  public boolean button2Pressed() {
    return state.b2Pressed;
  }

  /**
   * Returns true only on the frame button 2 was released.
   *
   * @return true on release event, otherwise false
   */
  public boolean button2Released() {
    return state.b2Released;
  }

  /**
   * Returns the current raw potentiometer value.
   *
   * @return the raw potentiometer value in the range 0..4095
   */
  public int potRaw() {
    return state.pot;
  }

  /**
   * Returns the current raw distance value.
   *
   * @return the raw distance value
   */
  public float distRaw() {
    return state.dist;
  }

  /**
   * Returns a stable potentiometer value using the default filter (MEDIAN).
   *
   * @return filtered potentiometer value
   */
  public float pot() {
    return pot(MEDIAN);
  }

  /**
   * Returns a stable potentiometer value using the chosen filter mode.
   *
   * @param filterType INDbox.MEDIAN or INDbox.SLEW
   * @return filtered potentiometer value
   */
  public float pot(int filterType) {
    return (filterType == SLEW) ? state.potSlew : state.potMedian;
  }

  /**
   * Returns a stable distance value using the default filter (MEDIAN).
   *
   * @return filtered distance value
   */
  public float dist() {
    return dist(MEDIAN);
  }

  /**
   * Returns a stable distance value using the chosen filter mode.
   *
   * @param filterType INDbox.MEDIAN or INDbox.SLEW
   * @return filtered distance value
   */
  public float dist(int filterType) {
    return (filterType == SLEW) ? state.distSlew : state.distMedian;
  }

  /**
   * Sets the valid range for potentiometer filtering.
   *
   * @param min minimum value
   * @param max maximum value
   */
  public void setPotRange(int min, int max) {
    state.potMin = min;
    state.potMax = max;
  }

  /**
   * Sets the valid range for distance filtering.
   *
   * @param min minimum value
   * @param max maximum value
   */
  public void setDistRange(float min, float max) {
    state.distMin = min;
    state.distMax = max;
  }

  /**
   * Sets the maximum step per update for potentiometer slew filtering.
   *
   * @param step maximum change per update
   */
  public void setPotSlewStep(float step) {
    state.potSlewStep = Math.max(0.001f, step);
  }

  /**
   * Sets the maximum step per update for distance slew filtering.
   *
   * @param step maximum change per update
   */
  public void setDistSlewStep(float step) {
    state.distSlewStep = Math.max(0.001f, step);
  }

  /**
   * Returns the last successfully parsed input line.
   *
   * @return the last valid input line
   */
  public String lastLine() {
    return state.lastLine;
  }

  /**
   * Returns whether the input source is currently connected.
   *
   * @return true if connected or active, false otherwise
   */
  public boolean connected() {
    return state.connected;
  }

  /**
   * Returns the selected serial port system name.
   *
   * @return the selected port system name
   */
  public String selectedPortSystemName() {
    return state.selectedPortSystemName;
  }

  /**
   * Returns the selected serial port description.
   *
   * @return the selected port description
   */
  public String selectedPortDescription() {
    return state.selectedPortDescription;
  }

  /**
   * Returns the number of successfully parsed input lines.
   *
   * @return the number of valid lines
   */
  public int linesOk() {
    return state.linesOk;
  }

  /**
   * Returns the number of invalid or malformed input lines.
   *
   * @return the number of invalid lines
   */
  public int linesBad() {
    return state.linesBad;
  }

  /**
   * Returns the timestamp of the last valid line in milliseconds.
   *
   * @return timestamp of the last valid line
   */
  public long lastLineMillis() {
    return state.lastLineMillis;
  }

  private void updateButtonEvents() {
    boolean currentB1 = state.b1 == 1;
    boolean currentB2 = state.b2 == 1;

    state.b1Pressed = currentB1 && !state.prevB1;
    state.b1Released = !currentB1 && state.prevB1;

    state.b2Pressed = currentB2 && !state.prevB2;
    state.b2Released = !currentB2 && state.prevB2;

    state.prevB1 = currentB1;
    state.prevB2 = currentB2;
  }

  private void updateFilters() {
    int potClamped = PApplet.constrain(state.pot, state.potMin, state.potMax);
    float distClamped = PApplet.constrain(state.dist, state.distMin, state.distMax);

    // pot median
    if (!state.potMedianInitialized) {
      Arrays.fill(state.potWindow, potClamped);
      state.potMedian = potClamped;
      state.potMedianInitialized = true;
    }
    state.potWindow[state.potWindowIndex] = potClamped;
    state.potWindowIndex = (state.potWindowIndex + 1) % state.potWindow.length;
    int[] potCopy = state.potWindow.clone();
    Arrays.sort(potCopy);
    state.potMedian = potCopy[potCopy.length / 2];

    // dist median
    if (!state.distMedianInitialized) {
      Arrays.fill(state.distWindow, distClamped);
      state.distMedian = distClamped;
      state.distMedianInitialized = true;
    }
    state.distWindow[state.distWindowIndex] = distClamped;
    state.distWindowIndex = (state.distWindowIndex + 1) % state.distWindow.length;
    float[] distCopy = state.distWindow.clone();
    Arrays.sort(distCopy);
    state.distMedian = distCopy[distCopy.length / 2];

    // pot slew
    if (!state.potSlewInitialized) {
      state.potSlew = potClamped;
      state.potSlewInitialized = true;
    }
    float potDelta = potClamped - state.potSlew;
    potDelta = PApplet.constrain(potDelta, -state.potSlewStep, state.potSlewStep);
    state.potSlew += potDelta;

    // dist slew
    if (!state.distSlewInitialized) {
      state.distSlew = distClamped;
      state.distSlewInitialized = true;
    }
    float distDelta = distClamped - state.distSlew;
    distDelta = PApplet.constrain(distDelta, -state.distSlewStep, state.distSlewStep);
    state.distSlew += distDelta;
  }

  /** Internal shared state used by both serial and simulation input sources. */
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

    boolean prevB1 = false;
    boolean prevB2 = false;

    boolean b1Pressed = false;
    boolean b1Released = false;
    boolean b2Pressed = false;
    boolean b2Released = false;

    int potMin = 0;
    int potMax = 4095;
    float distMin = 50;
    float distMax = 2000;

    int[] potWindow = new int[5];
    int potWindowIndex = 0;
    boolean potMedianInitialized = false;
    float potMedian = 0;

    float[] distWindow = new float[5];
    int distWindowIndex = 0;
    boolean distMedianInitialized = false;
    float distMedian = 0;

    float potSlew = 0;
    boolean potSlewInitialized = false;
    float potSlewStep = 40;

    float distSlew = 0;
    boolean distSlewInitialized = false;
    float distSlewStep = 6;

    String lastLine = "";
  }

  private interface InputSource {
    void update();

    void close();

    void printPorts();

    void sendCommand(String command);

    boolean isSimulation();
  }

  /** Serial input backend for physical INDbox hardware. */
  private static class SerialInputSource implements InputSource {
    private final PApplet p;
    private final State state;

    private SerialPort port;
    private final StringBuilder rx = new StringBuilder(256);

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

      p.println(
          "[INDbox] Connected: "
              + state.selectedPortSystemName
              + " ("
              + state.selectedPortDescription
              + ")");
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

    @Override
    public void sendCommand(String command) {
      if (port == null || command == null) return;

      String msg = command.trim();
      if (msg.length() == 0) return;

      try {
        byte[] data = (msg + "\n").getBytes(StandardCharsets.UTF_8);
        port.writeBytes(data, data.length);
        p.println("[INDbox] Sent command: " + msg);
      } catch (Exception e) {
        p.println("[INDbox] Failed to send command: " + msg);
      }
    }

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
            sys.contains("usbserial")
                || sys.contains("usbmodem")
                || desc.contains("cp210")
                || desc.contains("silabs")
                || desc.contains("ch340")
                || desc.contains("usb to uart")
                || desc.contains("usb serial")
                || desc.contains("usbmodem");

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
        p.println(
            "  ["
                + i
                + "] "
                + safe(sp.getSystemPortName())
                + "  |  "
                + safe(sp.getDescriptivePortName()));
      }
    }

    @Override
    public boolean isSimulation() {
      return false;
    }

    private String safe(String s) {
      return (s == null) ? "" : s;
    }
  }

  /** Simulation backend using keyboard input instead of serial data. */
  public static class SimulatedInputSource implements InputSource {
    private final PApplet p;
    private final State state;

    private final Set<Integer> keysDown = new HashSet<Integer>();

    private final int potStep;
    private final float distStep;

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
          state.b1 + "," + state.b2 + "," + state.pot + "," + PApplet.nf(state.dist, 0, 2);

      state.lastLineMillis = p.millis();
      state.linesOk++;
    }

    @Override
    public void sendCommand(String command) {
      p.println("[INDbox] Simulation mode: command ignored -> " + command);
    }

    public void keyEvent(KeyEvent e) {
      int action = e.getAction();
      char k = Character.toLowerCase(e.getKey());

      if (action == KeyEvent.PRESS) {
        keysDown.add((int) k);
      } else if (action == KeyEvent.RELEASE) {
        keysDown.remove((int) k);
      }
    }

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
