var MemoryFS = require("memory-fs");
var webpack = require("webpack");
var fs = require('fs');

const Compiler = require("webpack/lib/Compiler");
const MultiCompiler = require("webpack/lib/MultiCompiler");
const NodeEnvironmentPlugin = require("webpack/lib/node/NodeEnvironmentPlugin");
const WebpackOptionsApply = require("webpack/lib/WebpackOptionsApply");
const WebpackOptionsDefaulter = require("webpack/lib/WebpackOptionsDefaulter");
const validateSchema = require("webpack/lib/validateSchema");
const WebpackOptionsValidationError = require("webpack/lib/WebpackOptionsValidationError");
const webpackOptionsSchema = require("webpack/schemas/webpackOptionsSchema.json");
const NodeWatchFileSystem = require("webpack/lib/node/NodeWatchFileSystem");
const NodeOutputFileSystem = require("webpack/lib/node/NodeOutputFileSystem");
const NodeJsInputFileSystem = require("enhanced-resolve/lib/NodeJsInputFileSystem");
const CachedInputFileSystem = require("enhanced-resolve/lib/CachedInputFileSystem");

var compile = function (inputPath, resultsObject) {

    console.log("Webpacking " + inputPath);

    var compiler = getWebpack({
        context: '/',
        entry: inputPath,
        output: {
            filename: 'bundle.js',
            path: '/'
        },
        plugins: [
            new webpack.ProgressPlugin(function (percentage, message, pass) {
                console.log("progress");
                console.info("[" + inputPath + "] (" + Math.round(percentage * 100) + "%) - " + message + (pass ? ' [' + pass + ']' : ''));
            }),
            new webpack.LoaderOptionsPlugin({
                debug: true
            })
        ],
        cache: false,
        module: {
            rules: [
                {
                    test: /\.jsx$/,
                    loader: 'babel-loader',
                    options: {
                        presets: [
                            ['es2015', {"modules": false}],
                            'react'
                        ]
                    }
                }
            ]
        },
        resolve: {
            extensions: ['.js', 'json', '.jsx', '*']
        },
        externals: [
            function(context, request, callback) {
                if (/^react.*$/.test(request)){
                    return callback(null, 'commonjs ' + request);
                }
                callback();
            }
        ],
        node: {
            console: true,
            __filename: true,
            __dirname: true
        }
    });
    // compiler.inputFileSystem = fs;
    // compiler.resolvers.normal.fileSystem = compiler.inputFileSystem;
    // compiler.resolvers.context.fileSystem = compiler.inputFileSystem;
    // compiler.resolvers.loader.fileSystem = compiler.inputFileSystem;
    // compiler.outputFileSystem = new MemoryFS();

    compiler.run(function(err, stats) {

        if (err) {
            console.error(err);
        } else {
            console.log(stats.toJson())
        }
        resultsObject['err'] = err;
        resultsObject['stats'] = stats;
        var content = compiler.outputFileSystem.data['bundle.js'];
        resultsObject['result'] = (content) ? content.toString() : null;
    });
};

function getWebpack(options, callback) {
    const webpackOptionsValidationErrors = validateSchema(webpackOptionsSchema, options);
    if(webpackOptionsValidationErrors.length) {
        throw new WebpackOptionsValidationError(webpackOptionsValidationErrors);
    }
    let compiler;
    if(Array.isArray(options)) {
        compiler = new MultiCompiler(options.map(options => webpack(options)));
    } else if(typeof options === "object") {
        new WebpackOptionsDefaulter().process(options);

        compiler = new Compiler();
        compiler.context = options.context;
        compiler.options = options;
        //new NodeEnvironmentPlugin().apply(compiler);
        compiler.inputFileSystem = require('fs');
        compiler.outputFileSystem = new MemoryFS();
        compiler.watchFileSystem = new NodeWatchFileSystem(compiler.inputFileSystem);
        if(options.plugins && Array.isArray(options.plugins)) {
            compiler.apply.apply(compiler, options.plugins);
        }
        compiler.applyPlugins("environment");
        compiler.applyPlugins("after-environment");
        compiler.options = new WebpackOptionsApply().process(options, compiler);
    } else {
        throw new Error("Invalid argument: options");
    }
    if(callback) {
        if(typeof callback !== "function") throw new Error("Invalid argument: callback");
        if(options.watch === true || (Array.isArray(options) && options.some(o => o.watch))) {
            const watchOptions = (!Array.isArray(options) ? options : options[0]).watchOptions || {};
            return compiler.watch(watchOptions, callback);
        }
        compiler.run(callback);
    }
    return compiler;
}

module.exports = {compile: compile};
