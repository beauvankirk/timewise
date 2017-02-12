var MemoryFS = require('memory-fs');

var CustomMemoryFS = function (javaFs) {
    var memoryFS = new MemoryFS(arguments);
    var statSyncOrig = memoryFS.statSync.bind(memoryFS);
    var readFileSyncOrig = memoryFS.readFileSync.bind(memoryFS);
    var readdirSyncOrig = memoryFS.readdirSync.bind(memoryFS);

    memoryFS.statSync = function (_path) {
        try {
            return statSyncOrig(_path);
        } catch (err) {
            return javaFs.statSync(_path);
        }
    };
    memoryFS.readFileSync = function (path) {
        try {
            return readFileSyncOrig(path);
        } catch (err) {
            return javaFs.readFileSync(path);
        }
    };
    memoryFS.readdirSync = function (path) {
        try {
            return readdirSyncOrig(path);
        } catch (err) {
            return javaFs.readdirSync(path);
        }
    };
    memoryFS.existsSync = function(path) {
        var stat = this.statSync(path);
        if (stat) {
            return true;
        } else {
            return false;
        }
    };
    return memoryFS;
};

CustomMemoryFS.prototype = {};

module.exports = CustomMemoryFS;
