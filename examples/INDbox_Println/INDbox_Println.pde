import indbox.*;

INDbox box;

void setup() {
  size(800, 800);
  box = new INDbox(this); // auto-find
  frameRate(30);
}

void draw() {
  box.update();

  // dump with 5 fps
  if (frameCount % 6 == 0) {
    println(
      "b1=" + box.button1() +
      " b2=" + box.button2() +
      " pot=" + box.potRaw() +
      " dist=" + nf(box.distRaw(), 0, 2) +
      " | last='" + box.lastLine() + "'"
    );
  }

  background(20);
  fill(240);
  text("Check Console output (println)", 20, 30);
}
