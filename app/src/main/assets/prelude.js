function MicroAutoSelector(nativeSelector) {
  this.s = nativeSelector;
}

MicroAutoSelector.prototype.text = function (value) {
  return new MicroAutoSelector(this.s.text(String(value)));
};

MicroAutoSelector.prototype.textContains = function (value) {
  return new MicroAutoSelector(this.s.textContains(String(value)));
};

MicroAutoSelector.prototype.textMatches = function (re) {
  var flags = "";
  if (re && re.ignoreCase) flags += "i";
  if (re && re.multiline) flags += "m";
  return new MicroAutoSelector(this.s.textMatches(re.source, flags));
};

MicroAutoSelector.prototype.id = function (value) {
  return new MicroAutoSelector(this.s.id(String(value)));
};

MicroAutoSelector.prototype.idContains = function (value) {
  return new MicroAutoSelector(this.s.idContains(String(value)));
};

MicroAutoSelector.prototype.desc = function (value) {
  return new MicroAutoSelector(this.s.desc(String(value)));
};

MicroAutoSelector.prototype.descContains = function (value) {
  return new MicroAutoSelector(this.s.descContains(String(value)));
};

MicroAutoSelector.prototype.className = function (value) {
  return new MicroAutoSelector(this.s.className(String(value)));
};

MicroAutoSelector.prototype.clickable = function (value) {
  return new MicroAutoSelector(this.s.clickable(!!value));
};

MicroAutoSelector.prototype.enabled = function (value) {
  return new MicroAutoSelector(this.s.enabled(!!value));
};

MicroAutoSelector.prototype.selected = function (value) {
  return new MicroAutoSelector(this.s.selected(!!value));
};

MicroAutoSelector.prototype.findOne = function (timeout) {
  var wait = typeof timeout === "number" ? timeout : 10000;
  return native.findOne(this.s, wait);
};

MicroAutoSelector.prototype.findAll = function () {
  return native.findAll(this.s);
};

MicroAutoSelector.prototype.exists = function () {
  return native.findOne(this.s, 0) !== null;
};

MicroAutoSelector.prototype.waitFor = function (timeout) {
  var wait = typeof timeout === "number" ? timeout : 10000;
  return native.findOne(this.s, wait) !== null;
};

function text(value) {
  return new MicroAutoSelector(native.selector().text(String(value)));
}

function textContains(value) {
  return new MicroAutoSelector(native.selector().textContains(String(value)));
}

function textMatches(re) {
  return new MicroAutoSelector(native.selector()).textMatches(re);
}

function id(value) {
  return new MicroAutoSelector(native.selector().id(String(value)));
}

function idContains(value) {
  return new MicroAutoSelector(native.selector().idContains(String(value)));
}

function desc(value) {
  return new MicroAutoSelector(native.selector().desc(String(value)));
}

function descContains(value) {
  return new MicroAutoSelector(native.selector().descContains(String(value)));
}

function className(value) {
  return new MicroAutoSelector(native.selector().className(String(value)));
}

function click(x, y) {
  return native.tap(Math.round(x), Math.round(y), 50);
}

function longClick(x, y) {
  return native.tap(Math.round(x), Math.round(y), 600);
}

function press(x, y, duration) {
  return native.tap(Math.round(x), Math.round(y), Math.round(duration || 500));
}

function swipe(x1, y1, x2, y2, duration) {
  return native.swipe(
    Math.round(x1),
    Math.round(y1),
    Math.round(x2),
    Math.round(y2),
    Math.round(duration || 300)
  );
}

function sleep(ms) {
  native.sleep(Math.round(ms));
}

function toast(message) {
  native.toast(String(message));
}

function log(message) {
  native.log(String(message));
}

function exit() {
  native.exit();
}

function launchApp(pkg) {
  return native.launchApp(String(pkg));
}

function currentPackage() {
  return native.currentPackage();
}

function waitForPackage(pkg, timeout) {
  var wait = typeof timeout === "number" ? timeout : 10000;
  return native.waitForPackage(String(pkg), wait);
}

function back() {
  return native.back();
}

function home() {
  return native.home();
}

function recents() {
  return native.recents();
}

function dump() {
  log(native.dump());
}
