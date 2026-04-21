import indbox.*;

INDbox box;

void setup() {
  size(800, 400);
  box = new INDbox(this);
}

void draw() {
  background(0);

  box.update();

  fill(255);

  text("potRaw: " + box.potRaw(), 20, 40);
  text("potMedian: " + box.pot(), 20, 60);
  text("potSlew: " + box.pot(INDbox.SLEW), 20, 80);

  text("dist: " + box.dist(), 20, 120);

  text("b1: " + box.button1(), 20, 160);
  text("b1Pressed: " + box.button1Pressed(), 20, 180);
  text("b1Released: " + box.button1Released(), 20, 200);
}

void keyPressed() {
  if (key == 'o') box.sendCommand("DIST_ON");
  if (key == 'f') box.sendCommand("DIST_OFF");
  if (key == '1') box.sendCommand("DIST_HZ 10");
  if (key == '2') box.sendCommand("DIST_HZ 5");
}
