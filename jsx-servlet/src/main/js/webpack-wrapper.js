var MemoryFS = require("./memory-fs-wrapper");
var webpack = require("webpack/lib/webpack.web");
var Buffer = require("buffer").Buffer;

var compile = function(content, javaFs) {

    var inputFileSystem = new MemoryFS(javaFs);
    inputFileSystem.writeFileSync("/input.js", content);

    //todo: live progress:
    //https://github.com/webpack/webpack/issues/1000
    var compiler = webpack({
        entry: [
            '/input.js'
        ],
        output: {
            filename: 'bundle.js',
            path: '/'
        },
        inputFileSystem: inputFileSystem,
        outputFileSystem: new MemoryFS(javaFs)
    });

    var result = {
        finished: false
    };

    compiler.run(function (err, stats) {
        if (err) {
            result.err = err;
        } else {
            result.output = compiler.outputFileSystem.data['bundle.js'].toString();
        }

        var info = stats.toJson();

        //todo: add this to return object instead
        if (stats.hasErrors()) {
            console.error(info.errors);
        }

        //todo: add this to return object instead
        if (stats.hasWarnings()) {
            console.warn(info.warnings)
        }
        result.finished = true;
    });

    var start = java.lang.System.currentTimeMillis();

    while (!result.finished) {
        var now = java.lang.System.currentTimeMillis();
        if (now - start > 30000) { // 30 seconds
            throw new Error('Timeout while webpacking')
        }
    }

    var returnObj = {};
    returnObj.hasError = !!result.err;
    if (returnObj.hasError) {
        returnObj.err = JSON.stringify(result.err, null, 2)
    }
    returnObj.output = result.output;

    return returnObj;
};

module.exports = {compile: compile};
