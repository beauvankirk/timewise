var MemoryFS = require("./memory-fs-wrapper");
var webpack = require("webpack/lib/webpack.web");
var Buffer = require("buffer").Buffer;
var ProgressPlugin = require('webpack/lib/ProgressPlugin');

var compile = function (path, contextPath, content, javaFS) {

    var inputFileSystem = new MemoryFS(javaFS);
    inputFileSystem.mkdirpSync(contextPath);
    inputFileSystem.writeFileSync(path, content);

    global.javaFS = javaFS;
    function shim(id, parent) {
        this.id = id;
        this.exports = {};
        this.parent = parent;
        if (parent && parent.children) {
            parent.children.push(this);
        }

        this.filename = null;
        this.loaded = false;
        this.children = [];
    }
    shim._nodeModulePaths = function () {
        return ["/node_modules/"]
    };
    shim._resolveFilename = function (request, parent) {
        return request;
    };
    global.MODULE_SHIM = {
        __esModule: true,
        default: shim
    };

    global.__webpack_require_loader__ = function (loaderName) {
        if (loaderName.indexOf('babel-loader') >= 0) {
            return require('babel-loader?{"presets":["es2015","react"]}');
        }
    };

    var compiler = webpack({
        entry: [
            path
        ],
        output: {
            filename: 'bundle.js',
            path: '/'
        },
        inputFileSystem: inputFileSystem,
        outputFileSystem: new MemoryFS(javaFS),
        plugins: [
            new ProgressPlugin(function (percentage, message) {
                console.debug("[" + path + "] (" + Math.round(percentage * 100) + "%) - " + message);
            })
        ],
        module: {
            loaders: [
                {
                    test: /\.jsx$/,
                    loader: 'babel-loader',
                    query: {
                        presets: [
                           'es2015',
                            'react'
                        ]
                    }
                }
            ]
        },
        resolve: {
            extensions: ['.js', '.jsx', '.webpack.js', '.web.js', ''],
            alias: {
                fs$: './memory-fs-wrapper',
            },
            root: [
                '/js-server'
            ]
        },
        resolveLoader: {
            root: ['/js-server']
        },
        node: {
            fs: 'empty'
        }
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
        if (now - start > 240000) { // 240 seconds
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
