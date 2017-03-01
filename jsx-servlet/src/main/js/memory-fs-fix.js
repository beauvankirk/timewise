var memoryFS = require('memory-fs');

memoryFS.readFile = memoryFS.prototype.readFile;

module.exports = memoryFS;
